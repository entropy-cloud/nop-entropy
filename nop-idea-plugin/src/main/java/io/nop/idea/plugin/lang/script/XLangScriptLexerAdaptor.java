/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script;

import io.nop.xlang.parse.antlr.XLangLexer;
import org.antlr.intellij.adaptor.lexer.ANTLRLexerAdaptor;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class XLangScriptLexerAdaptor extends ANTLRLexerAdaptor {

    public XLangScriptLexerAdaptor() {
        super(XLangScriptLanguage.INSTANCE, new XLangLexer(null));
    }
}
