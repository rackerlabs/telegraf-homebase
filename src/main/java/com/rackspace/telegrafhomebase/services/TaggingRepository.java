package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@Repository @Slf4j
public class TaggingRepository {
    private final IgniteTransactions igniteTransactions;
    private final IgniteCache<TaggedNodesKey, TaggedNodes> taggedNodes;
    private final TaggedNodesAdder taggedNodesAdder = new TaggedNodesAdder();

    @Autowired
    public TaggingRepository(IgniteTransactions igniteTransactions, IgniteCacheProvider cacheProvider) {
        this.igniteTransactions = igniteTransactions;
        taggedNodes = cacheProvider.taggedNodesCache();
    }

    public void storeNodeTags(String tenantId, String tid, Map<String, String> nodeTags) {

        try (Transaction tx = igniteTransactions.txStart()) {
            nodeTags.forEach((name, value) ->
                                     taggedNodes.invoke(new TaggedNodesKey(tenantId, name, value),
                                                        taggedNodesAdder,
                                                        tid));

            tx.commit();
        }
    }

    public MultiValueMap<String,String> getActiveTags(String tenantId) {
        final SqlFieldsQuery query = new SqlFieldsQuery("select name, value from TaggedNodes as t" +
                                                                         " where t.tenantId = ?");

            final MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
            taggedNodes.query(query.setArgs(tenantId)).forEach(cols -> {
                result.add(((String) cols.get(0)), ((String) cols.get(1)));
            });

            return result;
    }

    public Collection<String> findMatches(String tenantId, Map<String, String> requestedTags) {
        Set<String> intersection = null;

        for (Map.Entry<String, String> entry : requestedTags.entrySet()) {
            final TaggedNodes matches = this.taggedNodes.get(new TaggedNodesKey(tenantId,
                                                                                    entry.getKey(),
                                                                                    entry.getValue()));

            if (matches == null) {
                return null;
            }

            if (intersection == null) {
                intersection = new HashSet<>(matches.getTids());
            }
            else {
                intersection.retainAll(matches.getTids());
                if (intersection.isEmpty()) {
                    return null;
                }
            }
        }

        return intersection;
    }

    private class TaggedNodesAdder implements EntryProcessor<TaggedNodesKey, TaggedNodes, Boolean> {

        @Override
        public Boolean process(MutableEntry<TaggedNodesKey, TaggedNodes> entry,
                               Object... args) throws EntryProcessorException {

            Assert.notEmpty(args, "Requires tid arg");
            Assert.isInstanceOf(String.class, args[0]);

            final String tid = (String) args[0];

            final TaggedNodes nodes;
            if (entry.getValue() == null) {
                nodes = new TaggedNodes();
            } else {
                nodes = entry.getValue();
            }

            final boolean didntContain = nodes.getTids().add(tid);
            if (didntContain) {
                entry.setValue(nodes);
            } else {
                log.warn("Tagged node={} was already present with tag {}", tid, entry.getKey());
            }

            return didntContain;
        }
    }}
