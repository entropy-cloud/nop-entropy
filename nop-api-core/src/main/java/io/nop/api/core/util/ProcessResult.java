/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

/**
 * 在简单的责任链处理过程中，每个步骤可能决定是否已经处理完毕，不再向后处理
 */
public enum ProcessResult {
    CONTINUE,
    STOP
}