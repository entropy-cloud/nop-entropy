package io.nop.orm.factory;

import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.persister.IPersistEnv;

public interface IOrmModelHolder extends AutoCloseable {
    ILoadedOrmModel getOrmModel(IPersistEnv env);

    void clearCache();

    void clearCacheForTenant(String tenantId);

    void close();
}
