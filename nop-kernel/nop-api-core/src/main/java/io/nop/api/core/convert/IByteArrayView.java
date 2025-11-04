/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.convert;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public interface IByteArrayView {
    byte[] EMPTY_BYTES = new byte[0];

    /**
     * 有可能直接返回底层的bytes存储
     *
     * @return
     */
    byte[] toByteArray();

    default InputStream toInputStream() {
        return new ByteArrayInputStream(toByteArray());
    }
}