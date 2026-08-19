package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * corpus 单元的预期（三类中适用者）：返回值+类型 / 副作用快照 / 异常（错误码+预期源位置）。
 */
public final class ExpectedOutcome {
    private Object returnValue;
    private boolean hasReturnValue;

    private Map<String, Object> expectedScopeVars;
    private List<RecordedOutputCall> expectedOutputCalls;

    private String errorCode;
    private SourceLocation expectedErrorLocation;

    public static ExpectedOutcome returnValue(Object value) {
        ExpectedOutcome o = new ExpectedOutcome();
        o.returnValue = value;
        o.hasReturnValue = true;
        return o;
    }

    public static ExpectedOutcome exception(String errorCode) {
        ExpectedOutcome o = new ExpectedOutcome();
        o.errorCode = errorCode;
        return o;
    }

    public ExpectedOutcome scopeVars(Map<String, Object> vars) {
        this.expectedScopeVars = new LinkedHashMap<>(vars);
        return this;
    }

    public ExpectedOutcome outputCalls(RecordedOutputCall... calls) {
        this.expectedOutputCalls = new ArrayList<>();
        for (RecordedOutputCall call : calls) {
            this.expectedOutputCalls.add(call);
        }
        return this;
    }

    public ExpectedOutcome errorLocation(SourceLocation loc) {
        this.expectedErrorLocation = loc;
        return this;
    }

    public boolean hasReturnValue() {
        return hasReturnValue;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public boolean hasExpectedScopeVars() {
        return expectedScopeVars != null;
    }

    public Map<String, Object> getExpectedScopeVars() {
        return expectedScopeVars;
    }

    public boolean hasExpectedOutputCalls() {
        return expectedOutputCalls != null;
    }

    public List<RecordedOutputCall> getExpectedOutputCalls() {
        return expectedOutputCalls;
    }

    public boolean isExpectException() {
        return errorCode != null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public SourceLocation getExpectedErrorLocation() {
        return expectedErrorLocation;
    }
}
