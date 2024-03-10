/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.compile;

import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.persister.IPersistEnv;

public class EqlCompileContext implements ISqlCompileContext {
    private final IPersistEnv env;
    private final boolean disableLogicalDelete;

    private final IEqlAstTransformer astTransformer;

    private final boolean allowUnderscoreName;

    private final boolean enableFilter;

    public EqlCompileContext(IPersistEnv env, boolean disableLogicalDelete, IEqlAstTransformer astTransformer,
                             boolean allowUnderscoreName, boolean enableFilter) {
        this.env = env;
        this.disableLogicalDelete = disableLogicalDelete;
        this.astTransformer = astTransformer;
        this.allowUnderscoreName = allowUnderscoreName;
        this.enableFilter = enableFilter;
    }

    public boolean isDisableLogicalDelete() {
        return disableLogicalDelete;
    }

    @Override
    public boolean isAllowUnderscoreName() {
        return allowUnderscoreName;
    }

    @Override
    public boolean isEnableFilter() {
        return enableFilter;
    }

    @Override
    public IEqlAstTransformer getAstTransformer() {
        return astTransformer;
    }

    @Override
    public IDialect getDialectForQuerySpace(String querySpace) {
        return env.getDialectForQuerySpace(querySpace);
    }

    @Override
    public EntityTableMeta resolveEntityTableMeta(String entityName) {
        return env.resolveEntityTableMeta(entityName, allowUnderscoreName);
    }

    @Override
    public IAliasGenerator getAliasGenerator() {
        return new SeqAliasGenerator();
    }
}
