/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.cache.CacheRef;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.dataset.BeanRowMapper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.BinderFieldMapper;
import io.nop.dataset.rowmapper.ColRowMapper;
import io.nop.dataset.rowmapper.SmartRowMapper;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.sql_lib._gen._SqlItemModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_INDEX;
import static io.nop.orm.OrmErrors.ARG_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_INVALID_COL_INDEX;

public abstract class SqlItemModel extends _SqlItemModel {
    private SqlLibModel sqlLibModel;

    public SqlItemModel() {

    }

    public boolean isColNameCamelCase() {
        return false;
    }

    public SqlLibModel getSqlLibModel() {
        return sqlLibModel;
    }

    public void setSqlLibModel(SqlLibModel sqlLibModel) {
        this.sqlLibModel = sqlLibModel;
    }

    public SQL buildSql(IEvalContext context) {
        checkArgs(context);
        IMarkedString sql = generateSql(context);
        int timeout = getTimeout() == null ? -1 : getTimeout();
        int fetchSize = getFetchSize() == null ? -1 : getFetchSize();
        CacheRef cacheRef = null;
        if (getCacheKeyExpr() != null && getCacheName() != null) {
            Object cacheKey = getCacheKeyExpr().invoke(context);
            cacheRef = new CacheRef(getCacheName(), (Serializable) cacheKey);
        }
        return new SQL(getName(), sql.getText(), sql.getMarkers(), timeout, cacheRef, fetchSize, getQuerySpace(),
                isDisableLogicalDelete(), getLocation());
    }

    void checkArgs(IEvalContext context) {

    }

    protected IMarkedString generateSql(IEvalContext context) {
        throw new UnsupportedOperationException();
    }

    public Object invoke(ISqlExecutor executor, LongRangeBean range, IEvalContext context) {
        SQL sql = buildSql(context);
        SqlMethod method = getSqlMethod();
        if (method == null) {
            String text = StringHelper.trimLeft(sql.getText());
            if (StringHelper.startsWithIgnoreCase(text, "select")) {
                if (range != null) {
                    method = SqlMethod.findPage;
                } else {
                    method = SqlMethod.findAll;
                }
            } else {
                method = SqlMethod.execute;
            }
        }

        IEvalScope scope = context.getEvalScope();
        IDialect dialect = executor.getDialectForQuerySpace(getQuerySpace());
        ValueWithLocation dialectVl = scope.recordValueLocation(OrmConstants.PARAM_DIALECT);
        ValueWithLocation modelVl = scope.recordValueLocation(OrmConstants.PARAM_SQL_ITEM_MODEL);
        scope.setLocalValue(null, OrmConstants.PARAM_DIALECT, dialect);
        scope.setLocalValue(null, OrmConstants.PARAM_SQL_ITEM_MODEL, this);

        try {
            switch (method) {
                case execute:
                    return executor.executeMultiSql(sql);
                case findAll:
                    return processResult(executor.findAll(sql, buildRowMapper(executor, sql.getQuerySpace())), executor);
                case findPage: {
                    long offset = range == null ? 0 : range.getOffset();
                    int limit = range == null ? 10 : (int) range.getLimit();
                    List<Object> data = executor.findPage(sql, offset, limit,
                            buildRowMapper(executor, sql.getQuerySpace()));
                    return processResult(data, executor);
                }
                case findFirst:
                    return executor.findFirst(sql, buildRowMapper(executor, sql.getQuerySpace()));
                case exists:
                    return executor.exists(sql);
                default:
                    throw new UnsupportedOperationException();
            }
        } finally {
            scope.restoreValueLocation(OrmConstants.PARAM_DIALECT, dialectVl);
            scope.restoreValueLocation(OrmConstants.PARAM_SQL_ITEM_MODEL, modelVl);
        }
    }

    protected List<Object> processResult(List<Object> data, ISqlExecutor executor) {
        if (data.isEmpty())
            return data;

        if (getBatchLoadSelection() != null && executor instanceof IOrmTemplate) {
            ((IOrmTemplate) executor).batchLoadSelection(data, getBatchLoadSelection());
        }
        return data;
    }

    protected IRowMapper buildRowMapper(ISqlExecutor executor, String querySpace) {
        IRowMapper rowMapper;
        if (DaoConstants.SQL_TYPE_SQL.equals(getType())) {

            if (getRowType() == null) {
                rowMapper = isColNameCamelCase() ? SmartRowMapper.CAMEL_CASE : SmartRowMapper.CASE_INSENSITIVE;
            } else {
                rowMapper = buildBeanMapper(getRowType(), true);
            }
        } else {
            // eql语法时列名就是指定的属性名，不需要作camelCase转换
            if (getRowType() == null) {
                rowMapper = SmartRowMapper.INSTANCE;
            } else {
                // sql语法时会对列名进行camelCase变换后作为属性名
                rowMapper = buildBeanMapper(getRowType(), false);
            }
        }

        if (hasFields()) {
            IDialect dialect = executor.getDialectForQuerySpace(querySpace);
            rowMapper = addColMapper(rowMapper, dialect);
        }
        return new SmartRowMapper(rowMapper);
    }

    private IRowMapper addColMapper(IRowMapper rowMapper, IDialect dialect) {
        List<IDataParameterBinder> binders = new ArrayList<>();
        for (SqlFieldModel colModel : getFields()) {
            if (colModel.getIndex() < 0 || colModel.getIndex() > 1000)
                throw new NopException(ERR_SQL_LIB_INVALID_COL_INDEX).loc(getLocation())
                        .param(ARG_INDEX, colModel.getIndex()).param(ARG_SQL_NAME, getName());

            IDataParameterBinder binder = dialect.getDataParameterBinder(colModel.getStdSqlType().getStdDataType(),
                    colModel.getStdSqlType());

            if (binder != null)
                CollectionHelper.set(binders, colModel.getIndex(), binder);
        }
        IFieldMapper colMapper = new BinderFieldMapper(binders);
        return new ColRowMapper(rowMapper, colMapper);
    }

    protected IRowMapper buildBeanMapper(IGenericType rowType, boolean camelCase) {
        IClassModel classModel = ReflectionManager.instance().loadClassModel(rowType.getRawTypeName());
        return new BeanRowMapper(classModel.getBeanModel(), camelCase);
    }
}
