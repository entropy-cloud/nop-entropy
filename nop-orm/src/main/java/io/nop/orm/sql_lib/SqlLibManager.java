/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.sql_lib.proxy.SqlLibInvoker;
import io.nop.xlang.xdsl.DslModelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static io.nop.orm.OrmErrors.ARG_PATH;
import static io.nop.orm.OrmErrors.ARG_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_INVALID_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_UNKNOWN_SQL_ITEM;
import static io.nop.orm.OrmErrors.ERR_SQL_UNKNOWN_LIB_PATH;

public class SqlLibManager implements ISqlLibManager {
    static final Logger LOG = LoggerFactory.getLogger(SqlLibManager.class);

    private IJdbcTemplate jdbcTemplate;

    private IOrmTemplate ormTemplate;

    private ICancellable cancellable;

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @PostConstruct
    public void init() {
        LOG.info("SqlLibManager.init");
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(OrmConstants.MODEL_TYPE_SQL_LIB);

        config.loader(OrmConstants.FILE_TYPE_SQL_LIB,
                path -> new DslModelParser(OrmConstants.XDSL_SCHEMA_SQL_LIB).parseFromVirtualPath(path));
        cancellable = ResourceComponentManager.instance().registerComponentModelConfig(config);
    }

    @PreDestroy
    public void destroy() {
        LOG.info("SqlLibManager.destroy");
        if (cancellable != null) {
            cancellable.cancel();
            cancellable = null;
        }
    }

    @Override
    public SQL buildSql(String sqlName, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlName);
        return item.buildSql(context);
    }

    @Override
    public Object invoke(String sqlName, LongRangeBean range, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlName);
        return item.invoke(getExecutor(item.getType()), range, context);
    }

    public Object invoke(String sqlLibPath, String sqlName, LongRangeBean range, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlLibPath, sqlName);
        return item.invoke(getExecutor(item.getType()), range, context);
    }

    @Override
    public SqlItemModel getSqlItemModel(String sqlName) {
        int pos = sqlName.lastIndexOf('.');
        if (pos < 0)
            throw new NopException(ERR_SQL_LIB_INVALID_SQL_NAME).param(ARG_SQL_NAME, sqlName);

        String className = sqlName.substring(0, pos);
        String sqlItemName = sqlName.substring(pos + 1);

        String sqlLibPath = buildSqlLibPathFromClassName(className);

        SqlItemModel item = getSqlItemModel(sqlLibPath, sqlItemName);
        return item;
    }

    private String buildSqlLibPathFromClassName(String className) {
        return "module:/sql/" + className + ".sql-lib.xml";
    }

    private String getSqlLibPath(Class<?> clazz) {
        SqlLibMapper mapper = clazz.getAnnotation(SqlLibMapper.class);
        if (mapper != null) {
            String path = mapper.value();
            if (path.length() > 0)
                return path;
        }
        return buildSqlLibPathFromClassName(clazz.getName());
    }

    public SqlItemModel getSqlItemModel(String sqlLibPath, String sqlItemName) {
        SqlLibModel libModel = (SqlLibModel) ResourceComponentManager.instance().loadComponentModel(sqlLibPath);
        if (libModel == null)
            throw new NopException(ERR_SQL_UNKNOWN_LIB_PATH).param(ARG_PATH, sqlLibPath);
        SqlItemModel item = libModel.getSql(sqlItemName);
        if (item == null)
            throw new NopException(ERR_SQL_LIB_UNKNOWN_SQL_ITEM).param(ARG_PATH, sqlLibPath).param(ARG_SQL_NAME,
                    sqlItemName);
        return item;
    }

    private ISqlExecutor getExecutor(String type) {
        if (OrmConstants.SQL_TYPE_EQL.equals(type) || OrmConstants.SQL_TYPE_QUERY.equals(type))
            return ormTemplate;

        return jdbcTemplate;
    }

    @Override
    public <T> T createProxy(Class<T> proxyClass) {
        String sqlLibPath = getSqlLibPath(proxyClass);
        checkProxyMethods(sqlLibPath, proxyClass);
        SqlLibInvoker invoker = new SqlLibInvoker(this, sqlLibPath);
        return (T) ReflectionManager.instance().newProxyInstance(new Class[]{proxyClass}, invoker);
    }

    void checkProxyMethods(String sqlLibPath, Class<?> proxyClass) {
        Method[] methods = proxyClass.getMethods();
        for (Method method : methods) {
            if (ReflectionHelper.isObjectMethod(method))
                continue;

            if (Modifier.isStatic(method.getModifiers()))
                continue;

            if (method.isDefault())
                continue;

            getSqlItemModel(sqlLibPath, method.getName());
        }
    }
}
