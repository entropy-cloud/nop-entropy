/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
