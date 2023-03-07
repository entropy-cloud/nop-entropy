/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.exec.ScopeValues;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.Arrays;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_SCRIPT_NOT_ALLOW_CHILD;
import static io.nop.xlang.xpl.XplConstants.LANG_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrLiteral;

public class MacroScriptTagCompiler implements IXplTagCompiler {
    public static final MacroScriptTagCompiler INSTANCE = new MacroScriptTagCompiler();

    static final List<String> ATTR_NAMES = Arrays.asList(LANG_NAME);

    // tell cpd to start ignoring code - CPD-OFF
    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        Literal literal = getAttrLiteral(node, LANG_NAME, cp, scope);
        if (node.hasChild())
            throw new NopEvalException(ERR_XPL_SCRIPT_NOT_ALLOW_CHILD).param(ARG_NODE, node);
        String source = node.contentText();
        String lang = literal == null ? null : literal.getStringValue();
        if (StringHelper.isEmpty(lang)) {
            Program prog = cp.parseFullExpr(node.content().getLocation(), source, scope);
            prog.setMacroScript(true);

            IExecutableExpression executable = cp.buildExecutable(prog, false, scope);
            ScopeValues scopeValues = (ScopeValues) XLang.execute(executable, scope);
            List<LocalVarDeclaration> varDecls = scopeValues.getVarDecls();
            List<Object> values = scopeValues.getValues();
            for (int i = 0, n = varDecls.size(); i < n; i++) {
                LocalVarDeclaration var = varDecls.get(i);
                Runnable cleanup = XplParseHelper.registerMacroVar(scope, var.getLocation(), var.getIdentifierName(),
                        var.getResolvedType(), values.get(i));
                scope.addBlockCleanupAction(cleanup);
            }
            return null;
        }

        throw new UnsupportedOperationException("not supported");
    }
    // resume CPD analysis - CPD-ON
}
