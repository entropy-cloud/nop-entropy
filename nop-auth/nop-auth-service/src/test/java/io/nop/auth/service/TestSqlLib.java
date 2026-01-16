package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.sql_lib.SqlLibManager;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
@NopTestProperty(name = "nop.auth.data-auth-config-path", value = "/nop/auth/auth/nop-auth.data-auth.xml")
public class TestSqlLib extends JunitBaseTestCase {

    @Inject
    SqlLibManager sqlLibManager;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testSqlLib() {
        ContextProvider.runWithTenant("444", () -> {
            UserContextImpl user = new UserContextImpl();
            user.setUserId("1");
            user.setUserName("test");

            IUserContext.set(user);

            IEvalScope scope = XLang.newEvalScope();
            scope.setLocalValue("name", "aaa");
            sqlLibManager.invoke("test.findFirstByName", null, scope);
            return null;
        });
    }

    @Test
    public void testFetchParent() {
        IEvalScope scope = XLang.newEvalScope();
        ormTemplate.runInSession(() -> {
            sqlLibManager.invoke("test.findParent", null, scope);
        });
    }
}
