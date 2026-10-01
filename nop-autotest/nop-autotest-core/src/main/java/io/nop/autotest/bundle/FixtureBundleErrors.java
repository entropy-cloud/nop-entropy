package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the fixture bundle export-side framework (M1.1).
 */
public interface FixtureBundleErrors {
    String ARG_TABLE_NAME = "tableName";
    String ARG_ERRORS = "errors";
    String ARG_PATH = "path";

    ErrorCode ERR_FIXTURE_BUNDLE_SESSION_BUSY = define("nop.err.fixture-bundle.export-session-busy",
            "导出会话独占冲突：同 JVM 已有导出会话在运行");

    ErrorCode ERR_FIXTURE_BUNDLE_VALIDATION_FAIL = define("nop.err.fixture-bundle.validation-fail",
            "fixture bundle 校验失败：{errors}", ARG_ERRORS);

    ErrorCode ERR_FIXTURE_BUNDLE_TABLE_NOT_CONFIGURED = define("nop.err.fixture-bundle.table-not-configured",
            "导出会话收集到未配置导出的表[{tableName}]：请在导出配置中声明其层归属", ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_BASE_KEYS_REQUIRED = define("nop.err.fixture-bundle.base-keys-required",
            "base 层表[{tableName}]必须声明业务键列（导入对账依据）", ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_SENSITIVE_COLUMN = define("nop.err.fixture-bundle.sensitive-column-unmasked",
            "表[{tableName}]的敏感列[{columnName}]未标记 masked，拒绝导出", ARG_TABLE_NAME, "columnName");
}
