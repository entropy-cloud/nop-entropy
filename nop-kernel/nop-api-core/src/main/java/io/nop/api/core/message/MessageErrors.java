package io.nop.api.core.message;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * Error codes for the message SPI layer (adjudication D9: prefix {@code nop.err.message.*}).
 */
public interface MessageErrors {
    String PREFIX = "nop.err.message.";

    ErrorCode ERR_OPTION_NOT_SUPPORTED =
            ErrorCode.define(PREFIX + "option-not-supported",
                    "Message option field is not supported by this message service backend",
                    "field", "capability", "backend");

    ErrorCode ERR_SUBSCRIBE_NOT_SUPPORTED =
            ErrorCode.define(PREFIX + "subscribe-not-supported",
                    "Subscribe is not supported by this message service backend");

    ErrorCode ERR_SEEK_MESSAGE_NOT_SUPPORTED =
            ErrorCode.define(PREFIX + "seek-message-not-supported",
                    "Seek by message id is not supported by this message service backend");

}
