package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import remote.Telegraf;

import java.util.List;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Service @Slf4j
public class TelegrafWellBeingHandler {

    private final IgniteCache<RunningKey, String> runningCache;
    private final IgniteCache<String, StoredRegionalConfig> storedCache;
    private final IgniteTransactions igniteTransactions;

    @Autowired
    public TelegrafWellBeingHandler(Ignite ignite) {
        runningCache = ignite.cache(CacheNames.RUNNING);
        storedCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        igniteTransactions = ignite.transactions();
    }

    public Telegraf.CurrentStateResponse confirmState(String tid, String region, List<String> activeConfigIdsList) {
        log.debug("Got keep alive from telegraf={}, activeConfigIds={}", tid, activeConfigIdsList);

        Telegraf.CurrentStateResponse.Builder resp = Telegraf.CurrentStateResponse.newBuilder();

        try (Transaction tx = igniteTransactions.txStart()) {

            activeConfigIdsList.forEach(configId -> {
                final RunningKey runningKey = new RunningKey(configId, region);

                if (storedCache.containsKey(configId)) {
                    // getting the key is enough touch it and keep the entry alive
                    final String assignedTo = runningCache.get(runningKey);

                    // runningOn might be null
                    if (assignedTo == null) {
                        // we must have restarted, so need to capture running assignment
                        log.info("Telegraf={} reported running managed input={}, but we didn't know anyone was, so we'll record it",
                                 tid, configId);
                        runningCache.put(runningKey, tid);
                    } else if (!tid.equals(assignedTo)) {
                        log.warn("The managed input={} got reported by telegraf={}, but we thought it was assigned to {}",
                                 configId, tid, assignedTo);

                        // it's not theirs, so tell them to stop
                        resp.addRemovedId(configId);
                    }
                }
                else {
                    log.info("Config {} was removed, so reporting back to telegraf as such", configId);
                    // it's been removed, let them know and let's clean up
                    resp.addRemovedId(configId);
                    runningCache.remove(runningKey);
                }
            });

            tx.commit();
        }

        return resp.build();
    }
}
