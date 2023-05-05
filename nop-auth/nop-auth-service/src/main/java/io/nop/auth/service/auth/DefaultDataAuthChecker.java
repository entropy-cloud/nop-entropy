package io.nop.auth.service.auth;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.TreeBean;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model.DataAuthModel;
import io.nop.auth.core.model.ObjDataAuthModel;
import io.nop.auth.core.model.RoleDataAuthModel;
import io.nop.auth.dao.entity.NopAuthRoleDataAuth;
import io.nop.biz.crud.BizFilterEvaluator;
import io.nop.biz.crud.BizFilterNodeGenerator;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.ResourceCacheEntry;
import io.nop.dao.api.IDaoProvider;
import io.nop.xlang.xdsl.DslModelParser;

import javax.inject.Inject;
import java.util.List;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_DATA_AUTH_CACHE_CHECK_CHANGED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_DATA_AUTH_CONFIG_PATH;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_USE_DATA_AUTH_TABLE;

public class DefaultDataAuthChecker implements IDataAuthChecker {

    private final ResourceCacheEntry<DataAuthModel> modelCache =
            new ResourceCacheEntry<>("data-auth");

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
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

            IEvalPredicate checker = ctx -> new BizFilterEvaluator((IServiceContext) ctx).testForEntity(filter.toTreeBean(), ctx.getEvalScope().getValue(AuthCoreConstants.VAR_ENTITY));
            IXNodeGenerator filterBuilder = new BizFilterNodeGenerator(filter);

            RoleDataAuthModel roleAuth = objAuth.getRoleAuth(auth.getRoleId());
            if (roleAuth == null) {
                roleAuth = new RoleDataAuthModel();
                roleAuth.setRoleId(auth.getRoleId());
                objAuth.addRoleAuth(roleAuth);
            }
            roleAuth.mergeChecker(checker);
            roleAuth.mergeFilter(filterBuilder);
        }
    }

    public void clearCache() {
        modelCache.clear();
    }

    private ObjDataAuthModel getObjAuth(String bizObj) {
        return modelCache.getObject(CFG_AUTH_DATA_AUTH_CACHE_CHECK_CHANGED.get(), this::loadDataAuthModel).getObj(bizObj);
    }

    @Override
    public boolean isPermitted(String bizObj, String action, Object entity, ISecurityContext context) {
        ObjDataAuthModel objAuth = getObjAuth(bizObj);
        if (objAuth == null)
            return true;
        return objAuth.isPermitted(action, entity, context);
    }

    @Override
    public TreeBean getFilter(String bizObj, String action, ISecurityContext context) {
        ObjDataAuthModel objAuth = getObjAuth(bizObj);
        if (objAuth == null)
            return null;
        return getObjAuth(bizObj).getFilter(action, context);
    }
}