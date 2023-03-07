/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.ast._gen._Program;
import io.nop.xlang.scope.LexicalScope;

import java.util.List;

public class Program extends _Program implements IWithLexicalScope {
    private LexicalScope lexicalScope;

    // c:script的编译结果，变量域应该为parent scope
    private boolean shareScope;

    private boolean macroScript;

    public static Program valueOf(SourceLocation loc, String sourceType, List<XLangASTNode> body) {
        Guard.notNull(body, "body");
        Program prog = new Program();
        prog.setSourceType(sourceType);
        prog.setLocation(loc);
        prog.setBody(body);
        return prog;
    }

    public static Program script(SourceLocation loc, List<XLangASTNode> body) {
        return valueOf(loc, XLangConstants.SOURCE_TYPE_SCRIPT, body);
    }

    public boolean isMacroScript() {
        return macroScript;
    }

    public void setMacroScript(boolean macroScript) {
        this.macroScript = macroScript;
    }

    public boolean isShareScope() {
        return shareScope;
    }

    public void setShareScope(boolean shareScope) {
        this.shareScope = shareScope;
    }

    public boolean hasLexicalScope() {
        return lexicalScope != null;
    }

    @Override
    public LexicalScope getLexicalScope() {
        if (lexicalScope != null)
            return lexicalScope;
        XLangASTNode parent = getASTParent();
        if (parent != null)
            return parent.getLexicalScope();
        return null;
    }

    public void setLexicalScope(LexicalScope lexicalScope) {
        this.lexicalScope = lexicalScope;
    }

}