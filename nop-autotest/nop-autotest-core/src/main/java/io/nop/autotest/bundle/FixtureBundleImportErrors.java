package io.nop.autotest.bundle;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes for the fixture bundle import side (M1.2). Kept in a dedicated file per
 * the M1.2 approval freeze (no modification of M1.1's FixtureBundleErrors).
 */
public interface FixtureBundleImportErrors {
    String ARG_BUNDLE_NAME = "bundleName";
    String ARG_TABLE_NAME = "tableName";
    String ARG_COLUMN_NAME = "columnName";
    String ARG_ID = "id";

    ErrorCode ERR_FIXTURE_BUNDLE_REQUIRES_MISSING = define("nop.err.fixture-bundle.requires-missing",
            "bundle 依赖缺失：requires 声明的 bundle[{bundleName}]在目标环境不可发现", ARG_BUNDLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_SCHEMA_DRIFT = define("nop.err.fixture-bundle.schema-drift",
            "bundle 表[{tableName}]列指纹与目标环境实体模型不一致（漂移校验 fail-fast）", ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_SYS_TABLE_FORBIDDEN = define("nop.err.fixture-bundle.sys-table-forbidden",
            "bundle 含系统表/序列表[{tableName}]，不属可导入面", ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_LOGICAL_DELETED = define("nop.err.fixture-bundle.logical-deleted-declared",
            "bundle 表[{tableName}]声明包含逻辑删除行（includeLogicalDeleted=true），导入器永不重建已删行",
            ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_DANGLING_REF = define("nop.err.fixture-bundle.dangling-ref",
            "bundle 表[{tableName}]的引用列[{columnName}]值[{id}]既不在包内映射也不在目标库存在（悬空引用）",
            ARG_TABLE_NAME, ARG_COLUMN_NAME, ARG_ID);

    ErrorCode ERR_FIXTURE_BUNDLE_UK_CONFLICT = define("nop.err.fixture-bundle.uk-conflict",
            "bundle 表[{tableName}]业务键唯一冲突：目标环境存在对账窗口外的同键行", ARG_TABLE_NAME);

    ErrorCode ERR_FIXTURE_BUNDLE_COMPOSITE_PK_PAYLOAD = define("nop.err.fixture-bundle.composite-pk-payload",
            "bundle 表[{tableName}]为复合主键 payload 表：剥 PK 重映射依赖平台单列主键生成语义，不支持导入",
            ARG_TABLE_NAME);
}
