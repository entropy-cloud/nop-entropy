/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.auth;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model.DataAuthModel;
import io.nop.auth.core.model.ObjDataAuthModel;
import io.nop.auth.core.model.RoleDataAuthModel;
import io.nop.auth.dao.entity.NopAuthRoleDataAuth;
import io.nop.biz.crud.BizFilterNodeGenerator;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.XNodeHelper;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.CacheEntryManagement;
import io.nop.core.resource.tenant.ResourceTenantManager;
import io.nop.dao.api.IDaoProvider;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_ID;
import static io.nop.auth.api.AuthApiErrors.ARG_USER_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_DATA_AUTH_CONFIG_PATH;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_USE_DATA_AUTH_TABLE;
import static io.nop.auth.service.NopAuthErrors.ARG_WHEN_CONFIG;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_AUTH_WHEN_CONFIG;

public class DefaultDataAuthChecker implements IDataAuthChecker {

    private CacheEntryManagement<DataAuthModel> modelCache;

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @PostConstruct
    public void init() {
    }

    public void lazyInit() {
        this.modelCache = ResourceTenantManager.instance().makeCacheEntry("data-auth-cache", isUseTenant(), null);
        GlobalCacheRegistry.instance().register(modelCache);
    }

    @PreDestroy
    public void destroy() {
        if (modelCache != null)
            GlobalCacheRegistry.instance().unregister(modelCache);
    }

    protected boolean isUseTenant() {
        if (ResourceTenantManager.instance().isEnableTenantResource())
            return true;
        return CFG_AUTH_USE_DATA_AUTH_TABLE.get() && daoProvider.daoFor(NopAuthRoleDataAuth.class).isUseTenant();
    }

    protected DataAuthModel loadDataAuthModel(String key) {
        String path = CFG_AUTH_DATA_AUTH_CONFIG_PATH.get();

        IResource resource = VirtualFileSystem.instance().getResource(path);
        DataAuthModel authModel;
        if (resource.exists()) {
            authModel = (DataAuthModel) new DslModelParser().parseFromResource(resource);
        } else {
            authModel = new DataAuthModel();
        }

        if (CFG_AUTH_USE_DATA_AUTH_TABLE.get()) {
            List<NopAuthRoleDataAuth> roleAuths = daoProvider.daoFor(NopAuthRoleDataAuth.class).findAll();
            mergeRoleAuth(authModel, roleAuths);
        }

        authModel.initCheckerFromFilter();
        authModel.sort();
        return authModel;
    }

    private void mergeRoleAuth(DataAuthModel authModel, List<NopAuthRoleDataAuth> roleAuths) {
        for (NopAuthRoleDataAuth auth : roleAuths) {
            String bizObj = auth.getBizObj();
            XNode filter = XNodeParser.instance().forFragments(true).parseFromText(null, auth.getFilterConfig());
            ObjDataAuthModel objAuth = authModel.getObj(bizObj);
            if (objAuth == null) {
                objAuth = new ObjDataAuthModel();
                objAuth.setName(bizObj);
                authModel.addObj(objAuth);
            }

            IXNodeGenerator filterBuilder = new BizFilterNodeGenerator(filter);

            RoleDataAuthModel roleAuth = new RoleDataAuthModel();
            roleAuth.setId(auth.getSid());
            roleAuth.setRoleIds(ConvertHelper.toCsvSet(auth.getRoleIds()));
            roleAuth.setPriority(auth.getPriority());
            roleAuth.setFilter(filterBuilder);
            roleAuth.setWhen(buildWhen(auth));
            objAuth.addRoleAuth(roleAuth);
        }
    }

    protected IEvalPredicate buildWhen(NopAuthRoleDataAuth auth) {
        if (StringHelper.isEmpty(auth.getWhenConfig()))
            return null;

        String whenConfig = auth.getWhenConfig();
        XNode node;
        if (!whenConfig.contains("<")) {
            // 认为是标签名
            if (!StringHelper.startsWithNamespace(whenConfig, AuthCoreConstants.NS_BIZ)
                    || !StringHelper.isValidXmlName(whenConfig)) {
                throw new NopException(ERR_AUTH_INVALID_AUTH_WHEN_CONFIG)
                        .param(ARG_ID, auth.get_id())
                        .param(ARG_WHEN_CONFIG, whenConfig);
            }
            node = XNode.make(whenConfig);
        } else {
            node = XNodeParser.instance().parseFromText(null, whenConfig);
            XNodeHelper.checkSafeXpl(node, AuthCoreConstants.NS_BIZ, false);
        }
        return XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .loadLib(null, AuthCoreConstants.NS_BIZ, AuthCoreConstants.LIB_PATH_BIZ_WHEN)
                .compileTag(node);
    }

    public void clearCache() {
        modelCache.clear();
    }

    private DataAuthModel getAuthModel() {
        return modelCache.getObject(true, this::loadDataAuthModel, CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT.get());
    }

    protected IEvalScope newEvalScope(ObjDataAuthModel objAuth, String action, Object entity, ISecurityContext context) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(AuthCoreConstants.VAR_AUTH_OBJ_NAME, objAuth.getName());
        scope.setLocalValue(AuthCoreConstants.VAR_ACTION, action);
        scope.setLocalValue(AuthCoreConstants.VAR_OBJ_AUTH_MODEL, objAuth);
        if (entity != null) {
            scope.setLocalValue(AuthCoreConstants.VAR_ENTITY, entity);
        }
        scope.setLocalValue(AuthCoreConstants.VAR_USER_CONTEXT, context.getUserContext());
        scope.setLocalValue(CoreConstants.VAR_SVC_CTX, context);
        return scope;
    }

    @Override
    public boolean isPermitted(String bizObj, String action, Object entity, ISecurityContext context) {
        DataAuthModel authModel = getAuthModel();
        ObjDataAuthModel objAuth = authModel.getObj(bizObj);
        if (objAuth == null)
            return true;
        IEvalScope scope = newEvalScope(objAuth, action, entity, context);
        Set<String> roleIds = authModel.decideDynamicRoles(objAuth, scope);
        RoleDataAuthModel roleAuth = objAuth.getRoleAuth(scope, roleIds, context);
        // 缺省总是假定没有权限
        if (roleAuth == null)
            return false;

        if (roleAuth.getCheck() != null) {
            return roleAuth.getCheck().passConditions(scope);
        }
        return true;
    }

    @Override
    public ITreeBean getFilter(String bizObj, String action, ISecurityContext context) {
        DataAuthModel authModel = getAuthModel();
        ObjDataAuthModel objAuth = authModel.getObj(bizObj);
        if (objAuth == null)
            return null;
        IEvalScope scope = newEvalScope(objAuth, action, null, context);
        Set<String> roleIds = authModel.decideDynamicRoles(objAuth, scope);
        RoleDataAuthModel roleAuth = objAuth.getRoleAuth(scope, roleIds, context);
        if (roleAuth == null)
            throw new NopException(ERR_AUTH_NO_DATA_AUTH)
                    .source(objAuth).param(ARG_BIZ_OBJ_NAME, bizObj)
                    .param(ARG_USER_NAME, context.getUserContext().getUserName());

        XNode filter = roleAuth.generateFilter(scope);
        if (filter == null || !filter.hasChild())
            return null;

        return filter;
    }
}