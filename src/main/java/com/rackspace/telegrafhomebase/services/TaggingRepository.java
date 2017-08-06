package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteTransactions;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.util.Map;

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

    private class TaggedNodesAdder implements EntryProcessor<TaggedNodesKey, TaggedNodes, Boolean> {

        @Override
        public Boolean process(MutableEntry<TaggedNodesKey, TaggedNodes> entry,
                               Object... args) throws EntryProcessorException {

            Assert.notEmpty(args, "Requires tid arg");
            Assert.isInstanceOf(String.class, args[0]);

            final String tid = (String) args[0];

            final TaggedNodes nodes = entry.getValue() != null ? entry.getValue() : new TaggedNodes();

            final boolean didntContain = nodes.getTids().add(tid);
            if (!didntContain) {
                log.warn("Tagged node={} was already present with tag {}", tid, entry.getKey());
            }

            return didntContain;
        }
    }}
