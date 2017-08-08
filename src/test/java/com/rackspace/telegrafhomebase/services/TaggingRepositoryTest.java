package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheConfigs;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.config.IgniteConfig;
import com.rackspace.telegrafhomebase.config.IgniteProperties;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.model.TaggedNodes;
import com.rackspace.telegrafhomebase.model.TaggedNodesKey;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        IgniteConfig.class,
        TaggingRepository.class,
        IgniteCacheConfigs.class,
        IgniteCacheProvider.class,
        IgniteProperties.class,
        TelegrafProperties.class
}, properties = {
        "logging.level.org.apache.ignite.internal.processors.query.h2=debug"
})
public class TaggingRepositoryTest {

    @Autowired
    TaggingRepository taggingRepository;

    @Autowired
    IgniteCacheProvider cacheProvider;

    @Test
    public void getActiveTags() throws Exception {

        populateTypicalContent();

        final TaggedNodes nodes = cacheProvider.taggedNodesCache().get(new TaggedNodesKey("ac-1", "os", "linux"));
        assertNotNull(nodes);
        assertThat(nodes.getTids(), CoreMatchers.hasItems("t-1", "t-2"));

        cacheProvider.taggedNodesCache().indexReadyFuture().get();

        final MultiValueMap<String, String> result = taggingRepository.getActiveTags("ac-1");
        assertThat(result.get("host"), CoreMatchers.hasItems("machine-1", "machine-2"));
        assertThat(result.get("os"), CoreMatchers.hasItems("linux"));
        assertThat(result.get("distro"), CoreMatchers.hasItems("ubuntu","centos"));
    }

    @Test
    public void testFindMatches_oneTagMultiMatch() throws Exception {
        populateTypicalContent();

        Map<String, String> requestedTags = new HashMap<>();
        requestedTags.put("os", "linux");
        final Collection<String> result = taggingRepository.findMatches("ac-1", requestedTags);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertThat(result, CoreMatchers.hasItems("t-1", "t-2"));
    }

    @Test
    public void testFindMatches_noIntersection() throws Exception {
        populateTypicalContent();

        Map<String, String> requestedTags = new HashMap<>();
        requestedTags.put("host", "machine-1");
        requestedTags.put("distro", "centos"); // here's the mismatch to test
        final Collection<String> result = taggingRepository.findMatches("ac-1", requestedTags);

        assertNull(result);
    }

    @Test
    public void testFindMatches_nonMatch() throws Exception {
        populateTypicalContent();

        Map<String, String> requestedTags = new HashMap<>();
        requestedTags.put("os", "windows");
        final Collection<String> result = taggingRepository.findMatches("ac-1", requestedTags);

        assertNull(result);
    }

    private void populateTypicalContent() {
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("host", "machine-1");
        tags1.put("os", "linux");
        tags1.put("distro", "ubuntu");
        taggingRepository.storeNodeTags("ac-1", "t-1", tags1);

        Map<String, String> tags2 = new HashMap<>();
        tags2.put("host", "machine-2");
        tags2.put("os", "linux");
        tags2.put("distro", "centos");
        taggingRepository.storeNodeTags("ac-1", "t-2", tags2);

        // NOTE different tenant
        Map<String, String> tags3 = new HashMap<>();
        tags3.put("host", "machine-3");
        tags3.put("os", "linux");
        tags3.put("distro", "ubuntu");
        taggingRepository.storeNodeTags("ac-2", "t-3", tags3);
    }
}