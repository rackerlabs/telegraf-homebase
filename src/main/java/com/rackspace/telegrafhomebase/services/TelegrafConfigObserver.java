package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RunningRemoteInputKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicReference;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.ContinuousQuery;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgnitePredicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class TelegrafConfigObserver implements Closeable, ClusterSingletonListener {
    private final Ignite ignite;
    private final IgniteCache<String, ManagedInput> managedInputsCache;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final IgnitePredicate<Event> handler;
    /**
     * Basically this is used to see if we're the first leader of the cluster to startup.
     */
    private final IgniteAtomicReference<Boolean> inputsLoaded;
    private boolean closed;
    private QueryCursor<Cache.Entry<String, ManagedInput>> regionalQueryCursor;

    @Autowired
    public TelegrafConfigObserver(Ignite ignite, IgniteCacheProvider cacheProvider, PendingConfigQueuer pendingConfigQueuer) {
        this.ignite = ignite;
        managedInputsCache = cacheProvider.managedInputsCache();
        this.pendingConfigQueuer = pendingConfigQueuer;

        inputsLoaded = ignite.atomicReference("inputs-loaded", Boolean.FALSE, true);

        this.handler = this::handle;
    }

    @Override
    public void handleGainedLeadership() throws Exception {
        setupRunningExpiredListener();

        setupRegionalConfigQuery();

        closed = false;
    }

    private void setupRunningExpiredListener() {
        log.info("Starting cache event listening");

        // Apparently all we need is a local listener even for events originating elsewhere in the cluster
        // Related to http://apache-ignite-users.70518.x6.nabble.com/Cache-Events-Questions-tp1090p1096.html
        ignite.events().localListen(handler,
                                    EventType.EVT_CACHE_OBJECT_EXPIRED);
        log.debug("Started event listening");
    }

    private void setupRegionalConfigQuery() {
        final ContinuousQuery<String, ManagedInput> regionalContinuousQuery = new ContinuousQuery<>();
        if (inputsLoaded.compareAndSet(false, true)) {
            log.info("Will perform initial loading of managed inputs");
            regionalContinuousQuery.setInitialQuery(new ScanQuery<>((cid, managedInput) -> managedInput.getRegion() != null));
        }
        regionalContinuousQuery.setRemoteFilterFactory(() -> {
            return cacheEntryEvent -> {
                return cacheEntryEvent.getEventType().equals(javax.cache.event.EventType.CREATED) &&
                        cacheEntryEvent.getValue().getRegion() != null;
            };
        });
        regionalContinuousQuery.setLocalListener(iterable -> {
            iterable.forEach(cacheEntryEvent -> {
                final ManagedInput createdManagedInput = cacheEntryEvent.getValue();
                log.debug("Queuing created input={} to queue", createdManagedInput);
                pendingConfigQueuer.offer(createdManagedInput.getRegion(), createdManagedInput.getId(), true);
            });
        });

        regionalQueryCursor = managedInputsCache.query(regionalContinuousQuery);
        regionalQueryCursor.forEach(e -> {
            final ManagedInput loadedManagedInput = e.getValue();
            log.debug("Queuing loaded input={} to queue", loadedManagedInput);
            pendingConfigQueuer.offer(loadedManagedInput.getRegion(), loadedManagedInput.getId(), true);
        });
        log.debug("Started continuous regional config query");
    }

    @Override
    public void handleLostLeadership() throws Exception {
        close();
    }

    private boolean handle(Event e) {
        log.trace("Handling event={}", e);

        if (e instanceof CacheEvent) {
            final CacheEvent cacheEvent = (CacheEvent) e;
            switch (cacheEvent.type()) {

                case EventType.EVT_CACHE_OBJECT_EXPIRED:
                    if (cacheEvent.cacheName().equals(CacheNames.RUNNING_REMOTE_INPUTS)) {
                        handleRunningConfigExpiration(cacheEvent);
                    }
                    break;
                default:
                    log.trace("Ignoring {}", e);
            }
        }
        else {
            log.trace("Ignoring {}", e);
        }

        return !closed;
    }

    private void handleRunningConfigExpiration(CacheEvent cacheEvent) {
        final Object rawKey = cacheEvent.key();
        final RunningRemoteInputKey key;
        if (rawKey instanceof BinaryObject) {
            key = ((BinaryObject) rawKey).deserialize();
        } else {
            key = (RunningRemoteInputKey) rawKey;
        }
        log.info("Observed expiration of applied config {}", key);

        log.info("Re-queueing regional config: {}", key);
        pendingConfigQueuer.offer(key.getRegion(), key.getId(), true);
    }

    @Override
    public void close() throws IOException {
        log.info("Stopping cache event listening");

        ignite.events().stopLocalListen(handler,
                                        EventType.EVT_CACHE_OBJECT_EXPIRED);

        regionalQueryCursor.close();
        regionalQueryCursor = null;
        closed = true;
    }
}
