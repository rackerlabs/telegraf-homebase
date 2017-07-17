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

    List<String> regions;
}
