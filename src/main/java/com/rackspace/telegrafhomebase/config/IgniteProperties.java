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

    int managedInputsCacheBackups = 1;

    /**
     * The amount of time an applied config can go without a keep alive (in seconds)
     */
    int runningConfigTtl = 30;

    int runningConfigCacheBackups = 1;

    /**
     * The TTL of a tracked telegraf instance (in seconds)
     */
    int telegrafInstanceTtl = 120;

    /**
     * This timeout allows time for the grid participants to wait for initial network grid negotiations.
     */
    long networkTimeout = 30_000;
}
