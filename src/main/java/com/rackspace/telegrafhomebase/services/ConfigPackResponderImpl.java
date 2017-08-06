package com.rackspace.telegrafhomebase.services;

import com.google.common.base.Strings;
import com.rackspace.telegrafhomebase.StandardTags;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRemoteInputKey;
import com.rackspace.telegrafhomebase.shared.ConfigResponseStreamBundle;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import javax.annotation.PostConstruct;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service
@Slf4j
public class ConfigPackResponderImpl implements Closeable, ConfigPackResponder {

    private final Ignite ignite;
    private final TaggingRepository taggingRepository;
    private final IgniteCache<RunningRemoteInputKey, String> runningCache;
    private final ConfigRepository configRepository;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final TelegrafProperties telegrafProperties;
    private final Random rand;
    private boolean closed;
    private ThreadGroup threadGroup;
    private Map<String, ConfigResponseStreamBundle> bundles = new HashMap<>();
    private Map<String, StreamObserver<Telegraf.ConfigPack>> perTelegrafResponseStreams
            = new ConcurrentHashMap<>();

    @Autowired
    public ConfigPackResponderImpl(Ignite ignite,
                                   ConfigRepository configRepository,
                                   TaggingRepository taggingRepository,
                                   IgniteCacheProvider cacheProvider,
                                   PendingConfigQueuer pendingConfigQueuer,
                                   TelegrafProperties telegrafProperties,
                                   Random rand) {
        this.ignite = ignite;
        this.taggingRepository = taggingRepository;
        runningCache = cacheProvider.runningInputsCache();
        this.configRepository = configRepository;
        this.pendingConfigQueuer = pendingConfigQueuer;
        this.telegrafProperties = telegrafProperties;
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
    }

    private void regionalResponder(String region, ConfigResponseStreamBundle observerBundle) {
        pendingConfigQueuer.observe(region, new PendingConfigQueuer.Handler() {
            @Override
            public boolean handle(String configId) {
                final ManagedInput config = configRepository.get(configId);

                if (config != null) {
                    observerBundle.respondToOne(entry -> {
                        try {
                            return respondWithNextConfig(entry.getTid(), region, entry.getStream(), configId, config);
                        } catch (Exception e) {
                            log.warn("Unexpected exception", e);
                            return false;
                        }
                    });
                } else {
                    log.warn("Saw configId={} in pending queue, but no corresponding config object", configId);
                }

                return !closed;
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

    @Override
    public void startConfigStreaming(Telegraf.Identifiers identifiers,
                                     Map<String, String> nodeTags,
                                     StreamObserver<Telegraf.ConfigPack> responseStream) {
        final String tid = identifiers.getTid();
        final String region = identifiers.getRegion();
        final String tenant = identifiers.getTenant();

        log.debug("Setting up config pack provider for telegraf={}", identifiers);

        final boolean hasTenant = !Strings.isNullOrEmpty(tenant);
        final boolean hasRegion = !Strings.isNullOrEmpty(region);

        // enrich the node tags with standard tags
        final Map<String, String> enrichedTags = new HashMap<>(nodeTags);
        enrichedTags.put(StandardTags.TELEGRAF_ID, identifiers.getTid());
        if (hasRegion) {
            enrichedTags.put(StandardTags.REGION, region);
        }

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
            taggingRepository.storeNodeTags(tenant, tid, enrichedTags);
        } else //noinspection ConstantConditions
            if (hasTenant && hasRegion) {
            responseStream.onError(new IllegalArgumentException("Tenant specific regions not yet supported"));
            return;
        } else {
            responseStream.onError(new IllegalArgumentException("Missing region and tenant designation."));
            return;
        }

        perTelegrafResponseStreams.put(tid, responseStream);
    }

    private void handleTelegrafRemoval(String tid) {
        perTelegrafResponseStreams.remove(tid);
    }

    /**
     * @return true to keep this telegraf in the response bundle
     */
    private boolean respondWithNextConfig(String tid,
                                          String region,
                                          StreamObserver<Telegraf.ConfigPack> responseObserver,
                                          String configId,
                                          ManagedInput config) {
        final Telegraf.ConfigPack.Builder configPackBuilder = Telegraf.ConfigPack.newBuilder();
        final Telegraf.Config.Builder builder = Telegraf.Config.newBuilder()
                .setId(config.getId())
                .setTenantId(config.getTenantId())
                .setDefinition(config.getText());

        if (config.getTitle() != null) {
            builder.setTitle(config.getTitle());
        }

        configPackBuilder.addNew(builder.build());

        try {
            final Telegraf.ConfigPack configPack = configPackBuilder.build();
            log.debug("Responding to {} with configPack={}", tid, configPack);
            responseObserver.onNext(configPack);

            final RunningRemoteInputKey key = new RunningRemoteInputKey(config.getId(), region);
            log.debug("Noting initial application of {} to {}", key, tid);

            try (Transaction tx = ignite.transactions().txStart()) {
                runningCache.put(key, tid);
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

            log.debug("Re-queueing due to exception: {}", config);
            pendingConfigQueuer.offer(region, configId, true);
            // and take this telegraf out of rotation
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

}
