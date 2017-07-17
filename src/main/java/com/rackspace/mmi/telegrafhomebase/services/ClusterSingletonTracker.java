package com.rackspace.mmi.telegrafhomebase.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCluster;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.events.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This Ignite service acts a proxy around the cluster singleton construct to let our local instance
 * of {@link TelegrafConfigObserver} know that it should be the active listener of cache events.
 *
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class ClusterSingletonTracker {
    private final Ignite ignite;
    private final TelegrafConfigObserver configObserver;
    private final AtomicBoolean actingSingleton = new AtomicBoolean();

    @Autowired
    public ClusterSingletonTracker(Ignite ignite, TelegrafConfigObserver configObserver) {
        this.ignite = ignite;
        this.configObserver = configObserver;

        final ClusterGroup leaderGroup = ignite.cluster().forOldest();
    }

    @PostConstruct
    public void registerDiscoveryListener() {

        ignite.events().localListen((event -> {
            log.debug("Handling discovery event={}", event);
            checkLeadership();

            return true;
        }), EventType.EVTS_DISCOVERY);

        checkLeadership();
    }

    private void checkLeadership() {
        final IgniteCluster cluster = ignite.cluster();
        if (cluster.forOldest().nodes().contains(cluster.localNode())) {
            log.debug("Currently leader");
            if (actingSingleton.compareAndSet(false, true)) {
                log.debug("Notifying about start transition");
                try {
                    configObserver.startEventListening();
                } catch (Exception e) {
                    log.warn("Failed to start event listening", e);
                }
            }
        }
        else {
            log.debug("Currently not leader");
            if (actingSingleton.compareAndSet(true, false)) {
                log.debug("Notifying about stop transition");
                configObserver.stopEventListening();
            }
        }
    }
}
