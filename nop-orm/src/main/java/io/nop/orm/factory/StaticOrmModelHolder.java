package io.nop.orm.factory;

import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.model.IOrmModel;
import io.nop.orm.persister.IPersistEnv;

public class StaticOrmModelHolder implements IOrmModelHolder{
    private final ILoadedOrmModel loadedOrmModel;

    public StaticOrmModelHolder(ILoadedOrmModel loadedOrmModel) {
        this.loadedOrmModel = loadedOrmModel;
    }

    public StaticOrmModelHolder(IPersistEnv env, IOrmModel ormModel){
        this.loadedOrmModel = new LoadedOrmModel(env, ormModel);
    }

    @Override
    public void clearCache() {

    }

    @Override
    public ILoadedOrmModel getOrmModel(IPersistEnv env) {
        return loadedOrmModel;
    }

    @Override
    public void clearCacheForTenant(String tenantId) {

    }

    @Override
    public void close() {

    }
}
