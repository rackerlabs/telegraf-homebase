package com.rackspace.telegrafhomebase.config;

import com.rackspace.telegrafhomebase.model.ManagedInput;
import org.apache.ignite.cache.store.cassandra.CassandraCacheStore;
import org.apache.ignite.cache.store.cassandra.CassandraCacheStoreFactory;
import org.apache.ignite.cache.store.cassandra.datasource.DataSource;
import org.apache.ignite.cache.store.cassandra.persistence.KeyValuePersistenceSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import javax.cache.configuration.Factory;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Profile(CassandraProperties.PROFILE)
@Configuration
public class CassandraCacheStoreConfig {

    private final CassandraProperties cassandraProperties;

    @Autowired
    public CassandraCacheStoreConfig(CassandraProperties cassandraProperties) {
        this.cassandraProperties = cassandraProperties;
    }

    @Bean
    public DataSource igniteCassandraDataSource() {
        final DataSource dataSource = new DataSource();

        dataSource.setContactPoints(cassandraProperties.getContactPoints());

        return dataSource;
    }

    @Bean
    public Factory<CassandraCacheStore<String, ManagedInput>> cassandraCacheStoreFactory() {
        final KeyValuePersistenceSettings persistenceSettings =
                new KeyValuePersistenceSettings(
                        new ClassPathResource("persistence-ManagedInputs.xml"));

        final CassandraCacheStoreFactory<String, ManagedInput> cacheStoreFactory =
                new CassandraCacheStoreFactory<>();
        cacheStoreFactory.setDataSource(igniteCassandraDataSource());
        cacheStoreFactory.setPersistenceSettings(persistenceSettings);

        return cacheStoreFactory;
    }
}
