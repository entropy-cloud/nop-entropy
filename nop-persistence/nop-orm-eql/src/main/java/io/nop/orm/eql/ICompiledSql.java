/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql;

import io.nop.core.lang.sql.SQL;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.ISqlExprMeta;

import java.util.List;

/**
 * 根据eql语句编译得到的SQL语句。
 */
public interface ICompiledSql {
    String getName();

    boolean isUseTenantModel();

    /**
     * 一条SQL语句只能是在一个数据源中执行。目前不支持一条SQL语句访问多个数据源。
     */
    String getQuerySpace();

    SqlStatementKind getStatementKind();

    /**
     * 分析eql语句得到的数据集元数据
     */
    IDataSetMeta getDataSetMeta();

    /**
     * eql语句中涉及到的所有实体对象
     */
    List<String> getReadEntityNames();

    /**
     * 对应update/delete/insert语句，返回被修改的实体对象。如果是查询语句，则返回null
     */
    String getWriteEntityName();

    List<IDataParameterBinder> getColumnBinders();

    /**
     * 从底层的JDBC数据集中读取数据得到getDataSetMeta()中描述的列。 列表的长度应该与getDataSetMeta().getColumnCount()相同。
     */
    List<ISqlExprMeta> getFieldMetas();

    /**
     * 包含翻译后的SQL文本以及marker标记。
     */
    SQL getSql();

    /**
     * 根据EQL语句的参数转化得到SQL语句的参数
     */
    List<Object> buildParams(List<Object> input);

    // ---------- 编译期结构信息（plan 2321，设计文档 §3.1）----------
    // 以下访问器均为编译产物静态属性，随 SQL 文本一起进 QueryPlanCache，缓存安全模型不变。
    // 非 SELECT 语句全部为确定缺省值（false / 空列表），无 null 歧义。

    /**
     * SELECT 是否携带 GROUP BY 子句。
     */
    boolean hasGroupBy();

    /**
     * SELECT 是否携带 HAVING 子句。
     */
    boolean hasHaving();

    /**
     * SELECT 是否为 DISTINCT 查询。
     */
    boolean hasDistinct();

    /**
     * 是否为 UNION 等集合操作查询（SqlUnionSelect）。
     */
    boolean hasSetOp();

    /**
     * SELECT 是否携带 FOR UPDATE。
     */
    boolean hasForUpdate();

    /**
     * 是否为无分组的裸 count 聚合查询（单投影 count(*)/count(expr)，无 GROUP BY/HAVING）。
     * 归并语义为各分片 count 求和。
     */
    boolean isBareCount();

    /**
     * 投影是否包含聚合函数（SqlAggregateFunction，不含子查询内部的聚合——子查询聚合在每分片
     * 内独立求值，不属顶层聚合投影）。
     *
     * <p>plan 2321 Phase 1 裁定的六个构造标志之外的实施期补充：无 GROUP BY 的 SUM/AVG 等
     * 聚合投影在六标志下与普通投影不可区分，而执行引擎要求"非 count 聚合显式失败"
     * （设计 §3.4），故以本标志闭合判定面。与 {@link #isBareCount()} 组合消费：
     * hasAggregate 且非裸 count → 显式失败。</p>
     */
    boolean hasAggregate();

    /**
     * 顶层 ORDER BY 排序项到投影列的序数映射，条目数与排序项数一致。
     * 排序键未投影的条目 {@link SqlOrderByOrdinal#getOrdinal()} 为
     * {@link SqlOrderByOrdinal#ORDINAL_NOT_PROJECTED}（无有效映射项，消费方须显式失败）。
     * 无 ORDER BY 或非 SELECT 语句返回空列表。
     */
    List<SqlOrderByOrdinal> getOrderByOrdinals();
}
