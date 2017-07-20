package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component
@Slf4j
public class ConsistencyChecker {

    private final IgniteCache<String, StoredRegionalConfig> regionalConfigCache;
    private final ClusterSingletonTracker clusterSingletonTracker;
    private final PendingConfigQueuer pendingConfigQueuer;
    private final Ignite ignite;

    @Autowired
    public ConsistencyChecker(Ignite ignite,
                              ClusterSingletonTracker clusterSingletonTracker,
                              PendingConfigQueuer pendingConfigQueuer) {
        this.ignite = ignite;
        this.regionalConfigCache = ignite.cache(CacheNames.REGIONAL_CONFIG);
        this.clusterSingletonTracker = clusterSingletonTracker;
        this.pendingConfigQueuer = pendingConfigQueuer;
    }

    @Scheduled(initialDelayString = "#{homebaseProperties.initialConsistencyCheckDelay}",
            fixedDelayString = "#{homebaseProperties.consistencyCheckDelay}")
    public void check() {
        if (!clusterSingletonTracker.isLeader()) {
            log.trace("Skipping consistency check on non-leader node");
            return;
        }

        log.debug("Performing consistency check");

        final String queryStr = "select s.id, s.region" +
                " from StoredRegionalConfig as s LEFT JOIN" +
                "  \"running\".String as r" +
                " ON s.id = r.id AND s.region = r.region" +
                " where   r._val is null";

        final SqlFieldsQuery sql = new SqlFieldsQuery(queryStr);

        int count = 0;
        for (List<?> row : regionalConfigCache.query(sql)) {
            ++count;

            final String configId = (String) row.get(0);
            final String region = (String) row.get(1);

            pendingConfigQueuer.offer(region, configId);
        }

        if (count == 0) {
            log.debug("All consistent");
        } else {
            log.debug("Fixed {} inconsistencies", count);
        }
    }
}
