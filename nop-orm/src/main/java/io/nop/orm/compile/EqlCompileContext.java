/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    public EqlCompileContext(IPersistEnv env, boolean disableLogicalDelete, IEqlAstTransformer astTransformer) {
        this.env = env;
        this.disableLogicalDelete = disableLogicalDelete;
        this.astTransformer = astTransformer;
    }

    public boolean isDisableLogicalDelete() {
        return disableLogicalDelete;
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
        return env.resolveEntityTableMeta(entityName);
    }

    @Override
    public IAliasGenerator newAliasGenerator() {
        return new SeqAliasGenerator();
    }
}
