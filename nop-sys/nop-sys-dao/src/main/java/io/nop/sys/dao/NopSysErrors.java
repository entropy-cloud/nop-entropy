/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopSysErrors {
    String ARG_SEQ_NAME = "seqName";
    String ARG_COUNT = "count";
    String ARG_PATTERN = "pattern";
    String ARG_PREFIX = "prefix";
    String ARG_RULE_NAME = "ruleName";

    ErrorCode ERR_SYS_NO_SEQ =
            define("nop.err.sys.no-seq",
                    "在序列号表中缺少对应记录:{seqName}", ARG_SEQ_NAME);

    ErrorCode ERR_SYS_CHAR_COUNT_EXCEED_LIMIT =
            define("nop.err.sys.count-exceed-limit",
                    "字符数量超过限制:{count}", ARG_COUNT);

    ErrorCode ERR_SYS_UNKNOWN_PREFIX_IN_CODE_RULE_PATTERN =
            define("nop.err.sys.unknown-prefix-in-code-rule-pattern",
                    "编码规则[{pattern}]中使用的前缀[{prefix}]未定义", ARG_PATTERN, ARG_PREFIX);

    ErrorCode ERR_SYS_UNKNOWN_CODE_RULE =
            define("nop.err.sys.unknown-code-rule", "未定义的编码规则:{ruleName}", ARG_RULE_NAME);

    ErrorCode ERR_SYS_CODE_RULE_EMPTY_SEQ_NAME =
            define("nop.err.sys.code-rule-empty-seq-name",
                    "编码规则中用到了顺序号，但是没有配置对应序列名称：{ruleName}", ARG_RULE_NAME);
}
