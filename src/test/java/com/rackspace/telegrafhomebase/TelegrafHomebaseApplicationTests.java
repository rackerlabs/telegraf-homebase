package com.rackspace.telegrafhomebase;

import com.rackspace.telegrafhomebase.model.RegionalInputDefinition;
import com.rackspace.telegrafhomebase.services.ConfigPackResponder;
import com.rackspace.telegrafhomebase.services.ConfigRepository;
import com.rackspace.telegrafhomebase.services.IdCreator;
import com.rackspace.telegrafhomebase.services.TelegrafWellBeingHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import remote.Telegraf;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.port=0",
                "ignite.runningConfigTtl=1",
                "ignite.managedInputsCacheBackups=1",
                "ignite.runningConfigCacheBackups=1",
                "homebase.initialConsistencyCheckDelay=5",
                "logging.level.com.rackspace.telegrafhomebase=debug"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Slf4j
public class TelegrafHomebaseApplicationTests {

    @MockBean
    IdCreator idCreator;

    @Autowired
    ConfigRepository configRepository;

    @Autowired
    ConfigPackResponder configPackResponder;

    @Autowired
    TelegrafWellBeingHandler wellBeingHandler;

    @SuppressWarnings("unchecked")
    @Test
    public void basicEndToEnd() throws InterruptedException {

        when(idCreator.create())
                .thenReturn("id-1");

        final RegionalInputDefinition definition = new RegionalInputDefinition();
        definition.setRegions(Collections.singletonList("west"));
        definition.setText("JUST A TEST");
        definition.setTitle("Testing");
        configRepository.createRegional("ac-1", definition);

        StreamObserver<Telegraf.ConfigPack> observer1 = Mockito.mock(StreamObserver.class);
        doAnswer(new OneTimeObserver())
                .when(observer1).onNext(any());

        StreamObserver<Telegraf.ConfigPack> observer2 = Mockito.mock(StreamObserver.class);
        doAnswer(new OneTimeObserver())
                .when(observer2).onNext(any());

        final Map<String, String> nodeTags = Collections.emptyMap();
        final Telegraf.Identifiers identifiers1 = Telegraf.Identifiers.newBuilder()
                .setTid("t-1")
                .setRegion("west")
                .build();
        log.debug("Starting t-1");
        configPackResponder.startConfigStreaming(identifiers1, nodeTags, observer1);

        Telegraf.Config expectedConfig = Telegraf.Config.newBuilder()
                .setId("id-1")
                .setTenantId("ac-1")
                .setTitle("Testing")
                .setDefinition("JUST A TEST")
                .build();
        Telegraf.ConfigPack expectedConfigPack = Telegraf.ConfigPack.newBuilder()
                .addNew(expectedConfig)
                .build();

        verify(observer1, timeout(500).times(1))
                .onNext(expectedConfigPack);

        // now start a second "remote telegraf"
        final Telegraf.Identifiers identifiers2 = Telegraf.Identifiers.newBuilder()
                .setTid("t-2")
                .setRegion("west")
                .build();
        log.debug("Starting t-2");
        configPackResponder.startConfigStreaming(identifiers2, nodeTags, observer2);

        Thread.sleep(10);
        log.debug("Checking pre-expiration state");
        final Telegraf.CurrentStateResponse normalResp = wellBeingHandler.confirmState(identifiers1,
                                                                                       Collections.singletonList("id-1"));
        assertThat(normalResp.getRemovedIdList(), empty());

        log.debug("Allowing for expiration");
        Thread.sleep(1500L);

        log.debug("Checking for post-expiration state");
        // ...the second "remote" should have been given the config
        verify(observer2, timeout(500).times(1))
                .onNext(expectedConfigPack);

        // ...but if t-1 comes back and reports it, tell it to remove
        final Telegraf.CurrentStateResponse nowRemovedResp = wellBeingHandler.confirmState(identifiers1,
                                                                                           Collections.singletonList(
                                                                                                   "id-1"));
        assertThat(nowRemovedResp.getRemovedIdList(), hasSize(1));

        // ...and sanity check a status update from t-2
        final Telegraf.CurrentStateResponse normalResp2 = wellBeingHandler.confirmState(identifiers2,
                                                                                        Collections.singletonList("id-1"));
        assertThat(normalResp2.getRemovedIdList(), empty());

    }

    @Slf4j
    private static class OneTimeObserver implements Answer {
        AtomicInteger count = new AtomicInteger();

        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
            if (count.getAndIncrement() > 0) {
                log.debug("Faking a disconnect");
                throw new StatusRuntimeException(Status.CANCELLED);
            }
            return null;
        }
    }
}
