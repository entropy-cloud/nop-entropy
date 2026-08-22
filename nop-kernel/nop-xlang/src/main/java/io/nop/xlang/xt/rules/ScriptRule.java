/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xt.rules;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xt.IXTransformContext;

public class ScriptRule extends AbstractSelectorRule {
    private final IEvalAction script;

    public ScriptRule(IXSelector<XNode> xpath, IEvalAction script) {
        super(xpath, false);
        this.script = script;
    }

    @Override
    public void apply(XNode parent, XNode node, IXTransformContext context) {
        XNode selected = selectNode(node, context);
        if (selected == null)
            return;

        // output 已在 XtTransformContext 构造时注入，这里幂等再次确认，供 XPL 脚本体内直接引用
        context.getEvalScope().setLocalValue("output", context.getOutput());

        script.invoke(context.getEvalScope());
    }

    public IEvalAction getScript() {
        return script;
    }
}
