/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.generator;

import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.model.IBatchTemplateBasedProducer;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;

public class BatchGenContextImpl implements IBatchGenContext {
    private IEvalScope scope;
    private IBatchTemplateBasedProducer producer;

    public BatchGenContextImpl(ValueResolverCompilerRegistry registry) {
        scope = EvalExprProvider.newEvalScope();
        producer = new BatchTemplateBasedProducer(registry);
    }

    public BatchGenContextImpl(IEvalScope scope, IBatchTemplateBasedProducer producer) {
        this.scope = scope;
        this.producer = producer;
    }

    public IBatchGenContext newSubContext() {
        return new BatchGenContextImpl(scope.newChildScope(), producer);
    }

    @Override
    public IBatchTemplateBasedProducer getProducer() {
        return producer;
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    public void setEvalScope(IEvalScope scope) {
        this.scope = scope;
    }

    public void setProducer(IBatchTemplateBasedProducer producer) {
        this.producer = producer;
    }
}
