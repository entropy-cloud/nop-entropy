/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.commons.cache.CacheRef;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.dataset.BeanRowMapper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.IRowMapper;
import io.nop.dataset.binder.IDataParameterBinder;
import io.nop.dataset.impl.BinderMapFieldMapper;
import io.nop.dataset.rowmapper.ColRowMapper;
import io.nop.dataset.rowmapper.ColumnMapRowMapper;
import io.nop.dataset.rowmapper.SmartRowMapper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.dataset.OrmEntityBuilder;
import io.nop.orm.sql_lib._gen._SqlItemModel;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class SqlItemModel extends _SqlItemModel {
    private SqlLibModel sqlLibModel;

    public SqlItemModel() {

    }

    public boolean isAllowUnderscoreName() {
        return false;
    }

    public boolean isEnableFilter() {
        return false;
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
                isDisableLogicalDelete(), isAllowUnderscoreName(), isEnableFilter(), getLocation());
    }

    void checkArgs(IEvalContext context) {

    }

    protected IMarkedString generateSql(IEvalContext context) {
        throw new UnsupportedOperationException();
    }

    public Object invoke(IDaoProvider daoProvider, ISqlExecutor executor, LongRangeBean range, IEvalContext context) {

        IEvalScope scope = context.getEvalScope();
        IDialect dialect = executor.getDialectForQuerySpace(getQuerySpace());
        scope.setLocalValue(null, OrmConstants.PARAM_DIALECT, dialect);
        scope.setLocalValue(null, OrmConstants.PARAM_SQL_ITEM_MODEL, this);

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

        switch (method) {
            case execute:
                return executor.executeMultiSql(sql);
            case findAll:
                return processResult(executor.findAll(sql,
                        buildRowMapper(daoProvider, executor, sql.getQuerySpace(), scope)), executor, daoProvider);
            case findPage: {
                long offset = range == null ? 0 : range.getOffset();
                int limit = range == null ? 10 : (int) range.getLimit();
                List<Object> data = executor.findPage(sql, offset, limit,
                        buildRowMapper(daoProvider, executor, sql.getQuerySpace(), scope));
                data = processResult(data, executor, daoProvider);
                return buildResult(data, scope);
            }
            case findFirst: {
                Object data = executor.findFirst(sql,
                        buildRowMapper(daoProvider, executor, sql.getQuerySpace(), scope));
                data = processSingleResult(data, executor, daoProvider);
                return buildResult(data, scope);
            }
            case exists:
                return executor.exists(sql);
            default:
                throw new UnsupportedOperationException();
        }
    }

    protected Object buildResult(Object result, IEvalScope scope) {
        if (getBuildResult() != null) {

            result = getBuildResult().call1(null, result, scope);
        }
        return result;
    }

    protected IOrmEntityDao<IOrmEntity> rowTypeDao(IDaoProvider daoProvider) {
        if (daoProvider == null)
            return null;

        String rowType = getRowType();
        if (rowType == null)
            return null;

        if (daoProvider.hasDao(rowType))
            return (IOrmEntityDao) daoProvider.dao(rowType);

        return null;
    }

    protected List<Object> processResult(List<Object> data, ISqlExecutor executor, IDaoProvider daoProvider) {
        if (data.isEmpty())
            return data;

        if (getRowType() != null) {
            IOrmEntityDao<IOrmEntity> dao = rowTypeDao(daoProvider);

            IBeanModel beanModel = ReflectionManager.instance().loadBeanModel(getRowType());
            data = data.stream().map(item -> {
                if (item instanceof Map) {
                    if (dao != null)
                        return new OrmEntityBuilder<>(dao, getOrmEntityRefreshBehavior()).buildEntity((Map<String, Object>) item);
                    return BeanRowMapper.newBean(beanModel, (Map<String, Object>) item, false);
                } else {
                    return BeanTool.buildBean(item, beanModel.getType());
                }
            }).collect(Collectors.toList());
        }

        if (getBatchLoadSelection() != null && executor instanceof IOrmTemplate) {
            ((IOrmTemplate) executor).batchLoadSelection(data, getBatchLoadSelection());
        }

        return data;
    }

    protected Object processSingleResult(Object data, ISqlExecutor executor, IDaoProvider daoProvider) {
        if (data == null)
            return null;

        if (getRowType() != null) {
            IOrmEntityDao<IOrmEntity> dao = rowTypeDao(daoProvider);
            IBeanModel beanModel = ReflectionManager.instance().loadBeanModel(getRowType());

            if (data instanceof Map) {
                if (dao != null)
                    return new OrmEntityBuilder<>(dao, getOrmEntityRefreshBehavior()).buildEntity((Map<String, Object>) data);

                return BeanRowMapper.newBean(beanModel, (Map<String, Object>) data, false);
            } else {
                return BeanTool.buildBean(data, beanModel.getRawClass());
            }
        }

        if (getBatchLoadSelection() != null && executor instanceof IOrmTemplate) {
            ((IOrmTemplate) executor).batchLoadSelection(Collections.singleton(data), getBatchLoadSelection());
        }

        return data;
    }

    protected IRowMapper buildRowMapper(IDaoProvider daoProvider, ISqlExecutor executor, String querySpace, IEvalScope scope) {
        boolean sql = DaoConstants.SQL_TYPE_SQL.equals(getType());

        IRowMapper rowMapper;
        if (hasFields()) {
            rowMapper = new SqlFiledRowMapper(this, sql, scope);
        } else {
            if (sql) {
                rowMapper = isColNameCamelCase() ? ColumnMapRowMapper.CAMEL_CASE : ColumnMapRowMapper.CASE_INSENSITIVE;
            } else {
                // eql语法时列名就是指定的属性名，不需要作camelCase转换
                rowMapper = ColumnMapRowMapper.INSTANCE;
            }
        }

        IDialect dialect = executor.getDialectForQuerySpace(querySpace);
        IFieldMapper colMapper = buildColMapper(dialect, sql);
        if (colMapper != null)
            rowMapper = new ColRowMapper<>(rowMapper, colMapper);
        scope.setLocalValue(OrmConstants.PARAM_ROW_MAPPER, rowMapper);

        if (getBuildRowMapper() != null) {
            Object result = getBuildRowMapper().call1(null, this, scope);
            if (result instanceof IRowMapper) {
                rowMapper = ((IRowMapper) result);
            }
        }

        if (!isDisableSmartRowMapper()) {
            if (!(rowMapper instanceof SmartRowMapper))
                rowMapper = new SmartRowMapper(rowMapper);
        }
        return rowMapper;
    }

    private IFieldMapper buildColMapper(IDialect dialect, boolean caseInsensitive) {
        Map<String, IDataParameterBinder> binders = caseInsensitive ?
                CollectionHelper.newCaseInsensitiveMap(getFields().size()) : new LinkedHashMap<>();

        for (SqlFieldModel colModel : getFields()) {
            if (colModel.getComputeExpr() != null)
                continue;

            if (colModel.getStdSqlType() == null)
                continue;

            StdSqlType sqlType = colModel.getStdSqlType();
            StdDataType dataType = sqlType.getStdDataType();
            if (colModel.getStdDataType() != null)
                dataType = colModel.getStdDataType();

            IDataParameterBinder binder = dialect.getDataParameterBinder(dataType,
                    colModel.getStdSqlType());

            if (binder != null)
                binders.put(colModel.getName(), binder);
        }
        if (binders.isEmpty())
            return null;
        return new BinderMapFieldMapper(binders);
    }
}