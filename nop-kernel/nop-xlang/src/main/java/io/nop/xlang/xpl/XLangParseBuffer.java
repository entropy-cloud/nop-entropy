/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.XLangASTNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_AST_NODE;
import static io.nop.core.CoreErrors.ARG_PARENT_NODE;
import static io.nop.core.CoreErrors.ERR_LANG_AST_NODE_NOT_ALLOW_MULTIPLE_PARENT;

public class XLangParseBuffer implements Appendable {

    private List<XLangASTNode> exprs;
    // 如果编译结果只有一个元素，exprs就会保持为null。这里是一个小优化策略。
    private Expression firstExpr;

    private SourceLocation loc;
    private StringBuilder sb;

    public void add(XLangASTNode expr) {
        if (expr == null)
            return;

        if (expr instanceof TextOutputExpression) {
            TextOutputExpression text = (TextOutputExpression) expr;
            appendText(text);
            return;
        }

        if (expr instanceof SequenceExpression) {
            SequenceExpression multi = (SequenceExpression) expr;
            tryMergeList(multi.getExpressions());
            if (!multi.getExpressions().isEmpty()) {
                endText();
                _append(expr);
            }
            return;
        }

        if (expr instanceof Program) {
            Program list = (Program) expr;
            if (list.isShareScope()) {
                for (XLangASTNode subExpr : list.getBody()) {
                    subExpr.setASTParent(null);
                    add(subExpr);
                }
            } else {
                tryMergeList(list.getBody());
                if (!list.getBody().isEmpty()) {
                    endText();
                    _append(expr);
                }
            }
            return;
        }

        endText();

        _append(expr);
    }

    private void tryMergeList(List<? extends XLangASTNode> list) {
        Iterator<? extends XLangASTNode> it = list.iterator();
        while (it.hasNext()) {
            XLangASTNode child = it.next();
            if (child instanceof TextOutputExpression) {
                child.setASTParent(null);
                appendText((TextOutputExpression) child);
                it.remove();
            } else {
                break;
            }
        }
    }

    private void appendText(TextOutputExpression text) {
        append(text.getLocation(), text.getText());
    }

    public void append(SourceLocation loc, String text) {
        if (this.loc == null)
            this.loc = loc;
        if (sb == null)
            sb = new StringBuilder();
        sb.append(text);
    }

    @Override
    public XLangParseBuffer append(CharSequence csq) {
        if (sb == null)
            sb = new StringBuilder();
        sb.append(csq);
        return this;
    }

    @Override
    public XLangParseBuffer append(CharSequence csq, int start, int end) {
        if (sb == null)
            sb = new StringBuilder();
        sb.append(csq, start, end);
        return this;
    }

    @Override
    public XLangParseBuffer append(char c) {
        if (sb == null)
            sb = new StringBuilder();
        sb.append(c);
        return this;
    }

    private void endText() {
        if (hasText()) {
            String s = sb.toString();

            TextOutputExpression text = TextOutputExpression.valueOf(loc, s);

            this.loc = null;
            sb.setLength(0);
            _append(text);
        }
    }

    private void _append(XLangASTNode expr) {
        if (expr.getASTParent() != null)
            throw new NopEvalException(ERR_LANG_AST_NODE_NOT_ALLOW_MULTIPLE_PARENT).param(ARG_AST_NODE, expr)
                    .param(ARG_PARENT_NODE, expr.getASTParent());

        if (exprs == null && firstExpr == null && expr instanceof Expression) {
            firstExpr = (Expression) expr;
        } else {
            if (exprs == null) {
                exprs = new ArrayList<>();
                exprs.add(firstExpr);
            }
            exprs.add(expr);
        }
    }

    private boolean hasText() {
        return sb != null && sb.length() > 0;
    }

    private SourceLocation getFirstLocation() {
        if (exprs != null) {
            for (XLangASTNode expr : exprs) {
                SourceLocation loc = expr.getLocation();
                if (loc != null)
                    return loc;
            }
        }
        return null;
    }

    public Expression getResult() {
        endText();
        if (exprs == null) {
            // 存在scope问题不能简化
            if (firstExpr instanceof VariableDeclaration)
                return Program.script(getFirstLocation(), Arrays.asList(firstExpr));
            return firstExpr;
        }
        // 此时exprs至少有一个元素
        return Program.script(getFirstLocation(), exprs);
    }

    public Expression getShareScopeResult() {
        endText();
        if (exprs == null) {
            return firstExpr;
        }
        // 此时exprs至少有一个元素
        Program prog = Program.script(getFirstLocation(), exprs);
        prog.setShareScope(true);
        return prog;
    }
}