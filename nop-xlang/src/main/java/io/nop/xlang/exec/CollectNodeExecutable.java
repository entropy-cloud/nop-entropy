/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectXNodeHandler;

import java.util.Collections;

import static io.nop.xlang.XLangErrors.ERR_EXEC_COLLECT_RESULT_NOT_SINGLE_NODE;

public class CollectNodeExecutable extends AbstractExecutable {
    private final IExecutableExpression bodyExpr;
    private final boolean singleNode;

    public CollectNodeExecutable(SourceLocation loc, IExecutableExpression bodyExpr, boolean singleNode) {
        super(loc);
        this.bodyExpr = bodyExpr;
        this.singleNode = singleNode;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("@collect:");
        bodyExpr.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        IEvalOutput oldOut = rt.getOut();
        CollectXNodeHandler out = new CollectXNodeHandler();
        rt.setOut(out);
        try {
            out.beginNode(getLocation(), CoreConstants.DUMMY_TAG_NAME, Collections.emptyMap());
            bodyExpr.execute(executor, rt);
            out.endNode(CoreConstants.DUMMY_TAG_NAME);
        } finally {
            rt.setOut(oldOut);
        }
        XNode node = out.endDoc();
        if (singleNode) {
            if (node.getChildCount() != 1) {
                throw newError(ERR_EXEC_COLLECT_RESULT_NOT_SINGLE_NODE);
            } else {
                node = node.child(0);
            }
        }
        return node;
    }
}