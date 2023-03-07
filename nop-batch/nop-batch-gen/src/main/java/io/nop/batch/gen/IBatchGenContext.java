/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen;

import io.nop.batch.gen.model.IBatchTemplateBasedProducer;
import io.nop.core.context.IEvalContext;

public interface IBatchGenContext extends IEvalContext {
    IBatchTemplateBasedProducer getProducer();
}
