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

/** Shared sink store for the E4 window-join scenario. */
public final class E4Stores {
    public static final List<Object> PAIRS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private E4Stores() {
    }
}
