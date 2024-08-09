/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core;

public interface AutoTestConstants {
    String TAG_VAR = "var";
    String TAG_SEQ = "seq";
    String TAG_CLOCK = "clock";

    String V_VAR_PREFIX = "v_";

    String VAR_PREFIX = "@var:";
    char JSON_PATTERN_PREFIX = '@';

    String VARIANT_DEFAULT = "_default";

    char PATTERN_PREFIX = '@';

    String INCLUDE_DIRECTIVE_PREFIX = "@include:";

    String CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP = "nop.graphql.ignore-millis-in-timestamp";
}
