package io.nop.vertx.commons;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.vertx.core.Vertx;

import static io.nop.vertx.commons.VertxCommonErrors.ERR_VERTX_NOT_INITIALIZED;

@GlobalInstance
public class NopVertx {
    private static Vertx _instance;

    public static Vertx instance() {
        Vertx instance = _instance;
        if (instance == null)
            throw new NopException(ERR_VERTX_NOT_INITIALIZED);

        return instance;
    }

    public static void registerInstance(Vertx vertx) {
        _instance = vertx;
    }
}
