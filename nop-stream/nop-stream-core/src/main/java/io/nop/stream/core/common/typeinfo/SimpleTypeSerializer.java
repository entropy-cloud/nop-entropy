/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeinfo;

import java.io.*;

public class SimpleTypeSerializer<T> implements TypeSerializer<T> {

    private static final long serialVersionUID = 1L;

    private final Class<T> typeClass;

    public SimpleTypeSerializer(Class<T> typeClass) {
        this.typeClass = typeClass;
    }

    @Override
    public byte[] serialize(T value) throws Exception {
        if (value == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(value);
        oos.flush();
        return bos.toByteArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] data) throws Exception {
        if (data == null) return null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        return (T) ois.readObject();
    }
}
