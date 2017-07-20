package com.rackspace.telegrafhomebase.web;

import com.rackspace.telegrafhomebase.model.ConfigResponse;
import com.rackspace.telegrafhomebase.model.StoredRegionalConfig;
import com.rackspace.telegrafhomebase.services.ConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@RestController
@RequestMapping("/config")
@Slf4j
public class ConfigController {

    private final ConfigRepository configRepository;

    @Autowired
    public ConfigController(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @GetMapping("{tenantId}")
    public List<StoredRegionalConfig> getAllForTenant(@PathVariable String tenantId) {
        return configRepository.getAllForTenant(tenantId);
    }

    @GetMapping("{tenantId}/{region}/{id}")
    public StoredRegionalConfig getOne(@PathVariable String region, @PathVariable String id) {
        return configRepository.getWithDetails(region, id);
    }

    @DeleteMapping("{tenantId}/{region}/{id}")
    public void delete(@PathVariable String region, @PathVariable String id) {
        configRepository.delete(id);
    }

    @PostMapping(value = "{tenantId}/{region}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ConfigResponse createConfig(@RequestBody String definition,
                                       @PathVariable String tenantId,
                                       @PathVariable String region,
                                       @RequestParam Optional<String> title) {

        final String id = configRepository.createRegional(tenantId, region, definition,
                                                          title.orElse(null));

        final ConfigResponse resp = new ConfigResponse();
        resp.setId(id);
        return resp;
    }
}