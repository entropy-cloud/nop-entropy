/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.model;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.delta.JsonMerger;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;

import java.util.Map;

public class BatchGenModelParser extends AbstractResourceParser<BatchGenModel> {

    @Override
    protected BatchGenModel doParseResource(IResource resource) {
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setIgnoreUnknownValueResolver(false);
        options.setEvalContext(EvalExprProvider.newEvalScope());
        BatchGenModel model = JsonTool.loadDeltaBean(resource, BatchGenModel.class, options);

        for (BatchGenCaseModel subCase : model.getSubCases()) {
            mergeWithParent(subCase, model);
        }
        return model;
    }

    void mergeWithParent(BatchGenCaseModel subCase, IBatchGenCaseModel parent) {
        if (subCase.isInheritParent()) {
            Map<String, Object> template = mergeMap(parent.getMergedTemplate(), subCase.getTemplate());
            subCase.setMergedTemplate(template);

            Map<String, Object> outputVars = mergeMap(parent.getMergedOutputVars(), subCase.getOutputVars());
            subCase.setMergedOutputVars(outputVars);
        } else {
            subCase.setMergedTemplate(subCase.getTemplate());
            subCase.setMergedOutputVars(subCase.getOutputVars());
        }

        if (subCase.getSubCases() != null) {
            subCase.getSubCases().forEach(sub -> {
                mergeWithParent(sub, subCase);
            });
        }
    }

    Map<String, Object> mergeMap(Map<String, Object> m1, Map<String, Object> m2) {
        if (m1 == null || m1.isEmpty())
            return m2;
        if (m2 == null || m1.isEmpty())
            return m1;

        return (Map<String, Object>) JsonMerger.instance().merge(m1, m2);
    }
}
