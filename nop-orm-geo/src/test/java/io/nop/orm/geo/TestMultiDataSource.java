/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.sql_lib.ISqlLibManager;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@NopTestConfig(localDb = true, initDatabaseSchema = true,beanContainerStartMode = BeanContainerStartMode.DEFAULT)
public class TestMultiDataSource extends JunitBaseTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    ISqlLibManager sqlLibManager;

    @Test
    public void testSqlQuerySpace() {
        IEvalScope scope = XLang.newEvalScope();
        Map<String, Object> args = new HashMap<>();
        sqlLibManager.invoke("test.getAllLocations", null, args, scope);
    }
}
