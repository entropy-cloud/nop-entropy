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
