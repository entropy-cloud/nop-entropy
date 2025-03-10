/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.generator;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRequestGenerator;
import io.nop.batch.gen.BatchGenConstants;
import io.nop.batch.gen.IBatchGenContext;
import io.nop.core.model.query.FilterBeanEvaluator;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.List;
import java.util.Map;

public class SequentialBatchRequestGenerator<S, R> implements IBatchRequestGenerator<S, R> {
    private final List<BatchGenState> subCases;

    private int subIndex;
    private IBatchGenContext subContext;

    public SequentialBatchRequestGenerator(List<BatchGenState> subCases, IBatchGenContext genContext) {
        this.subCases = subCases;
        this.subContext = genContext.newSubContext();
    }

    @Override
    public S nextRequest(IBatchChunkContext context) {
        subContext.getEvalScope().setLocalValue(null, BatchGenConstants.VAR_BATCH_CHUNK_CONTEXT, context);
        do {
            if (subIndex >= subCases.size()) {
                return null;
            }

            BatchGenState subCase = subCases.get(subIndex);
            if (!subCase.hasNext())
                return null;

            subIndex++;

            if (!shouldAccept(subCase, subContext)) {
                // 过滤条件返回false，则跳过本条记录
                subCase.next(subContext, false);
            } else {
                return (S) subCase.next(subContext, true);
            }
        } while (true);
    }

    boolean shouldAccept(BatchGenState subCase, IBatchGenContext context) {
        if (subCase.getWhen() != null) {
            Boolean b = new FilterBeanEvaluator().visit(subCase.getWhen(), context.getEvalScope());
            return Boolean.TRUE.equals(b);
        }
        return true;
    }

    @Override
    public void onResponse(R response, IBatchChunkContext context) {
        int lastIndex = subIndex - 1;
        BatchGenState subCase = subCases.get(lastIndex);
        Map<String, Object> outputVars = subCase.getOutputVars();
        if (outputVars != null) {
            subContext.getEvalScope().setLocalValue(BatchGenConstants.VAR_CHUNK_RESPONSE, response);
            // 根据响应数据生成输出变量，更新上下文环境
            Map<String, Object> vars = (Map<String, Object>) subContext.getProducer().produce(outputVars,
                    PredefinedGenericTypes.MAP_STRING_ANY_TYPE, subContext);
            subContext.getEvalScope().setLocalValues(null, vars);
        }
    }
}