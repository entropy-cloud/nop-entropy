/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.model;

import io.nop.batch.gen.BatchGenConstants;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.xlang.xdsl.DslModelHelper;

public class BatchGenModelParser extends AbstractResourceParser<BatchGenModel> {

    @Override
    protected BatchGenModel doParseResource(IResource resource) {
        if (resource.getName().endsWith(BatchGenConstants.POSTFIX_BATCH_GEN_XLSX)) {
            BatchGenModel model = (BatchGenModel) DslModelHelper.newExcelModelLoader(
                    BatchGenConstants.XDSL_BATCH_GEN_IMP_PATH).parseFromResource(resource);
            model.init();
            return model;
        }

        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setIgnoreUnknownValueResolver(false);
        options.setEvalContext(EvalExprProvider.newEvalScope());
        BatchGenModel model = JsonTool.loadDeltaBean(resource, BatchGenModel.class, options);
        model.init();
        return model;
    }

}
