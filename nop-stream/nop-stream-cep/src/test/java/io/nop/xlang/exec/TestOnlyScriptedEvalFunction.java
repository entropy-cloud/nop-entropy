package io.nop.xlang.exec;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * Test-only stand-in for a compiled XLang eval function leaf (real-world
 * shape: {@code io.nop.xlang.exec.MakeScopeEvalFunction} produced by XPL
 * scripted CEP pattern conditions, see fraud-example
 * {@code TestS1CdcPipelineE2E}). Deliberately lives under the
 * {@code io.nop.xlang} package namespace — that namespace prefix is part of
 * the {@code CepOperator} shareable-leaf whitelist contract — and is NOT
 * {@link java.io.Serializable}, exactly like the real compiled functions.
 *
 * <p>Stateless by contract: compiled XLang functions are immutable executables
 * evaluated against a caller-provided scope, and they implement no
 * RichFunction lifecycle (setRuntimeContext/open/close never touch them).
 */
public class TestOnlyScriptedEvalFunction implements IEvalFunction {
    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return Boolean.TRUE;
    }
}
