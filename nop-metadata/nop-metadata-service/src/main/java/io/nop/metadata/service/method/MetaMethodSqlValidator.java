package io.nop.metadata.service.method;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;

import java.util.List;
import java.util.Locale;

/**
 * 方法 SQL 沙箱（设计 14 §3.5，安全核心；plan 2026-10-26-2047-1 Phase 3）。
 *
 * <p><b>双侧同源</b>：save-time（{@link #validateSaveTime}，拒绝码
 * {@code method-impl-invalid}）与 query-time（{@link #validateQueryTime}，拒绝码
 * {@code method-sql-unsafe}，defense-in-depth 兜底校验器水位变化后的存量行）调用
 * 同一校验逻辑。
 *
 * <p><b>水位</b>不低于 custom_sql 现状：单语句（`;` 拒绝）、MySQL 可执行注释（{@code /*!}
 * 拒绝）、SELECT-only（首 token 白名单）、token 黑名单与多 token 危险序列
 * <b>直接复用</b> {@link MetaQualityRuleExecutor} 的 custom_sql 集合（同一语义域：
 * 整条语句文本 + token 精确匹配 + fail-closed——F9 DRY 裁定的同域共享面；字面量内含
 * 黑名单 token 同样被拒，宁可误拒不放过）。
 *
 * <p>参数只经 {@code ?} 占位符绑定（{@link #countPlaceholders} 跳过字符串字面量内
 * 的 {@code ?}），执行侧校验占位符数与声明参数数一致。
 */
public class MetaMethodSqlValidator {

    private MetaMethodSqlValidator() {
    }

    /** save-time 校验（Phase 1 非空校验的回接点位）。 */
    public static void validateSaveTime(String implSource, String methodName) {
        validate(implSource, methodName, NopMetadataErrors.ERR_METHOD_IMPL_INVALID);
    }

    /** query-time 重新校验（defense-in-depth，兜底存量行）。 */
    public static void validateQueryTime(String implSource, String methodName) {
        validate(implSource, methodName, NopMetadataErrors.ERR_METHOD_SQL_UNSAFE);
    }

    private static void validate(String implSource, String methodName, ErrorCode code) {
        if (implSource == null || implSource.trim().isEmpty()) {
            throw new NopMetadataException(code).param(NopMetadataArgs.ARG_METHOD_NAME, methodName);
        }
        String upper = implSource.trim().toUpperCase(Locale.ROOT);
        if (upper.contains(";")) {
            throw sandboxReject(code, methodName, "multi-statement");
        }
        if (upper.contains("/*!")) {
            throw sandboxReject(code, methodName, "executable-comment");
        }
        List<String> tokens = MetaQualityRuleExecutor.tokenizeSqlForSandbox(upper);
        if (tokens.isEmpty() || !"SELECT".equals(tokens.get(0))) {
            throw sandboxReject(code, methodName, "select-only");
        }
        for (String word : MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_WORDS) {
            if (tokens.contains(word)) {
                throw sandboxReject(code, methodName, "forbidden-keyword");
            }
        }
        for (String[] sequence : MetaQualityRuleExecutor.CUSTOM_SQL_FORBIDDEN_SEQUENCES) {
            if (MetaQualityRuleExecutor.containsTokenSequence(tokens, sequence)) {
                throw sandboxReject(code, methodName, "forbidden-keyword");
            }
        }
    }

    private static NopMetadataException sandboxReject(ErrorCode code, String methodName, String category) {
        // 错误面不泄漏 SQL 全文：只带方法身份与类别
        NopMetadataException ex = new NopMetadataException(code);
        ex.param(NopMetadataArgs.ARG_METHOD_NAME, methodName);
        ex.param(NopMetadataArgs.ARG_CATEGORY, category);
        return ex;
    }

    /**
     * 统计 {@code ?} 占位符数（跳过单引号字符串字面量内的 {@code ?}，'' 转义识别）。
     * 双引号标识符内的 {@code ?} 在合法标识符中不出现，按占位符计入与 custom_sql
     * 分词的 fail-closed 哲学一致（宁可误计不漏计）。
     */
    public static int countPlaceholders(String implSource) {
        if (implSource == null) {
            return 0;
        }
        int count = 0;
        boolean inLiteral = false;
        for (int i = 0; i < implSource.length(); i++) {
            char c = implSource.charAt(i);
            if (inLiteral) {
                if (c == '\'') {
                    if (i + 1 < implSource.length() && implSource.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inLiteral = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inLiteral = true;
            } else if (c == '?') {
                count++;
            }
        }
        return count;
    }
}
