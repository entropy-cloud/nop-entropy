/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.model;

import io.nop.record.codec.IFieldBinaryCodec;
import io.nop.record.codec.IFieldTextCodec;
import io.nop.record.model._gen._RecordFieldMeta;

public class RecordFieldMeta extends _RecordFieldMeta {
    private IFieldTextCodec resolvedTextCodec;
    private IFieldBinaryCodec resolvedBinaryCodec;

    public RecordFieldMeta() {

    }

    public IFieldTextCodec getResolvedTextCodec() {
        return resolvedTextCodec;
    }

    public void setResolvedTextCodec(IFieldTextCodec resolvedTextCodec) {
        this.resolvedTextCodec = resolvedTextCodec;
    }

    public IFieldBinaryCodec getResolvedBinaryCodec() {
        return resolvedBinaryCodec;
    }

    public void setResolvedBinaryCodec(IFieldBinaryCodec resolvedBinaryCodec) {
        this.resolvedBinaryCodec = resolvedBinaryCodec;
    }

    public int safeGetMaxLen() {
        if (getMaxLength() == null)
            return getLength();
        int max = getMaxLength();
        if (max > 0)
            return max;
        return getLength();
    }

    public int safeGetMinLen() {
        if (getMinLength() == null)
            return getLength();
        int min = getMinLength();
        if (min > 0)
            return min;
        return getLength();
    }
}
