package io.nop.xlang.ast.trans;

import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangASTOptimizer;

public class XLangASTTransformer {
    public static Expression replaceIdentifier(Expression ast, String name, Object value) {
        return (Expression) new XLangASTOptimizer<>() {
            @Override
            public XLangASTNode optimizeIdentifier(Identifier node, Object context) {
                if (node.getName().equals(name)) {
                    if (value instanceof Expression)
                        return ((Expression) value).deepClone();
                    return Literal.valueOf(node.getLocation(), value);
                }
                return node;
            }
        }.optimize(ast, null);
    }
}