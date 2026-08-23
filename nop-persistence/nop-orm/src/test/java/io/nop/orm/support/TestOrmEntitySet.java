package io.nop.orm.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICloneable;
import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.OrmErrors;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * 从未加载的proxy集合执行orm_reset后必须保持proxy语义，不允许被固化为"空已加载集合"
     */
    @Test
    public void testOrmResetKeepsProxy() {
        SimsCollege owner = new SimsCollege();
        OrmEntitySet<SimsClass> set = new OrmEntitySet<>(owner, "simsClasses", null, null, SimsClass.class);
        set.orm_proxy(true);
        assertTrue(set.orm_proxy());

        set.orm_reset();
        // 修复前：orm_clearDirty把initialEntities固化为空集合，orm_proxy()从此返回false
        assertTrue(set.orm_proxy(), "unloaded proxy set must stay proxy after orm_reset");
    }

    /**
     * 集合带未提交修改时切换租户访问，必须抛出错误码体系内的OrmException而不是bare IllegalStateException
     */
    @Test
    public void testDirtyEntitySetChangeTenantThrowsOrmException() {
        SimsCollege owner = new SimsCollege();
        OrmEntitySet<SimsClass> set = new OrmEntitySet<>(owner, "simsClasses", null, null, SimsClass.class);
        set.orm_tenantId("otherTenant");
        set.orm_markDirty();

        NopException err = assertThrows(NopException.class, set::size);
        assertEquals(OrmErrors.ERR_ORM_DIRTY_ENTITY_SET_NOT_ALLOW_CHANGE_TENANT.getErrorCode(), err.getErrorCode());
    }

    /**
     * getEntityChange返回[[propId, oldValue, newValue]]
     */
    @Test
    public void testGetEntityChange() {
        SimsClass entity = new SimsClass();
        entity.setClassName("a");
        // 第一次赋值会把null记录为oldValue，这里清除脏标记后重新赋值，oldValue应为"a"
        entity.orm_clearDirty();
        entity.setClassName("b");

        List<List<Object>> changes = OrmEntityHelper.getEntityChange(entity);
        assertEquals(1, changes.size());
        List<Object> change = changes.get(0);
        assertEquals(SimsClass.PROP_ID_className, change.get(0));
        assertEquals("a", change.get(1));
        assertEquals("b", change.get(2));
    }
}
