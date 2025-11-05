/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import io.nop.commons.io.stream.LimitedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public abstract class AbstractMessageParser<MessageType> implements Parser<MessageType> {
    /**
     * Creates an UninitializedMessageException for MessageType.
     */
//    private NopException newUninitializedMessageException(MessageType message) {
//        return new NopException(ERR_GRPC_UNINITIALIZED_MESSAGE);
//    }

    /**
     * Helper method to check if message is initialized.
     *
     * @return The message to check.
     * @throws InvalidProtocolBufferException if it is not initialized.
     */
    private MessageType checkMessageInitialized(MessageType message)
            throws InvalidProtocolBufferException {
//        if (message != null && !message.isInitialized()) {
//            throw newUninitializedMessageException(message)
//                    .asInvalidProtocolBufferException()
//                    .setUnfinishedMessage(message);
//        }
        return message;
    }

    private static final ExtensionRegistryLite EMPTY_REGISTRY =
            ExtensionRegistryLite.getEmptyRegistry();

    @Override
    public MessageType parsePartialFrom(CodedInputStream input)
            throws InvalidProtocolBufferException {
        return parsePartialFrom(input, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return checkMessageInitialized(parsePartialFrom(input, extensionRegistry));
    }

    @Override
    public MessageType parseFrom(CodedInputStream input) throws InvalidProtocolBufferException {
        return parseFrom(input, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parsePartialFrom(ByteString data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        MessageType message;
        try {
            CodedInputStream input = data.newCodedInput();
            message = parsePartialFrom(input, extensionRegistry);
            try {
                input.checkLastTagWas(0);
            } catch (InvalidProtocolBufferException e) {
                //throw e.setUnfinishedMessage(message);
                throw e;
            }
            return message;
        } catch (InvalidProtocolBufferException e) {
            throw e;
        }
    }

    @Override
    public MessageType parsePartialFrom(ByteString data) throws InvalidProtocolBufferException {
        return parsePartialFrom(data, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(ByteString data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return checkMessageInitialized(parsePartialFrom(data, extensionRegistry));
    }

    @Override
    public MessageType parseFrom(ByteString data) throws InvalidProtocolBufferException {
        return parseFrom(data, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(ByteBuffer data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        MessageType message;
        try {
            CodedInputStream input = CodedInputStream.newInstance(data);
            message = parsePartialFrom(input, extensionRegistry);
            try {
                input.checkLastTagWas(0);
            } catch (InvalidProtocolBufferException e) {
                //throw e.setUnfinishedMessage(message);
                throw e;
            }
        } catch (InvalidProtocolBufferException e) {
            throw e;
        }

        return checkMessageInitialized(message);
    }

    @Override
    public MessageType parseFrom(ByteBuffer data) throws InvalidProtocolBufferException {
        return parseFrom(data, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parsePartialFrom(
            byte[] data, int off, int len, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        try {
            CodedInputStream input = CodedInputStream.newInstance(data, off, len);
            MessageType message = parsePartialFrom(input, extensionRegistry);
            try {
                input.checkLastTagWas(0);
            } catch (InvalidProtocolBufferException e) {
                //throw e.setUnfinishedMessage(message);
                throw e;
            }
            return message;
        } catch (InvalidProtocolBufferException e) {
            throw e;
        }
    }

    @Override
    public MessageType parsePartialFrom(byte[] data, int off, int len)
            throws InvalidProtocolBufferException {
        return parsePartialFrom(data, off, len, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parsePartialFrom(byte[] data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return parsePartialFrom(data, 0, data.length, extensionRegistry);
    }

    @Override
    public MessageType parsePartialFrom(byte[] data) throws InvalidProtocolBufferException {
        return parsePartialFrom(data, 0, data.length, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(
            byte[] data, int off, int len, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return checkMessageInitialized(parsePartialFrom(data, off, len, extensionRegistry));
    }

    @Override
    public MessageType parseFrom(byte[] data, int off, int len)
            throws InvalidProtocolBufferException {
        return parseFrom(data, off, len, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(byte[] data, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return parseFrom(data, 0, data.length, extensionRegistry);
    }

    @Override
    public MessageType parseFrom(byte[] data) throws InvalidProtocolBufferException {
        return parseFrom(data, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parsePartialFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        CodedInputStream codedInput = CodedInputStream.newInstance(input);
        MessageType message = parsePartialFrom(codedInput, extensionRegistry);
        try {
            codedInput.checkLastTagWas(0);
        } catch (InvalidProtocolBufferException e) {
            throw e;
        }
        return message;
    }

    @Override
    public MessageType parsePartialFrom(InputStream input) throws InvalidProtocolBufferException {
        return parsePartialFrom(input, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return checkMessageInitialized(parsePartialFrom(input, extensionRegistry));
    }

    @Override
    public MessageType parseFrom(InputStream input) throws InvalidProtocolBufferException {
        return parseFrom(input, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parsePartialDelimitedFrom(
            InputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        int size;
        try {
            int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }
            size = CodedInputStream.readRawVarint32(firstByte, input);
        } catch (IOException e) {
            throw new InvalidProtocolBufferException(e);
        }
        InputStream limitedInput = new LimitedInputStream(input, size);
        return parsePartialFrom(limitedInput, extensionRegistry);
    }

    @Override
    public MessageType parsePartialDelimitedFrom(InputStream input)
            throws InvalidProtocolBufferException {
        return parsePartialDelimitedFrom(input, EMPTY_REGISTRY);
    }

    @Override
    public MessageType parseDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
            throws InvalidProtocolBufferException {
        return checkMessageInitialized(parsePartialDelimitedFrom(input, extensionRegistry));
    }

    @Override
    public MessageType parseDelimitedFrom(InputStream input) throws InvalidProtocolBufferException {
        return parseDelimitedFrom(input, EMPTY_REGISTRY);
    }
}
