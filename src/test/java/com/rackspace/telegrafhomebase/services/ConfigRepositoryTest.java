package com.rackspace.telegrafhomebase.services;

import com.rackspace.telegrafhomebase.config.IgniteCacheConfigs;
import com.rackspace.telegrafhomebase.config.IgniteCacheProvider;
import com.rackspace.telegrafhomebase.config.IgniteConfig;
import com.rackspace.telegrafhomebase.config.IgniteProperties;
import com.rackspace.telegrafhomebase.config.TelegrafProperties;
import com.rackspace.telegrafhomebase.model.AssignedInputDefinition;
import com.rackspace.telegrafhomebase.model.ManagedInputExt;
import com.rackspace.telegrafhomebase.model.RunningAssignedInputKey;
import org.apache.ignite.IgniteCache;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Geoff Bourne
 * @since Aug 2017
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        IgniteConfig.class,
        ConfigRepository.class,
        TaggingRepository.class,
        IgniteCacheConfigs.class,
        IgniteCacheProvider.class,
        IgniteProperties.class,
        TelegrafProperties.class
}, properties = {
        "logging.level.org.apache.ignite.internal.processors.query.h2=debug"
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ConfigRepositoryTest {

    @Autowired
    ConfigRepository configRepository;

    @Autowired
    TaggingRepository taggingRepository;

    @Autowired
    IgniteCache<RunningAssignedInputKey, String/*cluster node id*/> runningAssignedInputsCache;

    @MockBean
    IdCreator idCreator;

    @Test
    public void testCreateAssigned() throws Exception {
        Mockito.when(idCreator.create()).thenReturn("id-1");

        // fake out node connections
        taggingRepository.storeNodeTags("ac-1", "t-1", Collections.singletonMap("os", "linux"));
        taggingRepository.storeNodeTags("ac-1", "t-2", Collections.singletonMap("os", "linux"));

        AssignedInputDefinition definition = new AssignedInputDefinition();
        definition.setText("[[inputs.mem]]");
        definition.setAssignmentTags(Collections.singletonMap("os", "linux"));

        final String mid = configRepository.createAssigned("ac-1", definition);
        assertEquals("id-1", mid);

        // fake out the telegraf node assignments
        runningAssignedInputsCache.put(new RunningAssignedInputKey(mid, "t-1"), "grid-node-1");
        runningAssignedInputsCache.put(new RunningAssignedInputKey(mid, "t-2"), "grid-node-1");

        final List<ManagedInputExt> inputs = configRepository.getAllForTenant("ac-1");
        assertNotNull(inputs);
        assertEquals(1, inputs.size());

        assertThat(inputs.get(0).getRunningOn(), CoreMatchers.hasItems("t-1", "t-2"));
    }

}