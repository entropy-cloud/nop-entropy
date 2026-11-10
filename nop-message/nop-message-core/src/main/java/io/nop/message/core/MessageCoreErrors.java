package io.nop.message.core;

import io.nop.api.core.exceptions.ErrorCode;

public interface MessageCoreErrors {
    String PREFIX = "nop.err.message.core.";

    ErrorCode ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED =
            ErrorCode.define(PREFIX + "local-redelivery-schedule-failed",
                    "Local ConsumeLater redelivery could not be scheduled (scheduler rejected)");

    // 4.1-MA-01（plan 2558 裁定 A·Local）: bounded consume wait
    ErrorCode ERR_LOCAL_CONSUME_AWAIT_TIMEOUT =
            ErrorCode.define(PREFIX + "local-consume-await-timeout",
                    "Local consumer handler CompletionStage did not complete within consumeAwaitTimeoutMs");

    ErrorCode ERR_LOCAL_CONSUME_AWAIT_NOT_FUTURE =
            ErrorCode.define(PREFIX + "local-consume-await-not-future",
                    "Local consumer handler returned a CompletionStage that is not a CompletableFuture");

    // 4.1-MA-04（plan 2558 裁定 C）: bounded per-subscription pending redelivery queue
    ErrorCode ERR_LOCAL_REDELIVERY_PENDING_OVERFLOW =
            ErrorCode.define(PREFIX + "local-redelivery-pending-overflow",
                    "Local per-subscription pending redelivery cap exceeded; deferred message dropped with proof");
}
