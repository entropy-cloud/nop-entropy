/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.loader;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.DslNodeLoader;

public class DialectModelLoader extends AbstractDslResourcePersister {
    public DialectModelLoader() {
        super(DaoConstants.XDSL_SCHEMA_DIALECT, null);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        return DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource, schemaPath, phase);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        if (obj instanceof IDialect)
            obj = ((IDialect) obj).getDialectModel();
        XNode node = DslModelHelper.dslModelToXNode(schemaPath, obj);
        node.saveToResource(resource, null);
    }

    @Override
    public IDialect loadObjectFromResource(IResource resource) {
        DialectModel dialectModel = (DialectModel) new DslModelParser(DaoConstants.XDSL_SCHEMA_DIALECT)
                .parseFromResource(resource);
        dialectModel.freeze(true);

        String className = dialectModel.getClassName();
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        return (IDialect) classModel.getConstructor(1).call1(null, dialectModel, DisabledEvalScope.INSTANCE);
    }
}
