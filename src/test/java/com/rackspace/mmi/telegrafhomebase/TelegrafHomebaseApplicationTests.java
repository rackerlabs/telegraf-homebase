package com.rackspace.mmi.telegrafhomebase;

import com.rackspace.mmi.telegrafhomebase.services.ConfigPackResponder;
import com.rackspace.mmi.telegrafhomebase.services.ConfigRepository;
import com.rackspace.mmi.telegrafhomebase.services.IdCreator;
import com.rackspace.mmi.telegrafhomebase.services.TelegrafWellBeingHandler;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import remote.Telegraf;

import java.util.Collections;

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
                "ignite.appliedConfigTTL=1",
                "logging.level.org.apache.ignite.internal.util.worker.GridWorker=debug"
        })
public class TelegrafHomebaseApplicationTests {

    @MockBean
    IdCreator idCreator;

    @Autowired
    ConfigRepository configRepository;

    @Autowired
    ConfigPackResponder configPackResponder;

    @Autowired
    TelegrafWellBeingHandler wellBeingHandler;

    @Test
    public void basicEndToEnd() throws InterruptedException {

        when(idCreator.create())
                .thenReturn("id-1");

        configRepository.createRegional("ac-1", "west", "JUST A TEST", null);

        StreamObserver<Telegraf.ConfigPack> observer1 = Mockito.mock(StreamObserver.class);
        doAnswer(new OneTimeObserver())
                .when(observer1).onNext(any());

        StreamObserver<Telegraf.ConfigPack> observer2 = Mockito.mock(StreamObserver.class);
        doAnswer(new OneTimeObserver())
                .when(observer2).onNext(any());

        configPackResponder.startConfigStreaming("t-1", "west", observer1);

        Telegraf.Config expectedConfig = Telegraf.Config.newBuilder()
                .setId("id-1")
                .setTenantId("ac-1")
                .setDefinition("JUST A TEST")
                .build();
        Telegraf.ConfigPack expectedConfigPack = Telegraf.ConfigPack.newBuilder()
                .addNew(expectedConfig)
                .build();

        verify(observer1, timeout(500).times(1))
                .onNext(expectedConfigPack);

        // now start a second "remote telegraf"
        configPackResponder.startConfigStreaming("t-2", "west", observer2);

        final Telegraf.CurrentStateResponse normalResp = wellBeingHandler.confirmState("t-1",
                                                                                       "west",
                                                                                       Collections.singletonList("id-1"));
        assertThat(normalResp.getRemovedIdList(), empty());

        System.out.println("Allowing for expiration");
        Thread.sleep(1500L);

        // ...the second "remote" should have been given the config
        verify(observer2, timeout(500).times(1))
                .onNext(expectedConfigPack);

        // ...but if t-1 comes back and reports it, tell it to remove
        final Telegraf.CurrentStateResponse nowRemovedResp = wellBeingHandler.confirmState("t-1",
                                                                                           "west",
                                                                                           Collections.singletonList(
                                                                                                   "id-1"));
        assertThat(nowRemovedResp.getRemovedIdList(), hasSize(1));

        // ...and sanity check a status update from t-2
        final Telegraf.CurrentStateResponse normalResp2 = wellBeingHandler.confirmState("t-2",
                                                                                        "west",
                                                                                        Collections.singletonList("id-1"));
        assertThat(normalResp2.getRemovedIdList(), empty());

    }

    private static class OneTimeObserver implements Answer {
        int count = 0;

        @Override
        public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
            if (count++ > 0) {
                throw new StatusRuntimeException(Status.CANCELLED);
            }
            return null;
        }
    }
}
