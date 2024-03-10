/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.encoder;

import io.nop.api.core.annotations.core.GlobalInstance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@GlobalInstance
public class FieldEncoderRegistry {
    public static FieldEncoderRegistry DEFAULT = new FieldEncoderRegistry();

    private final Map<String, IFieldTextEncoder> textEncoders = new ConcurrentHashMap<>();

    private final Map<String, IFieldBinaryEncoder> binaryEncoders = new ConcurrentHashMap<>();

    public void registerTextEncoder(String name, IFieldTextEncoder encoder) {
        textEncoders.put(name, encoder);
    }

    public void unregisterTextEncoder(String name, IFieldTextEncoder encoder) {
        textEncoders.remove(name, encoder);
    }

    public void registerBinaryEncoder(String name, IFieldBinaryEncoder encoder) {
        binaryEncoders.put(name, encoder);
    }

    public void unregisterBinaryEncoder(String name, IFieldBinaryEncoder encoder) {
        binaryEncoders.remove(name, encoder);
    }

    public IFieldTextEncoder getTextEncoder(String name) {
        return textEncoders.get(name);
    }

    public IFieldBinaryEncoder getBinaryEncoder(String name) {
        return binaryEncoders.get(name);
    }
}
