package com.rackspace.telegrafhomebase.services;

import com.google.common.base.Strings;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.AssignedInputDefinition;
import com.rackspace.telegrafhomebase.model.ManagedInput;
import com.rackspace.telegrafhomebase.model.RegionalInputDefinition;
import com.rackspace.telegrafhomebase.model.RunningRegionalInputKey;
import com.rackspace.telegrafhomebase.model.StructuredInputConfig;
import com.rackspace.telegrafhomebase.shared.NotFoundException;
import com.rackspace.telegrafhomebase.shared.NotOwnedException;
import com.rackspace.telegrafhomebase.shared.StructuredInputFormatter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Repository
@Slf4j
public class ConfigRepository {
    private final IgniteCache<String, ManagedInput> managedInputsCache;
    private final IgniteCache<RunningRegionalInputKey, String> runningCache;
    private final IdCreator idCreator;
    private final TaggingRepository taggingRepository;
    private final IgniteTransactions transactions;
    private final StructuredInputFormatter structuredInputFormatter
            = new StructuredInputFormatter();

    @Autowired
    public ConfigRepository(Ignite ignite,
                            IdCreator idCreator,
                            IgniteCacheProvider cacheProvider,
                            TaggingRepository taggingRepository) {
        managedInputsCache = cacheProvider.managedInputsCache();
        runningCache = cacheProvider.runningRegionalInputsCache();
        transactions = ignite.transactions();
        this.idCreator = idCreator;
        this.taggingRepository = taggingRepository;
    }

    @PostConstruct
    public void loadCache() {
        managedInputsCache.localLoadCacheAsync(null);
    }

    public List<String> createRegional(String tenantId, RegionalInputDefinition definition) {
        List<String> ids = new ArrayList<>(definition.getRegions().size());

        final String definitionText = resolveDefinitionText(definition.getStructured(), definition.getText());

        if (Strings.isNullOrEmpty(definitionText)) {
            throw new IllegalArgumentException("text or structured needs to be specified");
        }

        try (Transaction tx = transactions.txStart()) {
            for (String region : definition.getRegions()) {
                final ManagedInput config = new ManagedInput();
                final String id = idCreator.create();
                ids.add(id);
                config.setId(id);
                config.setText(definitionText);
                config.setStructured(definition.getStructured());
                config.setTitle(definition.getTitle());
                config.setTenantId(tenantId);
                config.setRegion(region);

                log.debug("Creating regional configuration: {}", config);

                managedInputsCache.put(config.getId(), config);
            }

            tx.commit();
        }

        return ids;
    }

    public String createAssigned(String tenantId, AssignedInputDefinition definition) {
        final Collection<String> matchingTelegrafs = taggingRepository.findMatches(tenantId,
                                                                                   definition.getAssignmentTags());

        if (matchingTelegrafs == null || matchingTelegrafs.isEmpty()) {
            throw new IllegalArgumentException("Unable to find any running telegrafs that satisfy the assignment tags");
        }

        final String definitionText = resolveDefinitionText(definition.getStructured(), definition.getText());

        try (Transaction tx = transactions.txStart()) {
            final ManagedInput config = new ManagedInput();
            final String id = idCreator.create();
            config.setId(id);
            config.setText(definitionText);
            config.setStructured(definition.getStructured());
            config.setTitle(definition.getTitle());
            config.setTenantId(tenantId);
            config.setAssignmentTags(definition.getAssignmentTags());

            log.debug("Creating assigned configuration: {}", config);

            managedInputsCache.putAsync(config.getId(), config);

            tx.commit();
        }


        return null;
    }

    private String resolveDefinitionText(StructuredInputConfig structured, String text) {
        final String definitionText;
        if (structured != null) {
            definitionText = structuredInputFormatter.format(structured);
        } else {
            definitionText = text;
        }
        return definitionText;
    }

    public void delete(String tenantId, String id) throws NotOwnedException {
        log.info("Deleting {}", id);

        final boolean valid;
        try (Transaction tx = transactions.txStart()) {
            valid = managedInputsCache.invoke(id, (mutableEntry, args) -> {
                if (mutableEntry.getValue() != null) {
                    if (args.length >= 1 && args[0] instanceof String) {
                        final String expectedTenantId = (String) args[0];

                        if (Objects.equals(expectedTenantId, mutableEntry.getValue().getTenantId())) {
                            mutableEntry.remove();

                            return true;
                        }
                    }

                    return false;
                }
                // doesn't exist, so that's fine
                return true;
            }, tenantId);

            tx.commit();
        }

        if (!valid) {
            throw new NotOwnedException("Not owned by tenant");
        }
    }

    ManagedInput get(String id) {
        return managedInputsCache.get(id);
    }

    public ManagedInput getWithDetails(String tenantId, String id) throws NotFoundException, NotOwnedException {
        final ManagedInput managedInput = managedInputsCache.get(id);
        if (managedInput == null) {
            throw new NotFoundException("Managed input with given id doesn't exist", id);
        }
        if (!managedInput.getTenantId().equals(tenantId)) {
            log.debug("Attempted to retrieve managed input={} for wrong tenant={}", id, tenantId);
            throw new NotOwnedException("Not owned by tenant");
        }

        managedInput.setRunningOn(runningCache.get(new RunningRegionalInputKey(id, managedInput.getRegion())));

        return managedInput;
    }

    public List<ManagedInput> getAllForTenant(String tenantId) {
        final SqlQuery<String, ManagedInput> query = new SqlQuery<>(
                ManagedInput.class,
                "tenantid = ?"
        );

        try (Transaction tx = transactions.txStart()) {
            final QueryCursor<Cache.Entry<String, ManagedInput>> queryCursor =
                    managedInputsCache.query(query.setArgs(tenantId));
            return queryCursor.getAll().stream()
                    .map(entry -> {
                        final ManagedInput value = entry.getValue();

                        value.setRunningOn(runningCache.get(new RunningRegionalInputKey(value.getId(),
                                                                                        value.getRegion())));

                        return value;
                    })
                    .collect(Collectors.toList());
        }
    }
}
