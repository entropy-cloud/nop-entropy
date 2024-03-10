/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.common;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.ITextResourceParser;

/**
 * 根据antlr定义文件生成的ParseTree解析器
 */
public interface IParseTreeParser extends ITextResourceParser<ParseTreeResult> {
    ParseTreeResult parseFromResource(IResource resource, boolean ignoreUnknown);

    ParseTreeResult parseFromText(SourceLocation loc, String text);
}