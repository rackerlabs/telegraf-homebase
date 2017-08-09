package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.ConnectedNode;
import com.rackspace.telegrafhomebase.model.DirectAssignments;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicReference;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class TelegrafConfigObserver implements Closeable, ClusterSingletonListener {
    private final Ignite ignite;
    private final IgniteTransactions igniteTransactions;
    private final IgniteCache<String, ManagedInput> managedInputsCache;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final IgnitePredicate<Event> handler;
    /**
     * Basically this is used to see if we're the first leader of the cluster to startup.
     */
    private final IgniteAtomicReference<Boolean> inputsLoaded;
    private final TaggingRepository taggingRepository;
    private final TaskExecutor taskExecutor;
    private final IgniteCache<String, DirectAssignments> directAssignmentsCache;
    private final IgniteCache<String, ConnectedNode> connectedNodesCache;
    private boolean closed;
    private QueryCursor<Cache.Entry<String, ManagedInput>> regionalQueryCursor;
    private QueryCursor<Cache.Entry<String, ConnectedNode>> connectedNodesQueryCursor;

    @Autowired
    public TelegrafConfigObserver(Ignite ignite,
                                  IgniteTransactions igniteTransactions,
                                  IgniteCacheProvider cacheProvider,
                                  PendingConfigQueuer pendingConfigQueuer,
                                  TaggingRepository taggingRepository,
                                  TaskExecutor taskExecutor) {
        this.ignite = ignite;
        this.igniteTransactions = igniteTransactions;
        managedInputsCache = cacheProvider.managedInputsCache();
        directAssignmentsCache = cacheProvider.directAssignmentsCache();
        connectedNodesCache = cacheProvider.connectedNodesCache();
        this.pendingConfigQueuer = pendingConfigQueuer;

        inputsLoaded = ignite.atomicReference("inputs-loaded", Boolean.FALSE, true);
        this.taggingRepository = taggingRepository;
        this.taskExecutor = taskExecutor;

        this.handler = this::handleIgniteEvent;
    }

    @Override
    public void handleGainedLeadership() throws Exception {
        setupRunningExpiredListener();

        setupContinuousManagedInputQuery();

        setupConnectedNodeQuery();

        closed = false;
    }

    @Override
    public void handleLostLeadership() throws Exception {
        close();
    }

    private void setupRunningExpiredListener() {
        log.info("Starting cache event listening");

        // Apparently all we need is a local listener even for events originating elsewhere in the cluster
        // Related to http://apache-ignite-users.70518.x6.nabble.com/Cache-Events-Questions-tp1090p1096.html
        ignite.events().localListen(handler,
                                    EventType.EVT_CACHE_OBJECT_EXPIRED);
        log.debug("Started event listening");
    }

    private void setupContinuousManagedInputQuery() {
        final ContinuousQuery<String, ManagedInput> query = new ContinuousQuery<>();
        if (inputsLoaded.compareAndSet(false, true)) {
            log.info("Will perform initial loading of managed inputs");
            query.setInitialQuery(new ScanQuery<>((cid, managedInput) -> managedInput.getRegion() != null));
        }
        query.setRemoteFilterFactory(() -> {
            return cacheEntryEvent -> {
                return cacheEntryEvent.getEventType().equals(javax.cache.event.EventType.CREATED);
            };
        });
        query.setLocalListener(events -> {
            log.debug("Executing batch of managed input events={}", events);
            taskExecutor.execute(() -> {
                events.forEach(cacheEntryEvent -> {
                    final ManagedInput createdManagedInput = cacheEntryEvent.getValue();
                    handleCreatedManagedInput(createdManagedInput);
                });
            });
        });

        regionalQueryCursor = managedInputsCache.query(query);
        taskExecutor.execute(() -> {
            regionalQueryCursor.forEach(e -> {
                final ManagedInput loadedManagedInput = e.getValue();
                log.debug("Queuing loaded input={} to queue", loadedManagedInput);
                pendingConfigQueuer.offer(loadedManagedInput.getRegion(), loadedManagedInput.getId(), true);
            });
        });
        log.debug("Started continuous regional config query");
    }

    private void setupConnectedNodeQuery() {
        final ContinuousQuery<String, ConnectedNode> query = new ContinuousQuery<>();

        query.setRemoteFilterFactory(() -> {
            return cacheEntryEvent -> {
                return cacheEntryEvent.getValue().getTenantId() != null &&
                        !cacheEntryEvent.getValue().getTenantId().isEmpty() &&
                        cacheEntryEvent.getEventType().equals(javax.cache.event.EventType.CREATED);
            };
        });

        query.setLocalListener(events -> {

            taskExecutor.execute(() -> {

                for (CacheEntryEvent<? extends String, ? extends ConnectedNode> event : events) {
                    final String tid = event.getKey();
                    final ConnectedNode info = event.getValue();
                    if (info == null) {
                        log.warn("ConnectedNode was null for event={} with type={}", event.getEventType());
                        continue;
                    }

                    log.debug("Finding assignable inputs for connected node={}", info);

                    final SqlQuery<String, ManagedInput> inputSqlQuery
                            = new SqlQuery<>(ManagedInput.class, "tenantId = ?");

                    managedInputsCache.query(inputSqlQuery.setArgs(info.getTenantId())).forEach(entry -> {
                        final ManagedInput input = entry.getValue();

                        // For this managed input, see if this telegraf satisfies all the tags
                        if (input.getAssignmentTags() != null &&
                                input.getAssignmentTags().entrySet().stream()
                                        .allMatch(inputTag ->
                                                  inputTag.getValue().equals(info.getTags().get(inputTag.getKey())))) {

                            assignDirectly(input, tid);

                        }
                    });
                }

            });

        });

        connectedNodesQueryCursor = connectedNodesCache.query(query);
    }

    private boolean handleIgniteEvent(Event e) {
        log.trace("Handling event={}", e);

        if (e instanceof CacheEvent) {
            final CacheEvent cacheEvent = (CacheEvent) e;
            switch (cacheEvent.type()) {

                case EventType.EVT_CACHE_OBJECT_EXPIRED:
                    if (cacheEvent.cacheName().equals(CacheNames.RUNNING_REGIONAL_INPUTS)) {
                        handleRunningConfigExpiration(cacheEvent);
                    }
                    break;
                default:
                    log.trace("Ignoring {}", e);
            }
        } else {
            log.trace("Ignoring {}", e);
        }

        return !closed;
    }

    private void handleCreatedManagedInput(ManagedInput createdManagedInput) {

        if (createdManagedInput.getRegion() != null) {
            // REGIONAL

            log.debug("Queuing created regional-input={} to queue", createdManagedInput);
            pendingConfigQueuer.offer(createdManagedInput.getRegion(), createdManagedInput.getId(), true);
        } else {
            // ASSIGNED

            log.debug("Handling created assigned-input={}", createdManagedInput);
            final Collection<String> matches = taggingRepository.findMatches(createdManagedInput.getTenantId(),
                                                                             createdManagedInput.getAssignmentTags());

            if (matches == null || matches.isEmpty()) {
                log.warn("Unable to find any running telegrafs that satisfy the assignment tags");
                return;
            }

            try (Transaction tx = igniteTransactions.txStart()) {
                for (String tid : matches) {
                    assignDirectly(createdManagedInput, tid);
                }

                tx.commit();
            }
        }

    }

    private void handleRunningConfigExpiration(CacheEvent cacheEvent) {
        final Object rawKey = cacheEvent.key();
        final RunningRegionalInputKey key;
        if (rawKey instanceof BinaryObject) {
            key = ((BinaryObject) rawKey).deserialize();
        } else {
            key = (RunningRegionalInputKey) rawKey;
        }
        log.info("Observed expiration of applied config {}", key);

        if (key.getRegion() != null) {
            log.info("Re-queueing regional config: {}", key);
            pendingConfigQueuer.offer(key.getRegion(), key.getMid(), true);
        }
    }

    private void assignDirectly(ManagedInput createdManagedInput, String tid) {
        final String createdInputId = createdManagedInput.getId();
        log.debug("Assigning managedInput={} to telegraf={}", createdInputId, tid);

        final Boolean didNotExist = directAssignmentsCache.invoke(tid, (mutableEntry, args) -> {

            final DirectAssignments prev = mutableEntry.getValue();

            final DirectAssignments assignments;
            if (prev == null) {
                assignments = new DirectAssignments(createdInputId);
            }
            else {
                assignments = prev.add(createdInputId);
            }

            if (prev != assignments) {
                mutableEntry.setValue(assignments);
                return true;
            } else {
                return false;
            }
        });

        if (!didNotExist) {
            log.warn("Attempted to add assignment={} to telegraf={} when it already had it",
                     createdInputId, tid);

        }
    }

    @Override
    public void close() throws IOException {
        log.info("Stopping cache event listening");

        ignite.events().stopLocalListen(handler,
                                        EventType.EVT_CACHE_OBJECT_EXPIRED);

        regionalQueryCursor.close();
        connectedNodesQueryCursor.close();

        closed = true;
    }
}
