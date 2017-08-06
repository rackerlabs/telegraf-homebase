package com.rackspace.telegrafhomebase.services;

import io.grpc.stub.StreamObserver;
import org.springframework.scheduling.annotation.Async;
import remote.Telegraf;

import java.util.Map;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public interface ConfigPackResponder {
    @Async
    void startConfigStreaming(Telegraf.Identifiers identifiers,
                              Map<String, String> nodeTags,
                              StreamObserver<Telegraf.ConfigPack> responseObserver);
}
