/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AutoTestErrors {
    String ARG_FILE_NAME = "fileName";
    String ARG_FILE_PATH = "filePath";

    String ARG_ERROR_NAME = "errorName";

    String ARG_TABLE_NAME = "tableName";

    String ARG_FILE = "file";

    String ARG_ROW_NUMBER = "rowNumber";
    String ARG_ID = "id";

    String ARG_SQL = "sql";
    String ARG_SQL_RESULT = "sqlResult";

    String ARG_TEST_CLASS = "testClass";
    String ARG_TEST_METHOD = "testMethod";

    String ARG_EXPECTED = "expected";
    String ARG_OUTPUT = "output";

    String ARG_ERRORS = "errors";

    ErrorCode ERR_AUTOTEST_UNKNOWN_FILE = define("nop.err.autotest.unknown-file", "文件[{filePath}]不存在", ARG_FILE_NAME,
            ARG_FILE_PATH);

    ErrorCode ERR_AUTOTEST_EXPECT_ERROR = define("nop.err.autotest.expect-error", "代码应该抛出异常，不应该执行到这里");

    ErrorCode ERR_AUTOTEST_CHECK_MATCH_FAIL = define("nop.err.autotest.check-match-fail", "执行结果不符合预期", ARG_ERRORS);

    ErrorCode ERR_AUTOTEST_CHECK_OUTPUT_FAIL = define("nop.err.autotest.check-output-fail",
            "输出结果[{fileName}]与录制的文件不匹配");

    ErrorCode ERR_AUTOTEST_NOT_EXPECTED_DATA = define("nop.err.autotest.not-expect-data",
            "输出[{fileName}]不是期望的结果:expected={},output={}", ARG_FILE_NAME, ARG_EXPECTED, ARG_OUTPUT);

    ErrorCode ERR_AUTOTEST_NO_DAO_FOR_TABLE = define("nop.err.autotest.no-dao-for-table",
            "没有找到数据库表[{tableName}]所对应的DAO对象", ARG_TABLE_NAME);

    ErrorCode ERR_AUTOTEST_ROW_NO_ID = define("nop.err.autotest.row-no-id", "文件[{file}]中的数据行[{rowNumber}]没有id",
            ARG_FILE, ARG_ROW_NUMBER);

    ErrorCode ERR_AUTOTEST_CHECK_DELETED_ROW_FAIL = define("nop.err.autotest.check-deleted-row-fail",
            "数据校验失败：数据表[{tableName}]的id为[{id}]的记录没有被删除", ARG_TABLE_NAME, ARG_ID);

    ErrorCode ERR_AUTOTEST_OUTPUT_ROW_NOT_EXISTS = define("nop.err.autotest.output-row-not-exists",
            "数据校验失败：数据表[{tableName}]的id为[{id}]的记录不存在", ARG_TABLE_NAME, ARG_ID);

    ErrorCode ERR_AUTOTEST_SNAPSHOT_FINISHED = define("nop.err.autotest.snapshot-finished",
            "录制快照过程正常结束. 现在可以通过@EnableSnapshot来启用录制的快照数据来实现重放测试");

    ErrorCode ERR_AUTOTEST_CHECK_SQL_RESULT_FAIL = define("nop.err.autotest.check-sql-result-fail", "执行SQL验证失败：{sql}",
            ARG_SQL);

    ErrorCode ERR_AUTOTEST_TEST_CLASS_NO_LOCAL_DB_ANNOTATION = define(
            "nop.err.autotest.test-class-no-local-db-annotation",
            "测试方法[{testMethod}]要求使用localDb模式，因此在测试类[{testClass}]上必须增加@NopTestConfig(localDb=true)配置", ARG_TEST_METHOD,
            ARG_TEST_CLASS);
}
