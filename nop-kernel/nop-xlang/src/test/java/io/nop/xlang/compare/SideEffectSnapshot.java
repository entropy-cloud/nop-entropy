package io.nop.xlang.compare;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求值后求值现场的副作用快照：本地变量集（封闭化）+ 输出缓冲完整 API 调用序列。
 * 快照在每列各自的现场上获取，禁止跨列共享可变现场。
 */
public final class SideEffectSnapshot {
    private final Map<String, Object> localVars;
    private final List<RecordedOutputCall> outputCalls;

    public SideEffectSnapshot(Map<String, Object> localVars, List<RecordedOutputCall> outputCalls) {
        this.localVars = new LinkedHashMap<>(localVars);
        this.outputCalls = new ArrayList<>(outputCalls);
    }

    public Map<String, Object> getLocalVars() {
        return localVars;
    }

    public List<RecordedOutputCall> getOutputCalls() {
        return outputCalls;
    }
}
