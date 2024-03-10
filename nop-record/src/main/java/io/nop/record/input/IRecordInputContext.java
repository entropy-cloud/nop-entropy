/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record.input;

import io.nop.core.context.IEvalContext;
import io.nop.record.model.RecordFileMeta;

public interface IRecordInputContext extends IEvalContext {
    RecordFileMeta getMeta();
}
