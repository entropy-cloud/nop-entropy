/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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
