package com.rackspace.mmi.telegrafhomebase.services;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.QueueNames;
import com.rackspace.mmi.telegrafhomebase.model.AppliedKey;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.events.EventType;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.services.Service;
import org.apache.ignite.services.ServiceContext;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class TelegrafConfigObserverService implements Service {

    @IgniteInstanceResource
    private Ignite ignite;

    private UUID ourListenerId;

    private static final Set<String> expectedCaches = new HashSet<>();
    static {
        expectedCaches.add(CacheNames.REGIONAL_CONFIG);
        expectedCaches.add(CacheNames.APPLIED);
    }

    private static boolean filterEvent(Event e) {
        if (e instanceof CacheEvent) {
            final CacheEvent cacheEvent = (CacheEvent) e;
            if (expectedCaches.contains(cacheEvent.cacheName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void init(ServiceContext serviceContext) throws Exception {
        log.info("Initializing");

        ourListenerId = ignite.events().remoteListen(this::handle, TelegrafConfigObserverService::filterEvent,
                                                     EventType.EVT_CACHE_OBJECT_PUT,
                                                     EventType.EVT_CACHE_OBJECT_EXPIRED);
    }

    private boolean handle(UUID uuid, Event e) {

        if (e instanceof CacheEvent) {
            final CacheEvent cacheEvent = (CacheEvent) e;
            switch (cacheEvent.type()) {
                case EventType.EVT_CACHE_OBJECT_PUT:
                    if (cacheEvent.cacheName().equals(CacheNames.REGIONAL_CONFIG)) {
                        handleConfigPut(cacheEvent);
                    }
                    break;

                case EventType.EVT_CACHE_OBJECT_EXPIRED:
                    if (cacheEvent.cacheName().equals(CacheNames.APPLIED)) {
                        handleAppliedConfigExpiration(cacheEvent);
                    }
                    break;
            }
        }

        return true;
    }

    private void handleAppliedConfigExpiration(CacheEvent cacheEvent) {
        final Object rawKey = cacheEvent.key();
        final AppliedKey key;
        if (rawKey instanceof BinaryObject) {
            key = ((BinaryObject) rawKey).deserialize();
        } else {
            key = (AppliedKey) rawKey;
        }
        log.info("Observed expiration of applied config {}", key);

        final IgniteCache<String, StoredRegionalConfig> storedCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        final StoredRegionalConfig storedRegionalConfig = storedCache.get(key.getId());

        log.info("Re-queueing regional config: {}", storedRegionalConfig);
        addToQueue(storedRegionalConfig);
    }

    private void handleConfigPut(CacheEvent cacheEvent) {
        final Object v = cacheEvent.newValue();
        if (v instanceof StoredRegionalConfig) {
            final StoredRegionalConfig storedRegionalConfig = (StoredRegionalConfig) v;
            log.debug("Saw new or updated regional config: {}", storedRegionalConfig);

            addToQueue(storedRegionalConfig);
        }
        else {
            log.warn("Unexpected cache put type: {}", v.getClass());
        }
    }

    private void addToQueue(StoredRegionalConfig storedRegionalConfig) {
        if (storedRegionalConfig == null) {
            log.warn("Trying to queue null entry");
            return;
        }

        final IgniteQueue<StoredRegionalConfig> queue = ignite.queue(
                QueueNames.PREFIX_PENDING_CONFIG + storedRegionalConfig.getRegion(),
                0,
                null
        );

        if (queue != null) {
            queue.add(storedRegionalConfig);

            log.debug("Queued {}", storedRegionalConfig);
        }
        else {
            log.warn("Region={} was not previously registered for queueing", storedRegionalConfig.getRegion());
        }
    }

    @Override
    public void execute(ServiceContext serviceContext) throws Exception {
        log.info("Executing...but we're a background service");
        // and nothing else to do since we're just an event listener
    }

    @Override
    public void cancel(ServiceContext serviceContext) {
        log.info("Cancelling");

        if (ourListenerId != null) {
            log.debug("Stopping our remote listener");
            ignite.events().stopRemoteListen(ourListenerId);
        }
    }
}
