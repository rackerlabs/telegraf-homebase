package com.rackspace.mmi.telegrafhomebase.services;

import io.grpc.stub.StreamObserver;
import org.springframework.scheduling.annotation.Async;
import remote.Telegraf;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
public interface ConfigPackResponder {
    @Async
    void startConfigStreaming(String tid, String region, StreamObserver<Telegraf.ConfigPack> responseObserver);
}
