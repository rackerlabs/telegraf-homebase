package com.rackspace.mmi.telegrafhomebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Component @ConfigurationProperties("cassandra") @Data
public class CassandraProperties {

    /**
     * Array of contact points (hostaname:[port]) to use for Cassandra connection
     */
    String[] contactPoints;

    public boolean enabled() {
        return contactPoints != null && contactPoints.length > 0;
    }
}
