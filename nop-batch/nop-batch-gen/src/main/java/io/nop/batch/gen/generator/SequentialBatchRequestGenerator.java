/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen.generator;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRequestGenerator;
import io.nop.batch.gen.BatchGenConstants;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.query.FilterBeanEvaluator;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.List;
import java.util.Map;

public class SequentialBatchRequestGenerator<S, R> implements IBatchRequestGenerator<S, R, IBatchChunkContext> {
    private final List<BatchGenState> subCases;
    private final IBatchGenContext genContext;

    private int subIndex;
    private IEvalScope scope;

    public SequentialBatchRequestGenerator(List<BatchGenState> subCases, IBatchGenContext genContext) {
        this.subCases = subCases;
        this.genContext = genContext;
        this.scope = genContext.getEvalScope().newChildScope();
    }

    @Override
    public S nextRequest(IBatchChunkContext context) {
        scope.setLocalValue(null, BatchGenConstants.VAR_CHUNK_CONTEXT, context);
        do {
            if (subIndex >= subCases.size()) {
                return null;
            }

            BatchGenState subCase = subCases.get(subIndex);
            if (!subCase.hasNext())
                return null;

            subIndex++;

            if (!shouldAccept(subCase, genContext)) {
                // 过滤条件返回false，则跳过本条记录
                subCase.next(genContext, false);
            } else {
                return (S) subCase.next(genContext, true);
            }
        } while (true);
    }

    boolean shouldAccept(BatchGenState subCase, IBatchGenContext context) {
        if (subCase.getWhen() != null) {
            Boolean b = new FilterBeanEvaluator().visit(subCase.getWhen(), context.getEvalScope());
            return Boolean.TRUE.equals(b);
        }
        return false;
    }

    @Override
    public void onResponse(R response, IBatchChunkContext context) {
        int lastIndex = subIndex - 1;
        BatchGenState subCase = subCases.get(lastIndex);
        Map<String, Object> outputVars = subCase.getOutputVars();
        if (outputVars != null) {
            // 根据响应数据生成输出变量，更新上下文环境
            Map<String, Object> vars = (Map<String, Object>) genContext.getProducer().produce(outputVars,
                    PredefinedGenericTypes.MAP_STRING_ANY_TYPE, genContext);
            scope.setLocalValues(null, vars);
        }
    }
}