/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codec.compress;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.util.ReferenceCountUtil;
import io.nop.api.core.exceptions.NopException;
import io.nop.codec.support.AbstractByteBufCodec;
import io.nop.codec.util.ByteBufHelper;
import io.nop.commons.util.IoHelper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class DeflateCompressCodec extends AbstractByteBufCodec {

    @Override
    public ByteBuf encodeBuf(ByteBuf data, ByteBufAllocator allocator) {
        ByteBufOutputStream out = new ByteBufOutputStream(allocator.buffer());
        try {
            OutputStream zip = new DeflaterOutputStream(out);
            try {
                ByteBufHelper.writeBuf(zip, data);
            } finally {
                // 异常路径也必须关闭 zip，及时释放 deflater 的 native 内存
                IoHelper.safeCloseObject(zip);
            }
            return out.buffer();
        } catch (Exception e) {
            ReferenceCountUtil.release(out.buffer());
            throw NopException.adapt(e);
        }
    }

    @Override
    public ByteBuf decodeBuf(ByteBuf data, ByteBufAllocator allocator) {
        ByteBufOutputStream output = new ByteBufOutputStream(allocator.buffer());
        ByteBufInputStream input = new ByteBufInputStream(data);
        try {
            InputStream zip = new InflaterInputStream(input);
            try {
                IoHelper.copy(zip, output);
            } finally {
                IoHelper.safeCloseObject(zip);
            }
            return output.buffer();
        } catch (Exception e) {
            ReferenceCountUtil.release(output.buffer());
            throw NopException.adapt(e);
        }
    }
}