/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package test.io.entropy.beans;

import io.nop.api.core.annotations.core.NoReflection;

public class MyBaseBean {
    public Object getListC() {
        return null;
    }

    @NoReflection
    public Object secretMethodA() {
        return null;
    }

    @NoReflection
    public Object secretMethodB() {
        return null;
    }
}
