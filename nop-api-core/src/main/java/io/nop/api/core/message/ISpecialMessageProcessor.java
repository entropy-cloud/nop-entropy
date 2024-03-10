/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

public interface ISpecialMessageProcessor {
    /**
     * 如果是watermark等特殊消息，则由此函数进行处理，返回true。表示不再向下游传递
     *
     * @param message 消息对象
     * @return true表示已经被处理，不需要再向下游传递
     */
    boolean process(Object message);
}
