package com.rackspace.telegrafhomebase.services;

import com.google.common.base.Strings;
import com.rackspace.telegrafhomebase.StandardTags;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.model.ConnectedNode;
import com.rackspace.telegrafhomebase.model.DirectAssignments;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningAssignedInputKey;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
import com.rackspace.telegrafhomebase.shared.ConfigResponseStreamBundle;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service
@Slf4j
public class ConfigPackResponderImpl implements Closeable, ConfigPackResponder {

    private final Ignite ignite;
    private final IgniteTransactions igniteTransactions;
    private final TaggingRepository taggingRepository;
    private final IgniteCache<RunningRegionalInputKey, String> runningRegionalCache;
    private final ConfigRepository configRepository;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final TelegrafProperties telegrafProperties;
    private final TaskExecutor taskExecutor;
    private final Random rand;
    private final IgniteCache<String, DirectAssignments> directAssignmentsCache;
    private final String ourId;
    private final IgniteCache<RunningAssignedInputKey, String> runningAssignedInputsCache;
    private final IgniteCache<String, ConnectedNode> connectedNodesCache;
    private boolean closed;
    private ThreadGroup threadGroup;
    private Map<String/*region*/, ConfigResponseStreamBundle> bundles = new HashMap<>();
    private Map<String/*tid*/, StreamObserver<Telegraf.ConfigPack>> perTelegrafResponseStreams
            = new ConcurrentHashMap<>();
    private QueryCursor<Cache.Entry<String, DirectAssignments>> directAssignmentsQueryCursor;

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite,
                                   IgniteTransactions igniteTransactions,
                                   ConfigRepository configRepository,
                                   TaggingRepository taggingRepository,
                                   IgniteCacheProvider cacheProvider,
                                   PendingConfigQueuer pendingConfigQueuer,
                                   TelegrafProperties telegrafProperties,
                                   TaskExecutor taskExecutor,
                                   Random rand) {
        this.ignite = ignite;
        ourId = ignite.cluster().localNode().id().toString();
        this.igniteTransactions = igniteTransactions;
        this.taggingRepository = taggingRepository;
        runningRegionalCache = cacheProvider.runningRegionalInputsCache();
        runningAssignedInputsCache = cacheProvider.runningAssignedInputsCache();
        directAssignmentsCache = cacheProvider.directAssignmentsCache();
        connectedNodesCache = cacheProvider.connectedNodesCache();
        this.configRepository = configRepository;
        this.pendingConfigQueuer = pendingConfigQueuer;
        this.telegrafProperties = telegrafProperties;
        this.taskExecutor = taskExecutor;
        this.rand = rand;
    }

    @PostConstruct
    public void startRegionalResponders() {

        threadGroup = new ThreadGroup("responders");

        for (String region : telegrafProperties.getRegions()) {
            final ConfigResponseStreamBundle observerBundle
                    = new ConfigResponseStreamBundle(rand, this::handleTelegrafRemoval);
            bundles.put(region, observerBundle);

            final Thread thread = new Thread(threadGroup, String.format("responder-%s", region)) {
                @Override
                public void run() {
                    log.debug("Starting {} region responder", region);
                    try {
                        regionalResponder(region, observerBundle);
                    } catch (Exception e) {
                        log.warn("Unexpected exception", e);
                    }
                    log.debug("Stopping {} region responder", region);
                }
            };

            thread.start();
        }

        setupDirectAssignmentQuery();
    }

    private void setupDirectAssignmentQuery() {
        final ContinuousQuery<String, DirectAssignments> query = new ContinuousQuery<>();

        query.setLocalListener(events -> {
            log.debug("Executing batch of assigned input events={}", events);

            taskExecutor.execute(() -> {
                for (CacheEntryEvent<? extends String, ? extends DirectAssignments> event : events) {
                    final String tid = event.getKey();
                    final StreamObserver<Telegraf.ConfigPack> stream = perTelegrafResponseStreams.get(tid);

                    if (stream != null) {
                        handleUpdatedAssignment(event, tid, stream);
                    }
                }
            });
        });

        directAssignmentsQueryCursor = directAssignmentsCache.query(query);
    }

    private void handleUpdatedAssignment(CacheEntryEvent<? extends String, ? extends DirectAssignments> event,
                                         String tid, StreamObserver<Telegraf.ConfigPack> stream) {
        final DirectAssignments prev = event.getOldValue();
        final Set<String> additions;
        final Set<String> removals;
        final DirectAssignments updatedAssignment = event.getValue();
        log.debug("Handling updated assignment={} via event={}", updatedAssignment, event);

        if (event.getEventType().equals(EventType.REMOVED)) {
            log.debug("Full removal via event={} is already handled elsewhere", event);
            return;
        }

        if (prev == null) {
            additions = updatedAssignment.get();
            removals = null;
        }
        else {
            additions = prev.additionsIn(updatedAssignment);
            removals = prev.removalsIn(updatedAssignment);
        }

        final Telegraf.ConfigPack.Builder configPackBuilder = Telegraf.ConfigPack.newBuilder();

        if (additions != null) {
            try (Transaction tx = igniteTransactions.txStart()) {
                for (String additionalMid : additions) {
                    final ManagedInput managedInput = configRepository.get(additionalMid);
                    if (managedInput != null) {
                        addToConfigPack(managedInput, configPackBuilder);

                        runningAssignedInputsCache.put(new RunningAssignedInputKey(additionalMid, tid), ourId);
                    }
                    else {
                        log.warn("Assignment update indicated managedInput={} was new, but no config available", additionalMid);
                    }
                }

                tx.commit();
            }
        }

        if (removals != null) {
            configPackBuilder.addAllRemovedIds(removals);
        }

        final Telegraf.ConfigPack configPack = configPackBuilder.build();

        try {

            log.debug("Sending config pack={} due to assignment update", configPack);
            stream.onNext(configPack);

        } catch (StatusRuntimeException e) {
            if (e.getStatus().equals(Status.CANCELLED)) {
                log.debug("Farend telegraf={} cancelled", tid);
            } else {
                log.warn("Observed exception providing config pack to telegraf={}", tid, e);
            }

            bundles.forEach((region, bundle) -> {
                bundle.handleDisconnect(tid);
            });
            handleTelegrafRemoval(tid);
        }

    }

    private void regionalResponder(String region, ConfigResponseStreamBundle observerBundle) {
        pendingConfigQueuer.observe(region, new PendingConfigQueuer.Handler() {
            @Override
            public boolean handle(String configId) {
                return handleAcquiredRegionalConfig(configId, observerBundle, region);
            }

            @Override
            public void onError(Throwable throwable) {
                observerBundle.respondToAll(entry -> {
                    try {
                        entry.getStream().onError(throwable);
                    } catch (Exception e) {
                        log.warn("Unexpected exception provided an error back to telegraf={}", entry.getTid(), e);
                    }
                    // all will get removed from rotation and will need to re-initiate
                    return false;
                });
            }

            @Override
            public boolean waitForReady() throws InterruptedException {
                return observerBundle.waitForReady();
            }
        });
    }

    private boolean handleAcquiredRegionalConfig(String configId,
                                                 ConfigResponseStreamBundle observerBundle,
                                                 String region) {
        final ManagedInput config = configRepository.get(configId);

        if (config != null) {
            observerBundle.respondToOne(entry -> {
                final Telegraf.ConfigPack configPack = ConfigPackResponderImpl.this.addToConfigPack(config,
                                                                                                    Telegraf.ConfigPack.newBuilder()).build();
                String tid = entry.getTid();

                try {
                    log.debug("Responding to {} with configPack={}", tid, configPack);
                    entry.getStream().onNext(configPack);


                    try (Transaction tx = ignite.transactions().txStart()) {
                        runningRegionalCache.put(new RunningRegionalInputKey(configId, region), tid);
                        tx.commit();
                    }
                    // keep this telegraf in rotation
                    return true;
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().equals(Status.CANCELLED)) {
                        log.debug("Farend telegraf={} cancelled", tid);
                    } else {
                        log.warn("Observed exception providing config pack to telegraf={}", tid, e);
                    }

                    if (region != null) {
                        log.debug("Re-queueing due to exception: {}", configId);
                        pendingConfigQueuer.offer(region, configId, true);
                    }
                    // and take this telegraf out of rotation
                    return false;
                } catch (Exception e) {
                    log.warn("Unexpected exception while responding", e);
                    return false;
                }
            });
        } else {
            log.warn("Saw configId={} in pending queue, but no corresponding config object", configId);
        }

        return !closed;
    }

    @Override
    public void startConfigStreaming(Telegraf.Identifiers identifiers,
                                     Map<String, String> nodeTags,
                                     StreamObserver<Telegraf.ConfigPack> responseStream) {
        final String tid = identifiers.getTid();
        final String region = identifiers.getRegion();
        final String tenant = identifiers.getTenant();

        if (nodeTags != null) {
            // enrich the node tags with standard tags
            nodeTags = new HashMap<>(nodeTags);
            nodeTags.put(StandardTags.TELEGRAF_ID, identifiers.getTid());
        }

        log.debug("Setting up config pack provider for telegraf={}", identifiers);

        perTelegrafResponseStreams.put(tid, responseStream);

        ConnectedNode connectedNodeValue = new ConnectedNode();
        connectedNodeValue.setClusterNodeId(ourId);
        connectedNodeValue.setTenantId(tenant);
        connectedNodeValue.setRegion(region);
        connectedNodeValue.setTags(nodeTags);
        connectedNodesCache.put(tid, connectedNodeValue);

        final boolean hasTenant = !Strings.isNullOrEmpty(tenant);
        final boolean hasRegion = !Strings.isNullOrEmpty(region);

        if (hasRegion && !hasTenant) {
            final ConfigResponseStreamBundle bundle = bundles.get(region);
            if (bundle != null) {
                bundle.add(new ConfigResponseStreamBundle.Entry(tid, responseStream));
            } else {
                log.warn("telegraf={} reported a region={} that is not configured",
                         tid,
                         region);
                responseStream.onError(new IllegalArgumentException("Unknown region"));
                return;
            }
        } else if (hasTenant && !hasRegion) {
            log.debug("Tracking telegraf={} for tenant={} with node tags={}", tid, tenant, nodeTags);
            taggingRepository.storeNodeTags(tenant, tid, nodeTags);
        } else //noinspection ConstantConditions
            if (hasTenant && hasRegion) {
            responseStream.onError(new IllegalArgumentException("Tenant specific regions not yet supported"));
            return;
        } else {
            responseStream.onError(new IllegalArgumentException("Missing region and tenant designation."));
            return;
        }
    }

    private void handleTelegrafRemoval(String tid) {
        perTelegrafResponseStreams.remove(tid);

        try (Transaction tx = igniteTransactions.txStart()) {
            directAssignmentsCache.remove(tid);
            final ConnectedNode oldInfo = connectedNodesCache.getAndRemove(tid);
            if (oldInfo != null) {
                taggingRepository.removeNodeTags(oldInfo.getTenantId(), tid, oldInfo.getTags());
            }
            tx.commit();
        } catch (Exception e) {
            log.error("Unexpected exception while handling telegraf={} removal", tid, e);
        }
    }

    private Telegraf.ConfigPack.Builder addToConfigPack(ManagedInput config, Telegraf.ConfigPack.Builder configPackBuilder) {
        final Telegraf.Config.Builder builder = Telegraf.Config.newBuilder()
                .setId(config.getId())
                .setTenantId(config.getTenantId())
                .setDefinition(config.getText());

        if (config.getTitle() != null) {
            builder.setTitle(config.getTitle());
        }

        configPackBuilder.addNew(builder.build());

        return configPackBuilder;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        directAssignmentsQueryCursor.close();
    }

}
