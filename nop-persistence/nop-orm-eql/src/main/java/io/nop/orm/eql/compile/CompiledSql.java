/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.lang.sql.SQL;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.SqlOrderByOrdinal;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.ISqlExprMeta;
import io.nop.orm.eql.param.ISqlParamBuilder;

import java.util.ArrayList;
import io.nop.api.core.exceptions.NopException;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_FROZEN;

import java.util.Collections;
import java.util.List;

public class CompiledSql implements ICompiledSql {
    /**
     * 2.3-C1-013（plan 2552 裁定 H）：发布前冻结——19 个 setter 前置 checkFrozen（frozen 后
     * 抛 NopException(ERR_EQL_FROZEN)）；冻结点 = EqlCompiler.compile（唯一装配点）return 前
     * 最后语句，缓存发布面 = 冻结点（安全发布：frozen 为 volatile，冻结前的字段写对缓存读侧
     * 可见）。
     */
    private volatile boolean frozen;

    public void freeze() {
        this.frozen = true;
    }

    private void checkFrozen() {
        if (frozen)
            throw new NopException(ERR_EQL_FROZEN).param("sqlName", name);
    }

    private String name;
    private String querySpace;
    private SqlStatementKind statementKind;
    private IDataSetMeta dataSetMeta;
    private List<IDataParameterBinder> columnBinders;
    private List<ISqlExprMeta> fieldMetas;
    private List<ISqlParamBuilder> paramBuilders = Collections.emptyList();
    private SQL sql;
    private List<String> readEntityModels = Collections.emptyList();
    private String writeEntityModel;
    private boolean useTenantModel;

    // plan 2321：编译期结构信息（ICompiledSql 只读增量）。填充点在 EqlCompiler SELECT 分支，
    // 非 SELECT 语句保持确定缺省值。标志为编译产物静态属性，随 SQL 文本进 QueryPlanCache。
    private boolean hasGroupBy;
    private boolean hasHaving;
    private boolean hasDistinct;
    private boolean hasSetOp;
    private boolean hasForUpdate;
    private boolean bareCount;
    private boolean hasAggregate;
    private List<SqlOrderByOrdinal> orderByOrdinals = Collections.emptyList();

    @Override
    public List<String> getReadEntityNames() {
        return readEntityModels;
    }

    public void setReadEntityModels(List<String> readEntityModels) {
        checkFrozen();
        this.readEntityModels = readEntityModels;
    }

    @Override
    public String getWriteEntityName() {
        return writeEntityModel;
    }

    public void setWriteEntityModel(String writeEntityModel) {
        checkFrozen();
        this.writeEntityModel = writeEntityModel;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        checkFrozen();
        this.name = name;
    }

    @Override
    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        checkFrozen();
        this.querySpace = querySpace;
    }

    @Override
    public SqlStatementKind getStatementKind() {
        return statementKind;
    }

    public void setStatementKind(SqlStatementKind statementKind) {
        checkFrozen();
        this.statementKind = statementKind;
    }

    @Override
    public IDataSetMeta getDataSetMeta() {
        return dataSetMeta;
    }

    public void setDataSetMeta(IDataSetMeta dataSetMeta) {
        checkFrozen();
        this.dataSetMeta = dataSetMeta;
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return columnBinders;
    }

    public void setColumnBinders(List<IDataParameterBinder> columnBinders) {
        checkFrozen();
        this.columnBinders = columnBinders;
    }

    @Override
    public List<ISqlExprMeta> getFieldMetas() {
        return fieldMetas;
    }

    public void setFieldMetas(List<ISqlExprMeta> fieldMetas) {
        checkFrozen();
        this.fieldMetas = fieldMetas;
    }

    @Override
    public SQL getSql() {
        return sql;
    }

    public void setSql(SQL sql) {
        checkFrozen();
        this.sql = sql;
    }

    public List<ISqlParamBuilder> getParamBuilders() {
        return paramBuilders;
    }

    public void setParamBuilders(List<ISqlParamBuilder> paramBuilders) {
        checkFrozen();
        this.paramBuilders = paramBuilders;
    }

    @Override
    public boolean isUseTenantModel() {
        return useTenantModel;
    }

    public void setUseTenantModel(boolean useTenantModel) {
        checkFrozen();
        this.useTenantModel = useTenantModel;
    }

    @Override
    public List<Object> buildParams(List<Object> input) {
        List<Object> ret = new ArrayList<>(sql.getMarkers().size());
        this.paramBuilders.forEach(c -> c.buildParams(input, ret));
        return ret;
    }

    @Override
    public boolean hasGroupBy() {
        return hasGroupBy;
    }

    public void setHasGroupBy(boolean hasGroupBy) {
        checkFrozen();
        this.hasGroupBy = hasGroupBy;
    }

    @Override
    public boolean hasHaving() {
        return hasHaving;
    }

    public void setHasHaving(boolean hasHaving) {
        checkFrozen();
        this.hasHaving = hasHaving;
    }

    @Override
    public boolean hasDistinct() {
        return hasDistinct;
    }

    public void setHasDistinct(boolean hasDistinct) {
        checkFrozen();
        this.hasDistinct = hasDistinct;
    }

    @Override
    public boolean hasSetOp() {
        return hasSetOp;
    }

    public void setHasSetOp(boolean hasSetOp) {
        checkFrozen();
        this.hasSetOp = hasSetOp;
    }

    @Override
    public boolean hasForUpdate() {
        return hasForUpdate;
    }

    public void setHasForUpdate(boolean hasForUpdate) {
        checkFrozen();
        this.hasForUpdate = hasForUpdate;
    }

    @Override
    public boolean isBareCount() {
        return bareCount;
    }

    public void setBareCount(boolean bareCount) {
        checkFrozen();
        this.bareCount = bareCount;
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    public void setHasAggregate(boolean hasAggregate) {
        checkFrozen();
        this.hasAggregate = hasAggregate;
    }

    @Override
    public List<SqlOrderByOrdinal> getOrderByOrdinals() {
        return orderByOrdinals;
    }

    public void setOrderByOrdinals(List<SqlOrderByOrdinal> orderByOrdinals) {
        checkFrozen();
        this.orderByOrdinals = orderByOrdinals == null ? Collections.emptyList()
                : Collections.unmodifiableList(orderByOrdinals);
    }
}
