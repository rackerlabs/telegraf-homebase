package com.rackspace.telegrafhomebase.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.events.EventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
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
    private final AtomicBoolean actingSingleton = new AtomicBoolean();
    private final ClusterNode localNode;
    private final List<ClusterSingletonListener> listeners;
    private boolean leader;

    @Autowired
    public ClusterSingletonTracker(Ignite ignite,
                                   List<ClusterSingletonListener> listeners) {
        this.ignite = ignite;

        localNode = ignite.cluster().localNode();
        this.listeners = listeners;
    }

    @PostConstruct
    public void registerDiscoveryListener() {

        ignite.events().localListen((event -> {
            log.debug("Handling discovery event={}", event);
            resolveLeadership();

            return true;
        }), EventType.EVTS_DISCOVERY);

        resolveLeadership();
    }

    private void resolveLeadership() {
        if (isCurrentlyLeader()) {
            leader = true;
            if (actingSingleton.compareAndSet(false, true)) {
                try {
                    handleGainedLeadership();
                } catch (Exception e) {
                    log.warn("Failed to handle gained leadership", e);
                }
            }
            else {
                log.debug("Still leader");
            }
        }
        else {
            leader = false;
            if (actingSingleton.compareAndSet(true, false)) {
                try {
                    handleLostLeadership();
                } catch (Exception e) {
                    log.warn("Failed to handle lost leadership", e);
                }
            }
            else {
                log.debug("Still not leader");
            }
        }
    }

    private void handleGainedLeadership() throws Exception {
        log.debug("Notifying about gained leadership");
        listeners.forEach(l -> {
            try {
                l.handleGainedLeadership();
            } catch (Exception e) {
                log.warn("Unexpected exception", e);
            }
        });
    }

    private void handleLostLeadership() {
        log.debug("Notifying about lost leadership");
        listeners.forEach(l -> {
            try {
                l.handleLostLeadership();
            } catch (Exception e) {
                log.warn("Unexpected exception", e);
            }
        });
    }

    public boolean isLeader() {
        return leader;
    }

    private boolean isCurrentlyLeader() {
        try {
            return ignite.cluster().forOldest().nodes().contains(localNode);
        } catch (IllegalStateException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to compute leadership at this time", e);
            }
            else {
                log.warn("Unable to compute leadership at this time");
            }

            return false;
        }
    }
}
