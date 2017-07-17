package com.rackspace.telegrafhomebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Geoff Bourne
 * @since Jun 2017
 */
@Component
@ConfigurationProperties("ignite")
@Data
public class IgniteProperties {
    String zkConnection;
    /**
     * <a href="https://ignite.apache.org/releases/mobile/org/apache/ignite/configuration/IgniteConfiguration.html#setMetricsLogFrequency(long)">Pass-thru config</a>
     */
    long metricsLogFrequency = 0;

    int storedConfigBackups = 1;

    /**
     * The amount of time an applied config can go without a keep alive (in seconds)
     */
    int appliedConfigTtl = 30;

    int appliedConfigBackups = 1;

    /**
     * The TTL of a tracked telegraf instance (in seconds)
     */
    int telegrafInstanceTtl = 120;
}
