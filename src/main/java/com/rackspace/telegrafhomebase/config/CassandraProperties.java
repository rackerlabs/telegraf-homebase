package com.rackspace.telegrafhomebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Profile(CassandraProperties.PROFILE)
@Component @ConfigurationProperties(CassandraProperties.PREFIX) @Data
public class CassandraProperties {

    public static final String PREFIX = "cassandra";
    public static final String PROFILE = "CassandraCacheStore";

    /**
     * Array of contact points (hostaname:[port]) to use for Cassandra connection
     */
    String[] contactPoints;
}
