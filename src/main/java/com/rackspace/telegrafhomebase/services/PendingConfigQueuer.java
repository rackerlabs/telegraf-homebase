package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteProperties;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.shared.DistributedQueueUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.configuration.CollectionConfiguration;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service @Slf4j
public class PendingConfigQueuer {

    private final Ignite ignite;
    private final TelegrafProperties telegrafProperties;
    private final IgniteProperties igniteProperties;
    private final IgniteTransactions igniteTransactions;

    @Autowired
    public PendingConfigQueuer(Ignite ignite, IgniteTransactions igniteTransactions,
                               TelegrafProperties telegrafProperties, IgniteProperties igniteProperties) {
        this.ignite = ignite;
        this.igniteTransactions = igniteTransactions;
        this.telegrafProperties = telegrafProperties;
        this.igniteProperties = igniteProperties;
    }

    @PostConstruct
    public void setupQueues() {
        telegrafProperties.getRegions().forEach(region -> {
            log.info("Registering pending config queue for region={}", region);

            final CollectionConfiguration collectionConfig = new CollectionConfiguration();
            collectionConfig.setBackups(igniteProperties.getRunningConfigCacheBackups());

            ignite.queue(DistributedQueueUtils.derivePendingConfigQueueName(region),
                         0,
                         collectionConfig);
        });
    }


    public void offer(String region, String configId, boolean force) {

        final IgniteQueue<String> queue = ignite.queue(
                DistributedQueueUtils.derivePendingConfigQueueName(region),
                0, null
        );

        if (queue != null) {
            log.debug("Queueing non-running configId={} for region={}", configId, region);

            try (Transaction tx = igniteTransactions.txStart()) {
                queue.offer(configId);

                tx.commit();
            }
        }
        else {
            log.warn("Queue is not configured for region={} referenced by configId={}", region, configId);
        }

    }

    /**
     * Blocks waiting for another pending configuration ID and notifies the given <code>handler</code>
     * @param region
     * @param handler
     */
    public void observe(String region, Handler handler) {
        Assert.notNull(handler, "handler is required");
        final IgniteQueue<String> queue;
        try {
            queue = ignite.queue(DistributedQueueUtils.derivePendingConfigQueueName(region),
                                 0, null);

        } catch (Exception e) {
            log.warn("Unexpected exception while locating queue", e);
            handler.onError(new IllegalStateException("Unexpected exception while locating queue"));
            return;
        }

        boolean proceed = true;
        while (proceed) {

            try {
                proceed = handler.waitForReady();
            } catch (InterruptedException e) {
                log.debug("Interrupted", e);
                continue;
            }

            log.debug("Waiting for next pending config");
            final String configId;
            try {
                configId = queue.take();
            } catch (IgniteInterruptedException e) {
                log.warn("Interrupted during queue take, should happen only during shutdown");
                return;
            } catch (IgniteException e) {
                log.warn("Unexpected exception while taking from pending config queue", e);
                handler.onError(new IllegalStateException(
                        "Unexpected exception while taking from pending config queue"));
                return;
            }
            log.debug("Acquired next configuration in queue={}", configId);

            proceed = handler.handle(configId);
        }
    }

    public interface Handler {
        /**
         *
         * @param configId the next acquired configId
         * @return true to proceed, false to stop
         */
        boolean handle(String configId);

        void onError(Throwable throwable);

        boolean waitForReady() throws InterruptedException;
    }
}
