package io.nop.message.core;

import io.nop.api.core.exceptions.ErrorCode;

public interface MessageCoreErrors {
    String PREFIX = "nop.err.message.core.";

    ErrorCode ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED =
            ErrorCode.define(PREFIX + "local-redelivery-schedule-failed",
                    "Local ConsumeLater redelivery could not be scheduled (scheduler rejected)");
}
