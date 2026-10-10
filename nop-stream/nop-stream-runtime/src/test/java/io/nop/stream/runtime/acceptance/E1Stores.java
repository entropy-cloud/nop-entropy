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
import java.util.Map;

/** Shared sink stores for the E1 state-machine scenario. */
public final class E1Stores {
    public static final List<Map<String, String>> MAIN =
            java.util.Collections.synchronizedList(new ArrayList<>());
    public static final List<Map<String, String>> ALERT =
            java.util.Collections.synchronizedList(new ArrayList<>());
    public static final List<String> JOURNAL =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private E1Stores() {
    }
}
