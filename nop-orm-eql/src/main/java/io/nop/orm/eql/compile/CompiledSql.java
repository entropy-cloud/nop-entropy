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
    public List<Object> buildParams(List<Object> input) {
        List<Object> ret = new ArrayList<>(sql.getMarkers().size());
        this.paramBuilders.forEach(c -> c.buildParams(input, ret));
        return ret;
    }
}
