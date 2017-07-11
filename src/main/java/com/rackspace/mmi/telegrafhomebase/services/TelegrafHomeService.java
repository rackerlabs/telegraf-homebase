package com.rackspace.mmi.telegrafhomebase.services;

import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;
import remote.Telegraf;
import remote.TelegrafRemoteGrpc.TelegrafRemoteImplBase;

/**
 * @author Geoff Bourne
 * @since Jul 2017
 */
@GRpcService
public class TelegrafHomeService extends TelegrafRemoteImplBase  {

    private final ConfigPackResponder configPackResponder;
    private final TelegrafWellBeingHandler wellBeingHandler;

    @Autowired
    public TelegrafHomeService(ConfigPackResponder configPackResponder,
                               TelegrafWellBeingHandler wellBeingHandler) {
        this.configPackResponder = configPackResponder;
        this.wellBeingHandler = wellBeingHandler;
    }

    @Override
    public void startConfigStreaming(Telegraf.Greeting request, StreamObserver<Telegraf.ConfigPack> responseObserver) {
        configPackResponder.startConfigStreaming(request.getTid(), request.getRegion(), responseObserver);
    }

    @Override
    public void reportState(Telegraf.CurrentState request, StreamObserver<Telegraf.CurrentStateResponse> responseObserver) {
        try {
            final Telegraf.CurrentStateResponse resp =
                    wellBeingHandler.confirmState(request.getTid(), request.getRegion(), request.getActiveConfigIdsList());

            responseObserver.onNext(resp);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
