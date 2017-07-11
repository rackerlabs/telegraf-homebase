package com.rackspace.mmi.telegrafhomebase.services;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.model.AppliedKey;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
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

    private final IgniteCache<AppliedKey, String> appliedCache;
    private final IgniteCache<String, StoredRegionalConfig> storedCache;

    @Autowired
    public TelegrafWellBeingHandler(Ignite ignite) {
        appliedCache = ignite.cache(CacheNames.APPLIED);
        storedCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
    }

    public Telegraf.CurrentStateResponse confirmState(String tid, String region, List<String> activeConfigIdsList) {
        log.debug("Got keep alive from telegraf={}, activeConfigIds={}", tid, activeConfigIdsList);

        Telegraf.CurrentStateResponse.Builder resp = Telegraf.CurrentStateResponse.newBuilder();

        activeConfigIdsList.forEach(configId -> {
            final AppliedKey appliedKey = new AppliedKey(configId, region);

            if (storedCache.containsKey(configId)) {
                // getting the key is enough touch it and keep the entry alive
                final String assignedTo = appliedCache.get(appliedKey);

                // assignedTo might be null
                if (!tid.equals(assignedTo)) {
                    log.warn("The managed input {} got reported by {}, but we thought it was assigned to {}",
                             configId, tid, assignedTo);

                    // it's not theirs, so tell them to stop
                    resp.addRemovedId(configId);
                }
            }
            else {
                log.info("Config {} was removed, so reporting back to telegraf as such", configId);
                // it's been removed, let them know and let's clean up
                resp.addRemovedId(configId);
                appliedCache.remove(appliedKey);
            }
        });

        return resp.build();
    }
}
