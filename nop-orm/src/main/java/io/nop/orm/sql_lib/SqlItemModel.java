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
import io.nop.core.context.IEvalContext;
import io.nop.core.dataset.BeanRowMapper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                return processResult(executor.findAll(sql, buildRowMapper(executor, sql.getQuerySpace(), scope)), executor);
            case findPage: {
                long offset = range == null ? 0 : range.getOffset();
                int limit = range == null ? 10 : (int) range.getLimit();
                List<Object> data = executor.findPage(sql, offset, limit,
                        buildRowMapper(executor, sql.getQuerySpace(), scope));
                data = processResult(data, executor);
                return buildResult(data, scope);
            }
            case findFirst: {
                Object data = executor.findFirst(sql, buildRowMapper(executor, sql.getQuerySpace(), scope));
                data = processSingleResult(data, executor);
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
            scope.setLocalValue(OrmConstants.PARAM_DATA, result);
            result = getBuildResult().invoke(scope);
        }
        return result;
    }

    protected List<Object> processResult(List<Object> data, ISqlExecutor executor) {
        if (data.isEmpty())
            return data;

        if (getBatchLoadSelection() != null && executor instanceof IOrmTemplate) {
            ((IOrmTemplate) executor).batchLoadSelection(data, getBatchLoadSelection());
        }

        if (getRowType() != null) {
            IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(getRowType().getRawClass());
            data = data.stream().map(item -> {
                if (item instanceof Map) {
                    return BeanRowMapper.newBean(beanModel, (Map<String, Object>) item, false);
                } else {
                    return BeanTool.buildBean(item, getRowType());
                }
            }).collect(Collectors.toList());
        }

        return data;
    }

    protected Object processSingleResult(Object data, ISqlExecutor executor) {
        if (data == null)
            return null;

        if (getBatchLoadSelection() != null && executor instanceof IOrmTemplate) {
            ((IOrmTemplate) executor).batchLoadSelection(Collections.singleton(data), getBatchLoadSelection());
        }

        if (getRowType() != null) {
            if (data instanceof Map) {
                IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(getRowType().getRawClass());
                return BeanRowMapper.newBean(beanModel, (Map<String, Object>) data, false);
            } else {
                return BeanTool.buildBean(data, getRowType());
            }
        }
        return data;
    }

    protected IRowMapper buildRowMapper(ISqlExecutor executor, String querySpace, IEvalScope scope) {
        SmartRowMapper rowMapper;
        if (DaoConstants.SQL_TYPE_SQL.equals(getType())) {
            rowMapper = isColNameCamelCase() ?  SmartRowMapper.CASE_INSENSITIVE : SmartRowMapper.CAMEL_CASE;
        } else {
            // eql语法时列名就是指定的属性名，不需要作camelCase转换
            rowMapper = SmartRowMapper.INSTANCE;
        }
        scope.setLocalValue(OrmConstants.PARAM_ROW_MAPPER, rowMapper);

        if (hasFields()) {
            IDialect dialect = executor.getDialectForQuerySpace(querySpace);
            IFieldMapper colMapper = buildColMapper(dialect);
            scope.setLocalValue(OrmConstants.PARAM_COL_MAPPER, colMapper);
            rowMapper = new SmartRowMapper(new ColRowMapper<>(rowMapper, colMapper));
            scope.setLocalValue(OrmConstants.PARAM_ROW_MAPPER, rowMapper);
        }

        if (getBuildRowMapper() != null) {
            Object result = getBuildRowMapper().invoke(scope);
            if (result instanceof IRowMapper) {
                return ((IRowMapper) result);
            }
        }
        return rowMapper;
    }

    private IFieldMapper buildColMapper(IDialect dialect) {
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
        return colMapper;
    }
}
