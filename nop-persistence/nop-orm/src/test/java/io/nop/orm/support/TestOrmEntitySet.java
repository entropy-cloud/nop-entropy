package io.nop.orm.support;

import io.nop.api.core.util.ICloneable;
import io.nop.app.SimsCollege;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestOrmEntitySet {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testEL() {
        SimsCollege entity = new SimsCollege();
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("entity", entity);

        String expr = "entity.simsClasses.map(i=>i.id)";
        IEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true).compileSimpleExpr(null, expr);
        List<?> list = (List<?>) action.invoke(scope);
        Assertions.assertEquals(0, list.size());
    }

    interface IMyBaseEntity {

    }

    interface IMyEntity extends ICloneable {

    }

    interface IMyEntity2 extends IMyBaseEntity, IMyEntity {

    }

    static class MyBaseEntity implements IMyBaseEntity {

    }

    static class MyEntity extends MyBaseEntity implements IMyEntity {
        @Override
        public Object cloneInstance() {
            return this;
        }
    }

    @Test
    public void testReflection() {
        Class<?>[] interfaces = MyEntity.class.getInterfaces();
        assertEquals(1, interfaces.length);

        interfaces = IMyEntity2.class.getInterfaces();
        assertEquals(2, interfaces.length);
    }
}
