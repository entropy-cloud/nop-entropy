/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.tool.loader;

import org.antlr.v4.Tool;
import org.antlr.v4.tool.ErrorManager;
import org.antlr.v4.tool.ErrorType;

public class CustomErrorManager extends ErrorManager {
    public CustomErrorManager(Tool tool) {
        super(tool);
    }

    @Override
    public void grammarError(ErrorType etype, String fileName, org.antlr.runtime.Token token, Object... args) {
        // 忽略tokens文件找不到的错误。GrammarLoader中直接解析Lexer来获取tokenSource
        if (etype == ErrorType.CANNOT_FIND_TOKENS_FILE_REFD_IN_GRAMMAR)
            return;

        super.grammarError(etype, fileName, token, args);
    }
}
