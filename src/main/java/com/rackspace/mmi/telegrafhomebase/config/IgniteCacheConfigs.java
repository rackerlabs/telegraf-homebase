package com.rackspace.mmi.telegrafhomebase.config;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.model.RunningKey;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.store.cassandra.CassandraCacheStoreFactory;
import org.apache.ignite.cache.store.cassandra.datasource.DataSource;
import org.apache.ignite.cache.store.cassandra.persistence.KeyValuePersistenceSettings;
import org.apache.ignite.configuration.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ClassPathResource;

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
    private final CassandraProperties cassandraProperties;

    @Autowired
    public IgniteCacheConfigs(IgniteProperties properties, CassandraProperties cassandraProperties) {
        this.properties = properties;
        this.cassandraProperties = cassandraProperties;
    }

    @Bean
    public DataSource igniteCassandraDataSource() {
        final DataSource dataSource = new DataSource();

        dataSource.setContactPoints(cassandraProperties.getContactPoints());

        return dataSource;
    }

    @Bean
    public CacheConfiguration<String,StoredRegionalConfig> regionalConfigCacheConfig(
            QueryEntities storedRegionalConfigQueryEntities
    ) {
        final CacheConfiguration<String,StoredRegionalConfig> config =
                new CacheConfiguration<>(CacheNames.REGIONAL_CONFIG);
        config.setTypes(String.class, StoredRegionalConfig.class);
        config.setBackups(properties.getStoredConfigBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        if (cassandraProperties.enabled()) {
            config.setWriteThrough(true);
            config.setReadThrough(true);

            final KeyValuePersistenceSettings persistenceSettings =
                    new KeyValuePersistenceSettings(new ClassPathResource(
                    "persistence-StoredRegionalConfig.xml"));

            final CassandraCacheStoreFactory<String, StoredRegionalConfig> cacheStoreFactory =
                    new CassandraCacheStoreFactory<>();
            cacheStoreFactory.setDataSource(igniteCassandraDataSource());
            cacheStoreFactory.setPersistenceSettings(persistenceSettings);

            config.setCacheStoreFactory(cacheStoreFactory);
        }

        config.setQueryEntities(storedRegionalConfigQueryEntities.getQueryEntities());

        return config;
    }

    @Bean
    public CacheConfiguration<RunningKey,String> appliedConfigsCacheConfig() {
        final CacheConfiguration<RunningKey,String> config = new CacheConfiguration<>(CacheNames.RUNNING);
        config.setTypes(RunningKey.class, String.class);
        config.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(
                new Duration(TimeUnit.SECONDS, properties.getAppliedConfigTtl())
        ));
        config.setEagerTtl(true);
        config.setBackups(properties.getAppliedConfigBackups());
        config.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        config.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);

        return config;
    }
}
