/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.diff;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DiffErrors {
    String ARG_LINE = "line";
    String ARG_LINE_NUM = "lineNum";
    String ARG_OLD_LINE = "oldLine";
    String ARG_NEW_LINE = "newLine";
    String ARG_PATH = "path";
    String ARG_OLD_PATH = "oldPath";
    String ARG_NEW_PATH = "newPath";
    String ARG_REASON = "reason";
    String ARG_EXPECTED = "expected";
    String ARG_ACTUAL = "actual";

    ErrorCode ERR_DIFF_PARSE_INVALID_HEADER = define(
            "nop.err.diff.parse.invalid-header",
            "无效的diff头部行:{line}",
            ARG_LINE);

    ErrorCode ERR_DIFF_PARSE_INVALID_HUNK_HEADER = define(
            "nop.err.diff.parse.invalid-hunk-header",
            "无效的hunk头部行:{line}",
            ARG_LINE);

    ErrorCode ERR_DIFF_PARSE_MISSING_HUNK_HEADER = define(
            "nop.err.diff.parse.missing-hunk-header",
            "缺少hunk头部，期望@@开始");

    ErrorCode ERR_DIFF_APPLY_CONTEXT_MISMATCH = define(
            "nop.err.diff.apply.context-mismatch",
            "上下文行不匹配: 行{lineNum}期望[{expected}]实际[{actual}]",
            ARG_LINE_NUM, ARG_EXPECTED, ARG_ACTUAL);

    ErrorCode ERR_DIFF_APPLY_OLD_LINE_MISMATCH = define(
            "nop.err.diff.apply.old-line-mismatch",
            "旧文件行不匹配: 行{oldLine}期望[{expected}]实际[{actual}]",
            ARG_OLD_LINE, ARG_EXPECTED, ARG_ACTUAL);

    ErrorCode ERR_DIFF_APPLY_LINE_OUT_OF_RANGE = define(
            "nop.err.diff.apply.line-out-of-range",
            "行号超出范围: {oldLine}",
            ARG_OLD_LINE);

    ErrorCode ERR_DIFF_APPLY_FAILED = define(
            "nop.err.diff.apply.failed",
            "应用diff失败: {reason}",
            ARG_REASON);

    ErrorCode ERR_DIFF_APPLY_CONFLICT = define(
            "nop.err.diff.apply.conflict",
            "diff冲突: 在{oldPath}的行{oldLine}",
            ARG_OLD_PATH, ARG_OLD_LINE);
}
