/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.expr;

import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.IEvalScope;

public class ExprExecHelper {
    public static Object getVar(IEvalScope scope, String varName) {
        EvalFrame frame = scope.getCurrentFrame();

        if (frame != null) {
            int stackSize = frame.getStackSize();
            for (int i = 0; i < stackSize; i++) {
                if (varName.equals(frame.getVarName(i))) {
                    return EvalReference.deRef(frame.getVar(i));
                }
            }
        }
        return scope.getValue(varName);
    }
}
