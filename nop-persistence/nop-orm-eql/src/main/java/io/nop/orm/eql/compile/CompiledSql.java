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
import java.util.Collections;
import java.util.List;

public class CompiledSql implements ICompiledSql {
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
        this.readEntityModels = readEntityModels;
    }

    @Override
    public String getWriteEntityName() {
        return writeEntityModel;
    }

    public void setWriteEntityModel(String writeEntityModel) {
        this.writeEntityModel = writeEntityModel;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getQuerySpace() {
        return querySpace;
    }

    public void setQuerySpace(String querySpace) {
        this.querySpace = querySpace;
    }

    @Override
    public SqlStatementKind getStatementKind() {
        return statementKind;
    }

    public void setStatementKind(SqlStatementKind statementKind) {
        this.statementKind = statementKind;
    }

    @Override
    public IDataSetMeta getDataSetMeta() {
        return dataSetMeta;
    }

    public void setDataSetMeta(IDataSetMeta dataSetMeta) {
        this.dataSetMeta = dataSetMeta;
    }

    @Override
    public List<IDataParameterBinder> getColumnBinders() {
        return columnBinders;
    }

    public void setColumnBinders(List<IDataParameterBinder> columnBinders) {
        this.columnBinders = columnBinders;
    }

    @Override
    public List<ISqlExprMeta> getFieldMetas() {
        return fieldMetas;
    }

    public void setFieldMetas(List<ISqlExprMeta> fieldMetas) {
        this.fieldMetas = fieldMetas;
    }

    @Override
    public SQL getSql() {
        return sql;
    }

    public void setSql(SQL sql) {
        this.sql = sql;
    }

    public List<ISqlParamBuilder> getParamBuilders() {
        return paramBuilders;
    }

    public void setParamBuilders(List<ISqlParamBuilder> paramBuilders) {
        this.paramBuilders = paramBuilders;
    }

    @Override
    public boolean isUseTenantModel() {
        return useTenantModel;
    }

    public void setUseTenantModel(boolean useTenantModel) {
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
        this.hasGroupBy = hasGroupBy;
    }

    @Override
    public boolean hasHaving() {
        return hasHaving;
    }

    public void setHasHaving(boolean hasHaving) {
        this.hasHaving = hasHaving;
    }

    @Override
    public boolean hasDistinct() {
        return hasDistinct;
    }

    public void setHasDistinct(boolean hasDistinct) {
        this.hasDistinct = hasDistinct;
    }

    @Override
    public boolean hasSetOp() {
        return hasSetOp;
    }

    public void setHasSetOp(boolean hasSetOp) {
        this.hasSetOp = hasSetOp;
    }

    @Override
    public boolean hasForUpdate() {
        return hasForUpdate;
    }

    public void setHasForUpdate(boolean hasForUpdate) {
        this.hasForUpdate = hasForUpdate;
    }

    @Override
    public boolean isBareCount() {
        return bareCount;
    }

    public void setBareCount(boolean bareCount) {
        this.bareCount = bareCount;
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    public void setHasAggregate(boolean hasAggregate) {
        this.hasAggregate = hasAggregate;
    }

    @Override
    public List<SqlOrderByOrdinal> getOrderByOrdinals() {
        return orderByOrdinals;
    }

    public void setOrderByOrdinals(List<SqlOrderByOrdinal> orderByOrdinals) {
        this.orderByOrdinals = orderByOrdinals == null ? Collections.emptyList()
                : Collections.unmodifiableList(orderByOrdinals);
    }
}
