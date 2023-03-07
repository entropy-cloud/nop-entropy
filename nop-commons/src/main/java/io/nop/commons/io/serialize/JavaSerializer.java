/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.serialize;

import io.nop.api.core.exceptions.NopException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class JavaSerializer implements IByteArraySerializer, IStreamSerializer, IObjectSerializer {
    public static JavaSerializer INSTANCE = new JavaSerializer();

    @Override
    public Object serializeTo(Object o) {
        return serializeToByteArray(o);
    }

    @Override
    public Object deserializeFrom(Object s) {
        return this.deserializeFromByteArray((byte[]) s);
    }

    @Override
    public ObjectOutput getObjectOutput(OutputStream os) {
        try {
            return new ObjectOutputStream(os);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public ObjectInput getObjectInput(InputStream is) {
        try {
            return new ObjectInputStream(is);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void serializeToStream(Object o, OutputStream os) {
        try {
            ObjectOutput out = getObjectOutput(os);
            out.writeObject(o);
            out.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public Object deserializeFromStream(InputStream is) {
        try {
            return getObjectInput(is).readObject();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public byte[] serializeToByteArray(Object o) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializeToStream(o, out);
        return out.toByteArray();
    }

    @Override
    public Object deserializeFromByteArray(byte[] data) {
        if (data == null)
            return null;
        return this.deserializeFromStream(new ByteArrayInputStream(data));
    }
}