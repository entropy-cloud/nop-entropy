package io.nop.dyn.service.codegen;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.module.ModuleModel;
import io.nop.graphql.core.reflection.GraphQLBizModel;

public interface IDynCodeGenCacheHook {
    void prepareLoadModule(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope);

    void prepareUnloadModule(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope);

    void prepareBizObject(InMemoryCodeCache cache, GraphQLBizModel bizModel, ModuleModel module, IEvalScope scope);

    void prepareOrmModel(InMemoryCodeCache cache, ModuleModel module, IEvalScope scope);

    void prepareResource(InMemoryCodeCache cache, String path, IEvalScope scope);
}
