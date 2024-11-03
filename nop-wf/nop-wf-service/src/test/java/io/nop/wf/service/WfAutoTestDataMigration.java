/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service;

import io.nop.autotest.core.migration.CaseDataMigration;
import io.nop.commons.util.StringHelper;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 迁移测试数据到新的数据结构
 */
@Disabled
public class WfAutoTestDataMigration extends BaseTestCase {
    @Test
    public void run() {
        CaseDataMigration m = new CaseDataMigration();
        m.forTable("nop_wf_step_instance")
                .renameCol("STEP_GROUP","EXEC_GROUP")
                .transformCol("IS_READ",value->{
                  if(StringHelper.isEmptyObject(value))
                      return "false";
                  return value;
                })
                .transformCol("EXEC_GROUP",value->{
                    return StringHelper.replace(value.toString(),"@stepGroup","@execGroup");
                })
                .transformCol("EXEC_ORDER", value -> {
                    if ("0.0".equals(value))
                        return "0";
                    value = StringHelper.removeTail(value.toString(),".0");
                    if(value.equals("1"))
                        return "1000";
                    return value;
                });

        m.runForAllCases(getCasesDir());
    }
}
