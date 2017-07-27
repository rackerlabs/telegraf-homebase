package com.rackspace.telegrafhomebase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@ConfigurationProperties("telegraf")
@Data
@Component
public class TelegrafProperties {

    /**
     * Defines the allowed/expected regions where remote telegrafs will be originating their contact with us.
     */
    List<String> regions;
}
