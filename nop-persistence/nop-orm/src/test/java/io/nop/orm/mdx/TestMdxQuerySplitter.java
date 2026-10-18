/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.mdx;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.GroupFieldBean;
import io.nop.api.core.beans.query.QueryAggregateFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.beans.query.QueryJoinConditionBean;
import io.nop.api.core.beans.query.QuerySourceBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.orm.OrmErrors;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MdxQuerySplitter} 的拆分语义测试。
 *
 * <p>聚焦三条曾经静默失效的主/子表对齐不变式：
 * <ol>
 *     <li>显式join未声明dimFields时，必须回落到可用维度，而不是抛「维度个数不匹配」。</li>
 *     <li>join的conditions必须被消费，按主表维度顺序推导子表维度列。</li>
 *     <li>主表bean必须完整承载会被 queryToSelectFieldsSql 消费的查询属性（distinct 等）。</li>
 * </ol>
 */
class TestMdxQuerySplitter {

    private static IEntityModel entityModel(String name, String... pkColumns) {
        List<String> pk = Arrays.asList(pkColumns);
        return (IEntityModel) Proxy.newProxyInstance(
                TestMdxQuerySplitter.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getName":
                            return name;
                        case "getPkColumnNames":
                            return pk;
                        case "toString":
                            return "EntityModel(" + name + ")";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                    }
                    return null;
                });
    }

    private static QueryFieldBean subField(String owner, String name) {
        return QueryFieldBean.subField(owner, name);
    }

    private static QueryBean queryWithSubField(String alias) {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField(alias, "deptId").count().alias("deptCount"));
        return query;
    }

    // ---------------------------------------------------------------- B1

    /**
     * 子表字段含非聚合列时，回落到该列，且回落结果必须同时驱动结果集列下标和子表维度列名。
     */
    @Test
    void testExplicitJoinWithoutDimFieldsFallsBackToNonAggField() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("deptAlias", "userId"));

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("deptAlias");
        join.setSourceName("UserEntity");
        query.setJoins(List.of(join));

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId"));

        assertEquals(2, queries.size());
        MdxQueryBean sub = queries.get(1);
        assertNotNull(sub.getDimFields(), "回落后的dimFields必须回写到子查询bean");
        assertEquals(List.of("userId"), sub.getDimFields());
        assertEquals(1, sub.getDimFieldIndexes().size());

        // dimFieldIndexes 必须真实指向 dimFields 在 fields 中的下标
        int idx = sub.getDimFieldIndexes().get(0);
        assertEquals(sub.getDimFields().get(0), sub.getFields().get(idx).getName());
    }

    /**
     * 子表字段全是聚合字段时没有任何可推导的维度列。必须报「没有指定dimFields」这种可定位的错误，
     * 而不是让调用方拿到一个语义错误的「维度个数不匹配」，更不能静默产出错位结果。
     */
    @Test
    void testExplicitJoinWithOnlyAggFieldsRequiresDimFields() {
        QueryBean query = queryWithSubField("deptAlias");

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("deptAlias");
        join.setSourceName("UserEntity");
        query.setJoins(List.of(join));

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_NO_DIM_FIELDS.getErrorCode(), err.getErrorCode());
    }

    /**
     * 显式join下声明了dimFields即可正常拆分，主表默认以主键为维度。
     */
    @Test
    void testMainQueryDimFieldsDefaultToPkColumn() {
        QueryBean query = queryWithSubField("deptAlias");

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("deptAlias");
        join.setSourceName("UserEntity");
        join.setDimFields(List.of("dept_id"));
        query.setJoins(List.of(join));

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId"));

        MdxQueryBean main = queries.get(0);
        assertEquals(List.of("deptId"), main.getDimFields());
        assertEquals(List.of("deptName", "deptId"), names(main));
        assertTrue(main.getFields().get(1).isInternal());
        assertEquals(List.of("dept_id"), queries.get(1).getDimFields());
    }

    // ---------------------------------------------------------------- B2

    /**
     * leftJoin/rightJoin/innerJoin 在 query.dimFields 未预设时会生成 conditions 而非 dimFields
     * （见 QueryBean.addJoin）。这条路径曾经把关联键整个丢掉并直接抛维度不匹配，
     * 是公开 join API 不可用的根因。
     */
    @Test
    void testJoinConditionsDriveSubDimFields() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "userId").count().alias("userCount"));
        query.leftJoin("UserEntity", "d", "deptId,deptType", "dept_id,dept_type");

        QuerySourceBean join = query.getJoinByAlias("d");
        assertNull(join.getDimFields(), "本用例的前提是addJoin落入conditions分支");
        assertNotNull(join.getConditions());

        List<MdxQueryBean> queries =
                new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId", "deptType"));

        MdxQueryBean sub = queries.get(1);
        assertEquals(List.of("dept_id", "dept_type"), sub.getDimFields());
        assertEquals(2, sub.getDimFieldIndexes().size());
        for (int i = 0; i < 2; i++) {
            assertEquals(sub.getDimFields().get(i), sub.getFields().get(sub.getDimFieldIndexes().get(i)).getName());
        }
    }

    /**
     * 单列 leftJoin 是最简形态：主表以主键为维度，conditions 恰好覆盖它。
     */
    @Test
    void testSingleColumnLeftJoinIsUsable() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "userId").count().alias("userCount"));
        query.leftJoin("UserEntity", "d", "deptId", "dept_id");

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId"));

        MdxQueryBean main = queries.get(0);
        MdxQueryBean sub = queries.get(1);
        assertEquals(List.of("deptId"), main.getDimFields());
        assertEquals(List.of("dept_id"), sub.getDimFields());
        assertEquals(1, main.getDimFieldIndexes().size());
        assertEquals(1, sub.getDimFieldIndexes().size());
    }

    /**
     * conditions 必须按主表维度顺序重排，而不是照抄conditions的书写顺序，
     * 否则 buildDimValue 构造的位置键与主表对不上，子表结果会静默丢失。
     */
    @Test
    void testJoinConditionsAreOrderedByMainDimFields() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setDimFields(List.of("a", "b"));
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "v").count().alias("vCount"));

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("d");
        join.setSourceName("SubEntity");
        // 故意以 b 在前、a 在后的顺序声明
        join.setConditions(List.of(condition("b", "yb"), condition("a", "ya")));
        query.setJoins(List.of(join));

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "a", "b"));

        assertEquals(List.of("ya", "yb"), queries.get(1).getDimFields());
    }

    /**
     * conditions 未覆盖全部维度字段时无法按位置对齐，必须报错而不是产出错位结果。
     */
    @Test
    void testJoinConditionsNotCoveringAllDimFieldsThrows() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setDimFields(List.of("a", "b"));
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "v").count().alias("vCount"));

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("d");
        join.setSourceName("SubEntity");
        join.setConditions(List.of(condition("a", "ya")));
        query.setJoins(List.of(join));

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "a", "b")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_DIM_FIELDS_MISMATCH.getErrorCode(), err.getErrorCode());
    }

    /**
     * 显式dimFields优先于conditions：调用方直接指定子表维度列时不应被join条件改写。
     */
    @Test
    void testExplicitDimFieldsWinOverConditions() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setDimFields(List.of("a"));
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "v").count().alias("vCount"));

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("d");
        join.setSourceName("SubEntity");
        join.setDimFields(List.of("explicitDim"));
        join.setConditions(List.of(condition("a", "ya")));
        query.setJoins(List.of(join));

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "a"));

        assertEquals(List.of("explicitDim"), queries.get(1).getDimFields());
    }

    // ---------------------------------------------------------------- B4

    /**
     * getMainQuery 是一次不完整的属性拷贝：遗漏的字段会被 DaoQueryHelper.queryToSelectFieldsSql
     * 静默忽略，导致主表查询语义丢失。distinct 是已确认的线上影响
     * （SysDaoNamingService.getServices 依赖 SELECT DISTINCT 去重服务名）。
     */
    @Test
    void testMainQueryPreservesDistinctLeftJoinPropsAndLogicalDelete() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setName("myQuery");
        query.distinct();
        query.setLeftJoinProps(List.of("parent"));
        query.setDisableLogicalDelete(true);
        query.addField(QueryFieldBean.forField("deptName"));
        query.addFilter(FilterBeans.eq("status", 1));

        MdxQueryBean main = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")).get(0);

        assertTrue(main.isDistinct());
        assertEquals(List.of("parent"), main.getLeftJoinProps());
        assertTrue(main.isDisableLogicalDelete());
        assertEquals("myQuery", main.getName());
    }

    @Test
    void testDistinctReachesGeneratedSql() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.distinct();
        query.addField(QueryFieldBean.forField("deptName"));

        MdxQueryBean main = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")).get(0);

        String sql = DaoQueryHelper.queryToSelectFieldsSql(null, main, null).end().toString();
        assertTrue(sql.contains("distinct"), "MDX主查询必须保留DISTINCT，实际SQL: " + sql);
    }

    @Test
    void testMainQueryPreservesPaginationAndOrdering() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setLimit(20);
        query.setOffset(40);
        query.setCursor("cur");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addOrderField("deptName", true);

        MdxQueryBean main = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")).get(0);

        assertEquals(20, main.getLimit());
        assertEquals(40L, main.getOffset());
        assertEquals("cur", main.getCursor());
        assertEquals(1, main.getOrderBy().size());
    }

    // ---------------------------------------------------------------- 结果正确性

    /**
     * 主表和子表字段最终写入同一个结果map，而 QueryFieldBean.getLabel() 不含owner信息，
     * 同名字段会静默互相覆盖。必须报错而不是产出被污染的结果。
     */
    @Test
    void testDuplicateFieldLabelBetweenMainAndSubThrows() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "deptName"));

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_DUPLICATE_FIELD_LABEL.getErrorCode(), err.getErrorCode());
    }

    /**
     * 聚合字段的label是 {@code aggFunc(name)}，主表与子表同聚合同字段同样会碰撞。
     */
    @Test
    void testDuplicateAggFieldLabelBetweenMainAndSubThrows() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName").count());
        query.addField(subField("d", "deptName").count());

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_DUPLICATE_FIELD_LABEL.getErrorCode(), err.getErrorCode());
    }

    /**
     * alias 可以消除碰撞，因此带alias的同名字段必须仍然可用。
     */
    @Test
    void testAliasDisambiguatesSameFieldName() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(subField("d", "deptName").alias("subDeptName"));

        QuerySourceBean join = new QuerySourceBean();
        join.setAlias("d");
        join.setSourceName("SubEntity");
        join.setDimFields(List.of("dept_id"));
        query.setJoins(List.of(join));

        List<MdxQueryBean> all = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId"));

        assertEquals(2, all.size());
        assertEquals("deptName", all.get(0).getFields().get(0).getLabel());
        assertEquals("subDeptName", all.get(1).getFields().get(0).getLabel());
    }

    /**
     * expression/formula 在MDX通道没有任何消费方，SQL里也不会出现。
     * 静默丢弃会让调用方以为自己做了字段级计算。
     */
    @Test
    void testUnsupportedFieldAttrThrows() {
        QueryFieldBean field = QueryFieldBean.forField("price");
        field.setFormula("amount * 2");

        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(field);

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR.getErrorCode(), err.getErrorCode());
    }

    @Test
    void testAggregatesThrows() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.addField(QueryFieldBean.forField("deptName"));
        query.setAggregates(List.of(new QueryAggregateFieldBean()));

        NopException err = assertThrows(NopException.class,
                () -> new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")));
        assertEquals(OrmErrors.ERR_ORM_QUERY_UNSUPPORTED_FIELD_ATTR.getErrorCode(), err.getErrorCode());
    }

    // ---------------------------------------------------------------- 调用方状态

    /**
     * QueryBean.addGroupField 会就地追加到 groupBy 列表上。getMainQuery 若直接透传引用，
     * 调用方的集合会被悄悄改写，之后复用该 QueryBean 会带上多余的 GROUP BY。
     */
    @Test
    void testSplitDoesNotMutateCallerGroupByList() {
        List<GroupFieldBean> groupBy = new ArrayList<>(List.of(GroupFieldBean.forField("deptType")));

        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");
        query.setGroupBy(groupBy);
        query.addField(QueryFieldBean.forField("deptName"));
        query.addField(QueryFieldBean.forField("deptId").sum());

        MdxQueryBean main = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")).get(0);

        assertEquals(List.of("deptType"), groupBy.stream().map(GroupFieldBean::getName).toList(),
                "调用方的groupBy列表不得被MDX拆分改写");
        assertEquals(List.of("deptType", "deptName"),
                main.getGroupBy().stream().map(GroupFieldBean::getName).toList());
    }

    @Test
    void testSplitDoesNotMutateCallerSourceName() {
        QueryBean query = new QueryBean();
        // 故意不设置sourceName，由split从entityModel推导
        query.addField(QueryFieldBean.forField("deptName"));

        MdxQueryBean main = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId")).get(0);

        assertEquals("DeptEntity", main.getSourceName());
        assertNull(query.getSourceName(), "split不得回写调用方的sourceName");
    }

    @Test
    void testNullFieldsDoesNotThrowNpe() {
        QueryBean query = new QueryBean();
        query.setSourceName("DeptEntity");

        List<MdxQueryBean> queries = new MdxQuerySplitter().split(query, entityModel("DeptEntity", "deptId"));

        assertEquals(1, queries.size());
        assertEquals(List.of("deptId"), names(queries.get(0)));
    }

    private static List<String> names(MdxQueryBean bean) {
        List<String> ret = new ArrayList<>();
        for (QueryFieldBean field : bean.getFields())
            ret.add(field.getName());
        return ret;
    }

    private static QueryJoinConditionBean condition(String left, String right) {
        QueryJoinConditionBean bean = new QueryJoinConditionBean();
        bean.setLeftField(left);
        bean.setRightField(right);
        return bean;
    }
}