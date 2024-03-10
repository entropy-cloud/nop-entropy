/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

public interface IOrmRevisionSupport {
    Byte getNopRevType();

    void setNopRevType(Byte value);

    Long getNopRevBeginVer();

    void setNopRevBeginVer(Long value);

    Long getNopRevEndVer();

    void setNopRevEndVer(Long value);

    Boolean getNopRevChildChange();

    void setNopRevChildChange(Boolean b);
}
