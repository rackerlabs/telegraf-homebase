package com.rackspace.mmi.telegrafhomebase.web;

import com.rackspace.mmi.telegrafhomebase.CacheNames;
import com.rackspace.mmi.telegrafhomebase.model.ConfigResponse;
import com.rackspace.mmi.telegrafhomebase.model.StoredRegionalConfig;
import com.rackspace.mmi.telegrafhomebase.services.ConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
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
import java.util.UUID;

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

    @GetMapping
    public List<StoredRegionalConfig> getAll() {
        return configRepository.getAll();
    }

    @DeleteMapping("/{region}/{id}")
    public void delete(@PathVariable String region, @PathVariable String id) {
        configRepository.delete(id);
    }

    @PostMapping(value = "{region}", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ConfigResponse createConfig(@RequestBody String definition, @PathVariable String region) {

        final String id = configRepository.createRegional(region, definition);

        final ConfigResponse resp = new ConfigResponse();
        resp.setId(id);
        return resp;
    }
}
