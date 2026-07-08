package io.nop.rpc.model.validate;

import io.nop.xlang.xmeta.validate.ValidationContext;

public interface IMessageValidator {
    void validate(String messageType, Object message, ValidationContext validationContext);
}
