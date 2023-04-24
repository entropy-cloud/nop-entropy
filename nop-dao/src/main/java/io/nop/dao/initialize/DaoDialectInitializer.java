/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.initialize;

import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.dao.dialect.DialectManager;

public class DaoDialectInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT;
    }

    @Override
    public void initialize() {
//        ComponentModelConfig config = new ComponentModelConfig();
//        config.modelType("dialect");
//
//        config.loader("dialect.xml", this::buildDialect);
//        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));

        DialectManager.instance().loadDialectSelectors();
        // JdbcHelper.loadAllDrivers();
    }

//    IDialect buildDialect(String path) {
//        DialectModel dialectModel = (DialectModel) new DslModelParser(DaoConstants.XDSL_SCHEMA_DIALECT)
//                .parseFromVirtualPath(path);
//        dialectModel.freeze(true);
//
//        String className = dialectModel.getClassName();
//        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
//        return (IDialect) classModel.getConstructor(1).call1(null, dialectModel, DisabledEvalScope.INSTANCE);
//    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}
