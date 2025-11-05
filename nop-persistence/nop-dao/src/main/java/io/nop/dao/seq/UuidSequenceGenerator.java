/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.seq;

import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

public class UuidSequenceGenerator implements ISequenceGenerator {
    public static final UuidSequenceGenerator INSTANCE = new UuidSequenceGenerator();

    @Override
    public long generateLong(String seqName, boolean useDefault) {
        return MathHelper.randomPositiveLong();
    }

    @Override
    public String generateString(String seqName, boolean useDefault) {
        return StringHelper.generateUUID();
    }
}
