/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.util.ReflectionHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.sql.SQL;
import io.nop.core.module.ModuleManager;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmErrors;
import io.nop.orm.sql_lib.proxy.SqlLibInvoker;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmConfigs.CFG_CHECK_ALL_SQL_LIB_WHEN_INIT;
import static io.nop.orm.OrmErrors.ARG_PATH;
import static io.nop.orm.OrmErrors.ARG_PERMISSION;
import static io.nop.orm.OrmErrors.ARG_ROLES;
import static io.nop.orm.OrmErrors.ARG_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_SQL_LIB_INVALID_SQL_NAME;
import static io.nop.orm.OrmErrors.ERR_SQL_UNKNOWN_LIB_PATH;

public class SqlLibManager implements ISqlLibManager {
    static final Logger LOG = LoggerFactory.getLogger(SqlLibManager.class);

    private IJdbcTemplate jdbcTemplate;

    private IOrmTemplate ormTemplate;

    private ICancellable cancellable;

    private IDaoProvider daoProvider;

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @PostConstruct
    public void init() {
        LOG.info("SqlLibManager.init");
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(OrmConstants.MODEL_TYPE_SQL_LIB);

        config.loader(OrmConstants.FILE_TYPE_SQL_LIB,
                new ComponentModelConfig.LoaderConfig(null, null, path -> new DslModelParser(OrmConstants.XDSL_SCHEMA_SQL_LIB).parseFromVirtualPath(path)));
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

    public void delayInit() {
        if (CFG_CHECK_ALL_SQL_LIB_WHEN_INIT.get())
            checkAllLibValid();
    }

    public void checkAllLibValid() {
        List<IResource> resources = ModuleManager.instance().findModuleResources(false, "/sql", OrmConstants.FILE_TYPE_SQL_LIB);
        resources.forEach(resource -> checkLibValid(resource.getStdPath()));
    }

    @Override
    public QueryBean buildQueryBean(String sqlName, IEvalContext context) {
        QuerySqlItemModel item = (QuerySqlItemModel) getSqlItemModel(sqlName);
        IEvalScope scope = context.getEvalScope();
        ValueWithLocation sqlLibVl = scope.recordValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL);
        try {
            scope.setLocalValue(OrmConstants.PARAM_SQL_LIB_MODEL, item.getSqlLibModel());
            return item.buildQueryBean(context);
        } finally {
            scope.restoreValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL, sqlLibVl);
        }
    }

    @Override
    public SQL buildSql(String sqlName, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlName);
        IEvalScope scope = context.getEvalScope();
        ValueWithLocation sqlLibVl = scope.recordValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL);
        try {
            scope.setLocalValue(OrmConstants.PARAM_SQL_LIB_MODEL, item.getSqlLibModel());
            return item.buildSql(context);
        } finally {
            scope.restoreValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL, sqlLibVl);
        }
    }

    @Override
    public Object invoke(String sqlName, LongRangeBean range, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlName);
        return doInvoke(item, range, context);
    }

    Object doInvoke(SqlItemModel item, LongRangeBean range, IEvalContext context) {
        checkAuth(item, context);
        IEvalScope scope = context.getEvalScope();
        ValueWithLocation sqlLibVl = scope.recordValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL);
        try {
            scope.setLocalValue(OrmConstants.PARAM_SQL_LIB_MODEL, item.getSqlLibModel());
            return item.invoke(daoProvider, getExecutor(item.getType()), range, context);
        } finally {
            scope.restoreValueLocation(OrmConstants.PARAM_SQL_LIB_MODEL, sqlLibVl);
        }
    }

    void checkAuth(SqlItemModel item, IEvalContext context) {
        ActionAuthMeta auth = item.getAuth();
        if (auth == null)
            return;

        IServiceContext ctx = IServiceContext.fromEvalContext(context);
        if (ctx == null)
            return;

        IUserContext userContext = ctx.getUserContext();
        if (userContext == null)
            return;

        IActionAuthChecker checker = ctx.getActionAuthChecker();
        if (checker == null)
            return;

        if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
            if (userContext.isUserInAnyRole(auth.getRoles()))
                return;
        }

        if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
            if (checker.isPermissionSetSatisfied(auth.getPermissions(), ctx))
                return;
        }

        throw new NopException(OrmErrors.ERR_ORM_NO_PERMISSION_FOR_SQL)
                .param(ARG_PERMISSION, auth.getPermissions())
                .param(ARG_ROLES, auth.getRoles())
                .param(ARG_SQL_NAME, item.getName());
    }

    public Object invoke(String sqlLibPath, String sqlItemName, LongRangeBean range, IEvalContext context) {
        SqlItemModel item = getSqlItemModel(sqlLibPath, sqlItemName);
        return doInvoke(item, range, context);
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

    private SqlLibModel requireSqlLib(String sqlLibPath) {
        SqlLibModel libModel = (SqlLibModel) ResourceComponentManager.instance().loadComponentModel(sqlLibPath);
        if (libModel == null)
            throw new NopException(ERR_SQL_UNKNOWN_LIB_PATH).param(ARG_PATH, sqlLibPath);
        return libModel;
    }

    public SqlItemModel getSqlItemModel(String sqlLibPath, String sqlItemName) {
        SqlLibModel libModel = requireSqlLib(sqlLibPath);
        SqlItemModel item = libModel.requireSql(sqlItemName);
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

    @Override
    public void checkLibValid(String sqlLibPath) {
        SqlLibModel sqlLibModel = requireSqlLib(sqlLibPath);
        for (SqlItemModel item : sqlLibModel.getSqls()) {
            if (item.getValidateInput() != null) {
                IEvalScope scope = XLang.newEvalScope();
                Object input = item.getValidateInput().invoke(scope);
                if (input != null) {
                    if (input instanceof Map) {
                        scope.setLocalValues((Map<String, Object>) input);
                    } else {
                        throw new IllegalArgumentException("nop.err.orm.validate-input-result-not-map");
                    }
                }

                scope.setLocalValue(OrmConstants.PARAM_SQL_LIB_MODEL, item.getSqlLibModel());
                SQL sql = item.buildSql(scope);
                if (item.getType().equals(OrmConstants.SQL_TYPE_EQL)) {
                    ormTemplate.getSessionFactory().compileSql(sql.getName(), sql.getText(), item.isDisableLogicalDelete());
                }
            }
        }
    }
}
