package io.nop.dao.dialect.loader;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.dao.DaoConstants;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.xlang.xdsl.DslModelParser;

public class DialectModelLoader extends AbstractResourceParser<IDialect> {
    @Override
    protected IDialect doParseResource(IResource resource) {
        DialectModel dialectModel = (DialectModel) new DslModelParser(DaoConstants.XDSL_SCHEMA_DIALECT)
                .parseFromResource(resource);
        dialectModel.freeze(true);

        String className = dialectModel.getClassName();
        IClassModel classModel = ReflectionManager.instance().loadClassModel(className);
        return (IDialect) classModel.getConstructor(1).call1(null, dialectModel, DisabledEvalScope.INSTANCE);
    }
}
