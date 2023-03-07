/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent.impl;

import io.nop.commons.concurrent.IAtomicLong;

import java.util.concurrent.atomic.AtomicLong;

public class LocalAtomicLong extends AtomicLong implements IAtomicLong {
    private static final long serialVersionUID = -7213560469754311404L;
}