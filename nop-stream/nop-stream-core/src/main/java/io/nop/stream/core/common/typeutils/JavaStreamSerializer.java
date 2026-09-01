/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Java-serialization based {@link IStreamSerializer} for state values whose object
 * graph is {@link Serializable} but not JSON-{@code @DataBean}-shaped (e.g. the CEP
 * NFA/shared-buffer state graph, which contains queues, atomic counters and
 * generics the JSON path cannot round-trip).
 *
 * <p>Values serialized through this serializer are embedded into the JSON snapshot
 * as {@code byte[]} (base64 by the JSON layer), so the snapshot stays
 * backend-neutral and the restore path re-materializes the exact runtime types.
 */
public final class JavaStreamSerializer<T extends Serializable> implements IStreamSerializer<T> {

    private static final long serialVersionUID = 1L;

    public static final JavaStreamSerializer<?> INSTANCE = new JavaStreamSerializer<>();

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> JavaStreamSerializer<T> of() {
        return (JavaStreamSerializer<T>) INSTANCE;
    }

    private JavaStreamSerializer() {
    }

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public TypeSerializer<T> duplicate() {
        return this;
    }

    @Override
    public T createInstance() {
        return null;
    }

    @Override
    public T copy(T from) {
        return from;
    }

    @Override
    public T copy(T from, T reuse) {
        return from;
    }

    @Override
    public int getLength() {
        // variable-length encoding
        return -1;
    }

    @Override
    public byte[] serialize(T value) {
        if (value == null) {
            return null;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param("stateDetail", "Java-stream serialize failed for " + value.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] data, Class<T> type) {
        if (data == null) {
            return null;
        }
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, e)
                    .param("stateDetail", "Java-stream deserialize failed for " + type.getName());
        }
    }
}
