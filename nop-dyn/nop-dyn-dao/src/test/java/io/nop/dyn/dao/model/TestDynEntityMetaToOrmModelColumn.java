/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.dao.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.api.DaoProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.orm.exceptions.OrmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_INVALID_DEFAULT_VALUE;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_INVALID_PROP_NAME;
import static io.nop.dyn.dao.NopDynDaoErrors.ERR_DYN_INVALID_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 校验动态表名/属性名/缺省值进入DDL生成链前的格式校验（未填precision的缺省精度在service集成测试中覆盖）
 */
public class TestDynEntityMetaToOrmModelColumn {

    @BeforeEach
    public void setUp() {
        // 构造函数仅通过DaoProvider获取NopDynEntity的实体模型，toColumnModel/transformEntityModel的校验路径不依赖它
        DaoProvider.registerInstance((IDaoProvider) Proxy.newProxyInstance(
                IDaoProvider.class.getClassLoader(), new Class[]{IDaoProvider.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("daoFor"))
                        return newProxy(io.nop.orm.dao.IOrmEntityDao.class);
                    return defaultValue(method.getReturnType());
                }));
    }

    @AfterEach
    public void tearDown() {
        DaoProvider.registerInstance(null);
    }

    @SuppressWarnings("unchecked")
    static <T> T newProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        if (type == float.class)
            return 0F;
        if (type == short.class)
            return (short) 0;
        if (type == byte.class)
            return (byte) 0;
        if (type == char.class)
            return (char) 0;
        return null;
    }

    static NopDynPropMeta newProp(String propName, StdSqlType sqlType, Integer precision) {
        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.MyEntity");

        NopDynPropMeta propMeta = new NopDynPropMeta();
        propMeta.setPropId(1);
        propMeta.setPropName(propName);
        propMeta.setStdSqlType(sqlType.getName());
        propMeta.setPrecision(precision);
        propMeta.setEntityMeta(entityMeta);
        return propMeta;
    }

    @Test
    public void testInvalidTableNameRejected() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynEntityMeta entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName("test.BadTable");
        entityMeta.setTableName("bad table; drop table x");
        NopException ex = assertThrows(NopException.class,
                () -> transformer.transformEntityModel(entityMeta));
        assertEquals(ERR_DYN_INVALID_TABLE_NAME.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testInvalidPropNameRejected() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopException ex = assertThrows(NopException.class,
                () -> transformer.toColumnModel(newProp("a;b", StdSqlType.VARCHAR, 10)));
        assertEquals(ERR_DYN_INVALID_PROP_NAME.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testInjectedDefaultValueRejected() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynPropMeta propMeta = newProp("status", StdSqlType.VARCHAR, 10);
        propMeta.setDefaultValue("0; DROP TABLE x");
        NopException ex = assertThrows(NopException.class,
                () -> transformer.toColumnModel(propMeta));
        assertEquals(ERR_DYN_INVALID_DEFAULT_VALUE.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testQuotedStringDefaultValueAllowed() {
        DynEntityMetaToOrmModel transformer = new DynEntityMetaToOrmModel(false);
        NopDynPropMeta propMeta = newProp("status", StdSqlType.VARCHAR, 10);
        propMeta.setDefaultValue("'active'");
        // 合法带引号缺省值必须通过校验并完成转换。回归覆盖 wi7#1（plan 2306 项 35）：
        // 修复前 detached 实体在 getDomain 懒加载处抛 OrmException，本断言只能以
        // "抛的不是缺省值错误" 的间接方式证明校验放行；修复后 detached ref 按"未设置"
        // 处理（domain=null 合法分支），toColumnModel 对未挂域的 detached propMeta 完整可用
        io.nop.orm.model.OrmColumnModel col = transformer.toColumnModel(propMeta);
        org.junit.jupiter.api.Assertions.assertNotNull(col);
        org.junit.jupiter.api.Assertions.assertEquals("status", col.getName());
        org.junit.jupiter.api.Assertions.assertEquals("'active'", col.getDefaultValue(),
                "合法带引号缺省值必须原样保留");
    }
}
