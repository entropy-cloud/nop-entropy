/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.match;

public interface MatchConstants {
    char PATTERN_PREFIX = '@';

    String KEY_PREFIX = "@prefix";
    String MATCH_ALL_PATTERN = "*";

    String NAME_PATTERN = "pattern";

    String NAME_IF = "if";
    String NAME_SWITCH = "switch";
    String NAME_OR = "or";
    String NAME_AND = "and";

    String NAME_EXPR = "expr";
    String NAME_CHECK = "check";

    String NAME_VAR = "var";

    String VAR_MATCH_STATE = "matchState";
}
