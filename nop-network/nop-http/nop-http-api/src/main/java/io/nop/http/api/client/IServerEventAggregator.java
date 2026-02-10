package io.nop.http.api.client;

public interface IServerEventAggregator {

    void onNext(IServerEventResponse event);

    default void onError(Throwable e) {

    }

    IHttpResponse getFinalResult();
}