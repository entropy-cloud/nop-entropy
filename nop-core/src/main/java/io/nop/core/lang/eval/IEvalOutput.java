/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.eval;

import io.nop.api.core.util.SourceLocation;

/**
 * 支持sql/json/text/xml等多种文本转义模式
 */
public interface IEvalOutput {
    /**
     * 输出注释
     *
     * @param comment
     */
    void comment(String comment);

    void value(SourceLocation loc, Object value);

    void text(SourceLocation loc, String text);
}