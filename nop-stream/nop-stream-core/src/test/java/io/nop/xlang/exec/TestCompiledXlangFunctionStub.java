package io.nop.xlang.exec;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * Test stub standing in for a compiled XLang eval function (e.g.
 * {@code io.nop.xlang.exec.MakeScopeEvalFunction}): NOT Serializable, NOT a
 * RichFunction, class name scoped under {@code io.nop.xlang.} — exactly the
 * shareable-leaf whitelist shape of plan 2511 adjudication ② §3.2 reused by
 * plan 2512 adjudication ③. Lives in the {@code io.nop.xlang} package so the
 * whitelist's class-name predicate matches without production-code changes.
 */
public class TestCompiledXlangFunctionStub implements IEvalFunction {
    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return thisObj;
    }
}
