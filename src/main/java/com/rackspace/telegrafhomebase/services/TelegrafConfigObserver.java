package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.lang.IgnitePredicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class TelegrafConfigObserver implements Closeable {
    private final Ignite ignite;
    private final IgniteCache<String, StoredRegionalConfig> storedCache;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final IgnitePredicate<Event> handler;
    private boolean closed;

    @Autowired
    public TelegrafConfigObserver(Ignite ignite, PendingConfigQueuer pendingConfigQueuer) {
        this.ignite = ignite;
        storedCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        this.pendingConfigQueuer = pendingConfigQueuer;

        this.handler = this::handle;
    }

    void startEventListening() throws Exception {
        log.info("Starting");

        // Apparently all we need is a local listener even for events originating elsewhere in the cluster
        // Related to http://apache-ignite-users.70518.x6.nabble.com/Cache-Events-Questions-tp1090p1096.html
        ignite.events().localListen(handler,
                                    EventType.EVT_CACHE_OBJECT_PUT,
                                    EventType.EVT_CACHE_OBJECT_EXPIRED);

        log.debug("Started event listening");
    }

    void stopEventListening() {
        log.info("Stopping event listening");

        ignite.events().stopLocalListen(handler,
                                        EventType.EVT_CACHE_OBJECT_PUT,
                                        EventType.EVT_CACHE_OBJECT_EXPIRED);

        closed = true;
    }

    private boolean handle(Event e) {
        log.trace("Handling event={}", e);

        if (e instanceof CacheEvent) {
            final CacheEvent cacheEvent = (CacheEvent) e;
            switch (cacheEvent.type()) {
                case EventType.EVT_CACHE_OBJECT_PUT:
                    if (cacheEvent.cacheName().equals(CacheNames.REGIONAL_CONFIG)) {
                        handleConfigPut(cacheEvent);
                    }
                    break;

                case EventType.EVT_CACHE_OBJECT_EXPIRED:
                    if (cacheEvent.cacheName().equals(CacheNames.RUNNING)) {
                        handleAppliedConfigExpiration(cacheEvent);
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

    private void handleAppliedConfigExpiration(CacheEvent cacheEvent) {
        final Object rawKey = cacheEvent.key();
        final RunningKey key;
        if (rawKey instanceof BinaryObject) {
            key = ((BinaryObject) rawKey).deserialize();
        } else {
            key = (RunningKey) rawKey;
        }
        log.info("Observed expiration of applied config {}", key);

        final StoredRegionalConfig storedRegionalConfig = storedCache.get(key.getId());

        log.info("Re-queueing regional config: {}", storedRegionalConfig);
        addToQueue(storedRegionalConfig, true);
    }

    private void handleConfigPut(CacheEvent cacheEvent) {
        final Object v = cacheEvent.newValue();
        if (v instanceof StoredRegionalConfig) {
            final StoredRegionalConfig storedRegionalConfig = (StoredRegionalConfig) v;
            log.debug("Saw new regional config: {}", storedRegionalConfig);

            addToQueue(storedRegionalConfig, false);
        } else {
            log.warn("Unexpected cache put type: {}", v.getClass());
        }
    }

    private void addToQueue(StoredRegionalConfig storedRegionalConfig, boolean force) {
        if (storedRegionalConfig == null) {
            log.warn("Trying to queue null entry");
            return;
        }

        pendingConfigQueuer.offer(storedRegionalConfig.getRegion(), storedRegionalConfig.getId(), force);
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }
}
