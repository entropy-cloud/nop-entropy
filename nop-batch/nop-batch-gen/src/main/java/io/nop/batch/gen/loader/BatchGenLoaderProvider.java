/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.loader;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.common.AbstractBatchResourceHandler;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.batch.gen.generator.BatchGenContextImpl;
import io.nop.batch.gen.generator.BatchGenState;
import io.nop.batch.gen.model.BatchGenModel;
import io.nop.batch.gen.model.BatchGenModelParser;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;

import java.util.ArrayList;
import java.util.List;

public class BatchGenLoaderProvider<C> extends AbstractBatchResourceHandler
        implements IBatchLoaderProvider<Object> {

    /**
     * 总共生成多少条记录
     */
    private IEvalAction totalCountExpr;
    private ValueResolverCompilerRegistry registry = ValueResolverCompilerRegistry.DEFAULT;

    public void setValueResolverCompilerRegistry(ValueResolverCompilerRegistry registry) {
        this.registry = registry;
    }

    public void setTotalCount(IEvalAction totalCountExpr) {
        this.totalCountExpr = totalCountExpr;
    }

    static class LoaderState {
        BatchGenModel genModel;
        BatchGenState genState;
        IBatchGenContext genContext;
    }

    @Override
    public IBatchLoader<Object> setup(IBatchTaskContext context) {
        long totalCount = ConvertHelper.toPrimitiveLong(totalCountExpr.invoke(context), NopException::new);

        LoaderState state = new LoaderState();
        state.genModel = new BatchGenModelParser().parseFromResource(getResource(context));
        state.genState = new BatchGenState(state.genModel, totalCount);
        state.genContext = new BatchGenContextImpl(registry);
        return (batchSize, ctx) -> {
            synchronized (state) {
                return load(batchSize, ctx, state);
            }
        };
    }

    List<Object> load(int batchSize, IBatchChunkContext context, LoaderState state) {
        List<Object> ret = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            if (!state.genState.hasNext())
                break;

            Object item = state.genState.next(state.genContext, true);
            ret.add(item);
        }
        return ret;
    }
}