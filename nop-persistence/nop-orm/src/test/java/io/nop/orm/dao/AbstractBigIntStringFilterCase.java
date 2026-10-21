/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.dao;

import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmEntity;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.beans.FilterBeans.and;
import static io.nop.api.core.beans.FilterBeans.between;
import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.ge;
import static io.nop.api.core.beans.FilterBeans.gt;
import static io.nop.api.core.beans.FilterBeans.in;
import static io.nop.api.core.beans.FilterBeans.isNull;
import static io.nop.api.core.beans.FilterBeans.le;
import static io.nop.api.core.beans.FilterBeans.like;
import static io.nop.api.core.beans.FilterBeans.lt;
import static io.nop.api.core.beans.FilterBeans.ne;
import static io.nop.api.core.beans.FilterBeans.notIn;
import static io.nop.api.core.beans.FilterBeans.or;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方案 B（BIGINT 列 + stdDataType=string）过滤参数绑定回归。
 *
 * <p>缺陷面：QueryBean/EQL/DataAuth 过滤参数只按列声明的 Java stdDataType 绑定。
 * 对 BIGINT+string 列，参数以 String 发送；PostgreSQL 无 bigint = varchar 算子，
 * 报 42883。本用例的核心断言（{@code testXxxParamBindsAsLong}）直接检查
 * {@link ICompiledSql#buildParams} 的产出类型：修复前是 String（缺陷），
 * 修复后是 Long（与列 binder 的 AutoConvert 语义对齐），方言无关、无 Docker 可跑。</p>
 *
 * <p>其余 E2E 用例走真实 OrmEntityDao → DaoQueryHelper → EQL 链，在缺省 H2 上
 * 兜底回归（H2 有隐式转换，修复前后都可执行）；PostgreSQL/MySQL 子类在
 * {@code -Dnop.test.docker.enabled=true} 时用 Testcontainers 复跑同一套用例。</p>
 */
public class AbstractBigIntStringFilterCase extends AbstractOrmTestCase {

    static final String ENTITY = "test.entity.TestBigIntStringEntity";
    static final String MODEL = "/nop/test/orm/bigint-string.orm.xml";

    @Override
    protected io.nop.orm.factory.IOrmModelProvider ormModelProvider() {
        OrmModel model = new OrmModelLoader().loadOrmModel(
                List.of(VirtualFileSystem.instance().getResource(MODEL)));
        return new StaticOrmModelProvider(model);
    }

    /** 显式注入已加载模型（形态参照 TestTripleComboShardReadwriteEnc） */
    static class StaticOrmModelProvider implements io.nop.orm.factory.IOrmModelProvider {
        final OrmModel model;
        private volatile ILoadedOrmModel loaded;

        StaticOrmModelProvider(OrmModel model) {
            this.model = model;
        }

        @Override
        public ILoadedOrmModel getOrmModel(io.nop.orm.persister.IPersistEnv env) {
            if (loaded == null)
                loaded = new LoadedOrmModel(env, model);
            return loaded;
        }

        @Override
        public void clearCache() {
            loaded = null;
        }

        @Override
        public void clearCacheForTenant(String tenantId) {
            loaded = null;
        }

        @Override
        public void close() {
        }
    }

    /** 独立模型不含共享模型的 SimsCollege/SimsClass，本测试自建数据 */
    @Override
    protected void prepareData() {
        saveRow("1", "10", "alpha");
        saveRow("2", "20", "beta");
        saveRow("3", "30", "gamma");
        saveRow("4", null, "delta");
    }

    /**
     * 基类 /init.sql 是 H2 方言建表脚本，MySQL/PostgreSQL 子类上会因语法差异失败。
     * 本用例只依赖 ORM 模型建出的 TEST_BIGINT_STRING，不需要那批共享表。
     */
    @Override
    protected void executeSqlFile(String sqlFile) {
    }

    /** TC_DAEMON 复用同一容器时，跨用例 setUp 需先落表再重建（表名与 DdlSqlCreator 产物同为小写） */
    @Override
    protected void createTables() {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .sql("drop table if exists test_bigint_string").end());
        super.createTables();
    }

    @Override
    protected void prepareDataInTenant(String tenantId) {
    }

    protected void saveRow(String id, String refId, String name) {
        IOrmEntity entity = orm().newEntity(ENTITY);
        entity.orm_propValueByName("id", id);
        entity.orm_propValueByName("refId", refId);
        entity.orm_propValueByName("name", name);
        orm().save(entity);
    }

    protected IEntityDao<IOrmEntity> dao() {
        return daoProvider().dao(ENTITY);
    }

    protected List<Object> buildParams(String eqlName, String eql, Object... inputs) {
        ICompiledSql compiled = sessionFactory.compileSql(eqlName, eql, false);
        return compiled.buildParams(Arrays.asList(inputs));
    }

    protected List<IOrmEntity> findAll(TreeBean filter) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setFilter(filter);
        return dao().findAllByQuery(query);
    }

    protected long count(TreeBean filter) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setFilter(filter);
        return dao().countByQuery(query);
    }

    protected String nameOf(IOrmEntity entity) {
        return (String) entity.orm_propValueByName("name");
    }

    // ==== 核心断言：过滤参数绑定类型（缺陷复现点，方言无关） ====

    /** eq 比较谓词：BIGINT+string 列的过滤参数必须按列 SQL 类型绑成 Long（修复前是 String） */
    @Test
    public void testEqParamBindsAsLong() {
        List<Object> params = buildParams("bs-eq",
                "select o from " + ENTITY + " as o where o.refId = ?", "20");
        assertEquals(1, params.size());
        assertEquals(20L, params.get(0),
                "BIGINT+string 列 eq 过滤参数应绑定为 Long，实际: " + typeOf(params.get(0)));
    }

    @Test
    public void testGtParamBindsAsLong() {
        List<Object> params = buildParams("bs-gt",
                "select o from " + ENTITY + " as o where o.refId > ?", "20");
        assertEquals(20L, params.get(0));
    }

    @Test
    public void testPkCompareParamBindsAsLong() {
        List<Object> params = buildParams("bs-pk-eq",
                "select o from " + ENTITY + " as o where o.id = ?", "1");
        assertEquals(1L, params.get(0));
    }

    /** IN 列表：按对照列的 SQL 类型逐个转换；非数值列保持 ANY 透传（本实体无此场景，见 like 用例） */
    @Test
    public void testInParamsBindAsLong() {
        List<Object> params = buildParams("bs-in",
                "select o from " + ENTITY + " as o where o.refId in (?,?)", "10", "30");
        assertEquals(List.of(10L, 30L), params);
    }

    @Test
    public void testBetweenParamsBindAsLong() {
        List<Object> params = buildParams("bs-between",
                "select o from " + ENTITY + " as o where o.refId between ? and ?", "10", "30");
        assertEquals(List.of(10L, 30L), params);
    }

    /** UPDATE SET：写路径过滤/赋值同属定型面 */
    @Test
    public void testUpdateSetParamBindsAsLong() {
        List<Object> params = buildParams("bs-update",
                "update " + ENTITY + " o set o.refId = ? where o.id = ?", "99", "1");
        assertEquals(2, params.size());
        assertEquals(99L, params.get(0), "UPDATE SET 参数应绑定为 Long");
        assertEquals(1L, params.get(1), "UPDATE WHERE 参数应绑定为 Long");
    }

    /** LIKE 作用于 VARCHAR 列：必须保持 String，防止 %foo% 被转成数字 */
    @Test
    public void testVarcharLikeParamStaysString() {
        List<Object> params = buildParams("bs-like",
                "select o from " + ENTITY + " as o where o.name like ?", "%al%");
        assertEquals("%al%", params.get(0), "VARCHAR 列 LIKE 参数必须保持 String");
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    // ==== E2E：真实 DAO 查询链（H2 缺省回归 / PG·MySQL docker 子类复跑） ====

    @Test
    public void testGetEntityByIdWithStringId() {
        IOrmEntity entity = dao().getEntityById("1");
        assertNotNull(entity);
        assertEquals("alpha", nameOf(entity));
    }

    @Test
    public void testEqFilterOnPk() {
        List<IOrmEntity> list = findAll(eq("id", "2"));
        assertEquals(1, list.size());
        assertEquals("beta", nameOf(list.get(0)));
    }

    @Test
    public void testEqFilterOnFk() {
        List<IOrmEntity> list = findAll(eq("refId", "30"));
        assertEquals(1, list.size());
        assertEquals("gamma", nameOf(list.get(0)));
    }

    @Test
    public void testNeFilter() {
        List<IOrmEntity> list = findAll(ne("refId", "10"));
        // refId=20/30 命中；refId=null 的行 ne 语义不命中
        assertEquals(2, list.size());
    }

    @Test
    public void testRangeFilters() {
        assertEquals(2, findAll(gt("refId", "10")).size());
        assertEquals(3, findAll(ge("refId", "10")).size());
        assertEquals(1, findAll(lt("refId", "20")).size());
        assertEquals(2, findAll(le("refId", "20")).size());
    }

    @Test
    public void testInFilter() {
        List<IOrmEntity> list = findAll(in("refId", List.of("10", "30")));
        assertEquals(2, list.size());
    }

    @Test
    public void testNotInFilter() {
        List<IOrmEntity> list = findAll(notIn("refId", List.of("10")));
        assertEquals(2, list.size());
    }

    @Test
    public void testBetweenFilter() {
        List<IOrmEntity> list = findAll(between("refId", "10", "20"));
        assertEquals(2, list.size());
    }

    @Test
    public void testAndOrFilter() {
        assertEquals(1, findAll(and(eq("refId", "10"), eq("name", "alpha"))).size());
        assertEquals(3, findAll(or(eq("refId", "10"), eq("refId", "20"), eq("refId", "30"))).size());
    }

    @Test
    public void testIsNullFilter() {
        List<IOrmEntity> list = findAll(isNull("refId"));
        assertEquals(1, list.size());
        assertEquals("delta", nameOf(list.get(0)));
    }

    @Test
    public void testLikeOnVarcharColumn() {
        List<IOrmEntity> list = findAll(like("name", "%a%"));
        assertFalse(list.isEmpty());
        for (IOrmEntity entity : list) {
            assertTrue(nameOf(entity).contains("a"));
        }
    }

    @Test
    public void testCountByQuery() {
        assertEquals(3L, count(ge("refId", "10")));
    }

    @Test
    public void testFindPageByQuery() {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setFilter(ge("refId", "10"));
        query.setLimit(2);
        List<IOrmEntity> list = dao().findPageByQuery(query);
        assertEquals(2, list.size());
    }

    @Test
    public void testDeleteByQuery() {
        saveRow("5", "40", "epsilon");
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setFilter(eq("refId", "40"));
        assertEquals(1L, dao().deleteByQuery(query));
        assertNull(dao().getEntityById("5"));
    }

    @Test
    public void testUpdateByQuery() {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setFilter(eq("refId", "20"));
        dao().updateByQuery(query, Map.of("refId", "25"));
        assertEquals(1, findAll(eq("refId", "25")).size());
    }

    @Test
    public void testCursorPagination() {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.setLimit(2);
        PageBean<IOrmEntity> page = new PageBean<>();
        dao().findPageAndReturnCursor(query, page);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getHasNext());
        assertEquals("1", page.getItems().get(0).orm_propValueByName("id"));
        assertEquals("2", page.getItems().get(1).orm_propValueByName("id"));

        io.nop.api.core.beans.query.QueryBean next = new io.nop.api.core.beans.query.QueryBean();
        next.setLimit(2);
        next.setCursor(page.getNextCursor());
        PageBean<IOrmEntity> page2 = new PageBean<>();
        dao().findPageAndReturnCursor(next, page2);
        assertEquals(2, page2.getItems().size());
        assertEquals("3", page2.getItems().get(0).orm_propValueByName("id"));
    }

    // ==== 边界 ====

    /** >2^53 的 bigint 精确命中：Long 绑定不丢精度 */
    @Test
    public void testBigIntBeyondJsSafePrecision() {
        String big = "9007199254740993"; // 2^53 + 1
        saveRow(big, big, "huge");
        List<IOrmEntity> list = findAll(eq("refId", big));
        assertEquals(1, list.size());
        assertEquals("huge", nameOf(list.get(0)));
    }

    /** 非数字 String：修复后在平台层明确报转换错误，早于且清晰于 DB 的 42883/隐式转换错 */
    @Test
    public void testNonNumericStringFailsFast() {
        NopException error = assertThrows(NopException.class, () -> findAll(eq("id", "abc")));
        assertEquals("nop.err.api.convert-to-type-fail", error.getErrorCode());
    }

    /** 空串 eq 走 is null（平台约定），不产生数值转换 */
    @Test
    public void testEmptyStringEqBecomesIsNull() {
        assertEquals(1L, count(eq("refId", "")));
    }
}
