/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.input;

import io.nop.core.context.IEvalContext;
import io.nop.record.model.RecordFileMeta;

public interface IRecordInputContext extends IEvalContext {
    RecordFileMeta getMeta();
}
