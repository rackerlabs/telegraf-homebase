package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.CacheNames;
import com.rackspace.telegrafhomebase.model.RunningKey;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.store.cassandra.CassandraCacheStore;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import javax.cache.configuration.Factory;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import java.util.concurrent.TimeUnit;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Configuration
@ImportResource({
        /* needs to be classpath* for unit test classloader scoping */
        "classpath*:query-*.xml"
})
public class IgniteCacheConfigs {
    private final IgniteProperties properties;

    @Autowired
    public IgniteCacheConfigs(IgniteProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CacheConfiguration<String,StoredRegionalConfig> regionalConfigCacheConfig(
            QueryEntities storedRegionalConfigQueryEntities,
            @Autowired(required = false)
            Factory<CassandraCacheStore<String, StoredRegionalConfig>> cacheStoreFactory
    ) {
        final CacheConfiguration<String,StoredRegionalConfig> config =
                new CacheConfiguration<>(CacheNames.REGIONAL_CONFIG);
        config.setTypes(String.class, StoredRegionalConfig.class);
        config.setBackups(properties.getStoredConfigBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        if (cacheStoreFactory != null) {
            config.setWriteThrough(true);
            config.setReadThrough(true);
            config.setWriteBehindEnabled(true);

            config.setCacheStoreFactory(cacheStoreFactory);
        }

        config.setQueryEntities(storedRegionalConfigQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<RunningKey,String> runningCacheConfig(
            QueryEntities runningQueryEntities
    ) {
        final CacheConfiguration<RunningKey,String> config = new CacheConfiguration<>(CacheNames.RUNNING);
        config.setTypes(RunningKey.class, String.class);
        config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.SECONDS, properties.getRunningConfigTtl())
        ));
        config.setEagerTtl(true);
        config.setBackups(properties.getRunningConfigBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        config.setQueryEntities(runningQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<String/*stored config id*/, Boolean> queuedCacheConfig() {
        CacheConfiguration<String, Boolean> config = new CacheConfiguration<>(CacheNames.QUEUED);

        config.setTypes(String.class, Boolean.class);
        config.setBackups(properties.getRunningConfigBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);

        return config;
    }
}
