package io.nop.xlang.compare;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;

import java.util.function.Function;

import static io.nop.xlang.XLangErrors.ERR_EXEC_GET_PROP_ON_NULL_OBJ;

/**
 * 差异注入自检专用假列（测试设施，不是真后端）：包装解释器列，故意制造分歧，
 * 用于证明 harness 断言机制红/绿可控（判决由 harness 依据执行结果与证据做出，注入列不自招供）。
 */
public final class InjectedColumns {
    private InjectedColumns() {
    }

    /**
     * 返回值分歧：篡改返回值（值分歧与类型分歧各由 tamper 决定，如 7 -> 999 或 7 -> 7L）。
     */
    public static IEvalBackendColumn returnValueTampering(String backendId, Function<Object, Object> tamper) {
        return new DelegatingColumn(backendId) {
            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                BackendExecutionResult result = InterpreterBackendColumn.INSTANCE.execute(request);
                if (result.getThrown() != null)
                    return result;
                return BackendExecutionResult.value(tamper.apply(result.getReturnValue()),
                        result.getEvidence());
            }
        };
    }

    /**
     * 副作用分歧（scope 变量）：执行后向求值现场注入额外变量。
     */
    public static IEvalBackendColumn scopePolluter(String backendId, String extraVarName) {
        return new DelegatingColumn(backendId) {
            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                BackendExecutionResult result = InterpreterBackendColumn.INSTANCE.execute(request);
                IEvalScope scope = request.getScope();
                scope.setLocalValue(extraVarName, Boolean.TRUE);
                return result;
            }
        };
    }

    /**
     * 副作用分歧（输出缓冲）：执行后向输出缓冲追加调用事件。
     */
    public static IEvalBackendColumn outputPolluter(String backendId, String text) {
        return new DelegatingColumn(backendId) {
            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                BackendExecutionResult result = InterpreterBackendColumn.INSTANCE.execute(request);
                request.getOut().text(SourceLocation.fromLine("injected-output", 1), text);
                return result;
            }
        };
    }

    /**
     * 异常分歧：改写异常错误码与 SourceLocation。
     */
    public static IEvalBackendColumn exceptionRewriter(String backendId) {
        return new DelegatingColumn(backendId) {
            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                BackendExecutionResult result = InterpreterBackendColumn.INSTANCE.execute(request);
                if (result.getThrown() == null)
                    return result;
                NopEvalException rewritten = new NopEvalException(ERR_EXEC_GET_PROP_ON_NULL_OBJ);
                rewritten.loc(SourceLocation.fromLine("spoofed-location", 1));
                return BackendExecutionResult.error(rewritten, result.getEvidence());
            }
        };
    }

    /**
     * 身份伪装：声称 backendId（如 java）但实际经解释器执行（经典 vacuous-pass 场景）。
     */
    public static IEvalBackendColumn interpreterSpoofing(String claimedBackendId) {
        return new DelegatingColumn(claimedBackendId) {
            @Override
            public BackendExecutionResult execute(BackendExecRequest request) {
                return InterpreterBackendColumn.INSTANCE.execute(request);
            }
        };
    }

    abstract static class DelegatingColumn implements IEvalBackendColumn {
        private final String backendId;

        DelegatingColumn(String backendId) {
            this.backendId = backendId;
        }

        @Override
        public String getBackendId() {
            return backendId;
        }

        @Override
        public boolean isSupportsStaticUnits() {
            return true;
        }

        @Override
        public boolean isSupportsDynamicUnits() {
            return true;
        }
    }
}
