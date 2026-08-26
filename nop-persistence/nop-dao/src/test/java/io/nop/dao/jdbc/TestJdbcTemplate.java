/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc;

import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.cache.MapCache;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.QuerySpaceEnv;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJdbcTemplate extends JdbcTestCase {
    @Test
    public void testFindFirst() {
        Object value = jdbc().findFirst(new SQL("select a from my_entity order by id"));
        assertEquals("3", value.toString());
    }

    @Test
    public void testFindAll() {
        List<Map<String, Object>> list = jdbc().findAll(new SQL("select a, b, c from my_entity order by id"));
        System.out.println(list);
        assertEquals("CC", list.get(0).get("C"));
    }

    @Test
    public void testFindPage() {
        List<Map<String, Object>> list = jdbc().findPage(new SQL("select a, b, c from my_entity order by id"), 0, 1);
        System.out.println(list);
        assertEquals("CC", list.get(0).get("C"));

        list = jdbc().findPage(new SQL("select a, b, c from my_entity order by id"), 1, 1);
        assertEquals(0, list.size());
    }

    @Test
    public void testExists() {
        assertTrue(jdbc().exists(new SQL("select * from my_entity")));
    }

    @Test
    public void testFindLong() {
        assertEquals(1, jdbc().findLong(new SQL("select count(*) from my_entity"), 0L));
    }

    @Test
    public void testExecuteStatement() {
        SQL sql = new SQL("update my_entity set a=3");
        long ret = jdbc().executeStatement(sql, null, ds -> {
            return ds.getUpdateCount();
        }, null);
        assertEquals(1, ret);

        String id = jdbc().executeStatement(new SQL("select id from my_entity"), null, ds -> {
            return ds.getResultSet().next().getString(0);
        }, null);
        assertEquals("1", id);
    }

    /**
     * cacheProvider已配置但SQL没有设置cacheRef时（绝大多数查询），executeQuery不应崩溃
     */
    @Test
    public void testFindAllWithoutCacheRefWhenCacheProviderConfigured() {
        JdbcTemplateImpl template = (JdbcTemplateImpl) jdbc();
        template.setCacheProvider(new SimpleCacheProvider());

        List<Map<String, Object>> list = template.findAll(new SQL("select a, b, c from my_entity order by id"));
        assertEquals(1, list.size());
        assertEquals("CC", list.get(0).get("C"));
    }

    /**
     * 动态querySpace启用时，方言应跟随映射后的querySpace解析，而不是SQL原始的缺省querySpace
     */
    @Test
    public void testGetDialectForQuerySpaceFollowsDynamicQuerySpace() {
        JdbcTemplateImpl template = new JdbcTemplateImpl();
        List<String> asked = new ArrayList<>();
        template.setDialectProvider(querySpace -> {
            asked.add(querySpace);
            return null;
        });

        QuerySpaceEnv.runWithQuerySpace("slave", () -> {
            template.getDialectForQuerySpace(null);
            template.getDialectForQuerySpace("");
            return null;
        });
        // 缺省querySpace被映射到QuerySpaceEnv指定的slave
        assertEquals(List.of("slave", "slave"), asked);

        // 显式指定的非缺省querySpace不参与映射
        asked.clear();
        template.getDialectForQuerySpace("explicit");
        assertEquals(List.of("explicit"), asked);
    }

    /**
     * exists模板探测只应执行一次exists查询，不再先findAll物化整个结果集
     */
    @Test
    public void testExistsTemplateSingleExecution() throws Exception {
        CountingTemplate template = new CountingTemplate();
        template.setTransactionTemplate(txn());

        Method m = JdbcTemplateImpl.class.getDeclaredMethod("tryExistsByTemplate",
                String.class, String.class, String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        try {
            Boolean ret = (Boolean) m.invoke(template, null,
                    "select 1 from my_entity where {tableName} is not null", "PUBLIC", "my_entity", null, null);
            assertTrue(ret);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }

        assertEquals(0, template.findAllCalls);
        assertEquals(1, template.existsCalls);
    }

    /**
     * check2 P3：getTableMeta 的表名必须经 dialect 转义（与 existsTable 对齐）——
     * 含空格/保留字的表名不转义会直接拼出非法 SQL。
     */
    @Test
    public void testGetTableMetaEscapesTableName() {
        jdbc().executeUpdate(new SQL("create table \"MY ENTITY\"(id int primary key)"));
        io.nop.dataset.IDataSetMeta meta = jdbc().getTableMeta(null, "MY ENTITY");
        assertEquals(1, meta.getFieldCount());
        assertEquals("ID", meta.getFieldName(0).toUpperCase());
    }

    /**
     * check2 P3：callFunc 返回第一个 OUT 参数的值（函数返回值），而非更新计数。
     */
    @Test
    public void testCallFuncReturnsOutParamValue() {
        jdbc().executeUpdate(new SQL(
                "CREATE ALIAS T_CALL_FUNC AS 'String tCallFunc() { return \"42\"; }'"));
        Object ret = jdbc().callFunc(new SQL("{? = call T_CALL_FUNC()}"));
        assertEquals("42", ret);
    }

    static class SimpleCacheProvider implements ICacheProvider {
        @Override
        public <K, V> ICache<K, V> getCache(String name) {
            return new MapCache<>(name, true);
        }

        @Override
        public void clearAllCache() {
        }
    }

    static class CountingTemplate extends JdbcTemplateImpl {
        int findAllCalls;
        int existsCalls;

        @Override
        public List<Object> findAll(SQL sql) {
            findAllCalls++;
            return super.findAll(sql);
        }

        @Override
        public boolean exists(SQL sql) {
            existsCalls++;
            return super.exists(sql);
        }
    }
}
