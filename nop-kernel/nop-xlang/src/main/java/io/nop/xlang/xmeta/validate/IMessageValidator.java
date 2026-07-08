package io.nop.xlang.xmeta.validate;

public interface IMessageValidator {
    void validate(String messageType, Object message, ValidationContext validationContext);
}
