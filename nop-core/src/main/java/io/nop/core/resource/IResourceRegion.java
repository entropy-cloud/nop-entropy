/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.util.progress.IStepProgressListener;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 对应某个资源文件的一个区间。为了便于{@link io.nop.core.resource.impl.DelegateResource}直接返回底层资源的实现，避免额外包装，
 * IResourceRegion类不提供getResource()方法，摆脱特定Resource的绑定关系。
 */
public interface IResourceRegion {

    LongRangeBean getRange();

    default void writeToStream(OutputStream os) {
        writeToStream(os, null);
    }

    void writeToStream(OutputStream os, IStepProgressListener listener);

    InputStream getInputStream();
}