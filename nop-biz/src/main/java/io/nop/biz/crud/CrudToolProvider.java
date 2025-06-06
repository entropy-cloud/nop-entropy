package io.nop.biz.crud;

import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.xlang.xmeta.IObjMeta;
import jakarta.inject.Inject;

public class CrudToolProvider {
    private IDaoProvider daoProvider;
    private IBizObjectManager bizObjectManager;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    public OrmEntityCopier newOrmEntityCopier(IObjMeta objMeta) {
        return new OrmEntityCopier(daoProvider, bizObjectManager);
    }

    public ObjMetaBasedValidator newValidator(String bizObjName, IObjMeta objMeta, IServiceContext context, boolean checkWriteAuth) {
        return new ObjMetaBasedValidator(bizObjectManager, bizObjName, objMeta, context, checkWriteAuth);
    }

    public ObjMetaBasedFilterValidator newFilterValidator(IObjMeta objMeta) {
        return new ObjMetaBasedFilterValidator(objMeta);
    }
}