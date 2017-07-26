package com.rackspace.telegrafhomebase.services;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.rackspace.telegrafhomebase.config.CassandraProperties;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cache.store.cassandra.datasource.DataSource;
import org.apache.ignite.cache.store.cassandra.session.BatchLoaderAssistant;
import org.apache.ignite.cache.store.cassandra.session.CassandraSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Profile(CassandraProperties.PROFILE)
@Component
public class CassandraCacheStoreHealth extends AbstractHealthIndicator {

    private final DataSource dataSource;
    private final IgniteLogger igniteLogger;

    @Autowired
    public CassandraCacheStoreHealth(DataSource igniteCassandraDataSource, Ignite ignite) {
        this.dataSource = igniteCassandraDataSource;
        igniteLogger = ignite.log();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try (final CassandraSession session = dataSource.session(igniteLogger)) {

            session.execute(new BatchLoaderAssistant() {
                @Override
                public String operationName() {
                    return "checkHealth";
                }

                @Override
                public Statement getStatement() {
                    return QueryBuilder.select(new String[]{"release_version"}).from("system", "local");
                }

                @Override
                public void process(Row row) {
                    final String version = row.getString(0);
                    builder.withDetail("version", version).up();
                }
            });
        }
    }
}
