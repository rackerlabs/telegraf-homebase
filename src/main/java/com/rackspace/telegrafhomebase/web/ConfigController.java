package com.rackspace.telegrafhomebase.web;

import com.rackspace.telegrafhomebase.model.AssignedInputDefinition;
import com.rackspace.telegrafhomebase.model.ConfigResponse;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RegionalInputDefinition;
import com.rackspace.telegrafhomebase.services.ConfigRepository;
import com.rackspace.telegrafhomebase.services.TaggingRepository;
import com.rackspace.telegrafhomebase.shared.NotFoundException;
import com.rackspace.telegrafhomebase.shared.NotOwnedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is the REST interface for configuring managed telegraf inputs.
 *
 * NOTE: the tenant parameters may later be dicated by spring security.
 *
 * @author Geoff Bourne
 * @since Jul 2017
 */
@RestController
@RequestMapping("/config")
@Slf4j
public class ConfigController {

    private final ConfigRepository configRepository;
    private final TaggingRepository taggingRepository;

    @Autowired
    public ConfigController(ConfigRepository configRepository, TaggingRepository taggingRepository) {
        this.configRepository = configRepository;
        this.taggingRepository = taggingRepository;
    }

    @GetMapping("{tenantId}")
    public List<ManagedInput> getAllForTenant(@PathVariable String tenantId) {
        return configRepository.getAllForTenant(tenantId);
    }

    @GetMapping("{tenantId}/{id}")
    public ManagedInput getOne(@PathVariable String tenantId, @PathVariable String id) throws NotFoundException, NotOwnedException {
        return configRepository.getWithDetails(tenantId, id);
    }

    @DeleteMapping("{tenantId}/{id}")
    public void delete(@PathVariable String tenantId, @PathVariable String id) throws NotOwnedException {
        configRepository.delete(tenantId, id);
    }

    @PostMapping(value = "{tenantId}/regional")
    public ConfigResponse createConfig(@PathVariable String tenantId,
                                       @RequestBody @Validated RegionalInputDefinition definition) {

        final ConfigResponse resp = new ConfigResponse();
        resp.setCreated(
                configRepository.createRegional(tenantId, definition)
        );

        return resp;
    }

    @PostMapping(value = "{tenantId}/assigned")
    public ConfigResponse assignConfig(@RequestBody @Valid AssignedInputDefinition definition,
                                       @PathVariable String tenantId) {

        final String id = configRepository.createAssigned(tenantId, definition);

        final ConfigResponse resp = new ConfigResponse();
        resp.setCreated(Collections.singletonList(id));
        return resp;
    }

    @GetMapping("{tenantId}/tags")
    public Map<String,List<String>> getActiveTags(@PathVariable String tenantId) {
        return taggingRepository.getActiveTags(tenantId);
    }
}
