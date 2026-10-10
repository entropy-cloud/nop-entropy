/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.acceptance;

import java.util.ArrayList;
import java.util.List;

/** Shared emission store for the E2 top-speed scenario. */
public final class E2Stores {
    public static final List<Object> EMISSIONS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private E2Stores() {
    }
}
