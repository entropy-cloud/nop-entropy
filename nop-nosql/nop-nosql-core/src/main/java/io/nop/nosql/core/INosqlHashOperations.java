/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.nosql.core;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface INosqlHashOperations extends INosqlKeyValueOperations {

    CompletionStage<Map<String, Object>> getAllAsync();
}
