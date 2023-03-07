/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.autotest.core.migration.CaseDataMigration;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 迁移测试数据到新的数据结构
 */
@Disabled
public class AutoTestDataMigration extends BaseTestCase {
    @Test
    public void run() {
        CaseDataMigration m = new CaseDataMigration();
        m.forTable("nop_auth_user")
                .renameCol("SID", "USER_ID")
                .renameCol("PASS_UPDATE_TIME", "PWD_UPDATE_TIME")
                .renameCol("CHANGE_PASSWORD", "CHANGE_PWD_AT_LOGIN")
                .renameCol("CHANGE_PASS_AT_LOGIN", "CHANGE_PWD_AT_LOGIN")
                .transformCol("DEL_FLAG", value -> {
                    if ("true".equals(value))
                        return "1";
                    if ("false".equals(value))
                        return "0";
                    return value;
                });
        m.forTable("nop_auth_session")
                .renameCol("SID", "SESSION_ID")
                .deleteCol("LOGIN_STATUS")
                .deleteCol("UPDATED_BY")
                .deleteCol("UPDATE_TIME");

        m.forTable("nop_auth_position")
                .renameCol("SID", "POSITION_ID");

        m.forTable("nop_auth_op_log")
                .renameCol("SID", "LOG_ID");

        m.runForAllCases(getCasesDir());
    }
}
