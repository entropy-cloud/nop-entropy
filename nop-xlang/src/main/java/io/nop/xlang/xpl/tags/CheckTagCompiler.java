package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

public class CheckTagCompiler implements IXplTagCompiler {
    public static final CheckTagCompiler INSTANCE = new CheckTagCompiler();

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        Expression throwExpr = ThrowTagCompiler.INSTANCE.parseTag(node, cp, scope);

        Expression test = new FilterBeanExpressionCompiler(cp, scope).compilePredicate(node);
        IfStatement ifStm = new IfStatement();
        ifStm.setLocation(node.getLocation());
        ifStm.setTest(XLangASTBuilder.not(node.getLocation(), test));
        ifStm.setConsequent(throwExpr);
        return ifStm;
    }
}
