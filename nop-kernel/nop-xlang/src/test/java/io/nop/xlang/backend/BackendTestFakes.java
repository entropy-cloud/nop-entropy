package io.nop.xlang.backend;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * nop-xlang 测试域 fake 后端：裁决入口/注册表/观测的单元测试载体（真实后端模块不在本模块
 * test classpath——模块可见性硬约束，Phase 1 §6 裁定的拆分落点）。
 */
public final class BackendTestFakes {

    public static final String FAKE_STATIC_ID = "fake-static";

    public static final String FAKE_DYNAMIC_ID = "fake-dynamic";

    private BackendTestFakes() {
    }

    public static class FakeStaticBackend implements IEvalStaticBackend {
        private final Set<String> scanList;
        private volatile boolean available = true;
        private volatile String unavailableReason;
        private final Map<String, IEvalStaticBinding> bindings = new HashMap<>();

        public FakeStaticBackend(Set<String> scanList) {
            this.scanList = scanList;
        }

        public void markUnavailable(String reason) {
            this.available = false;
            this.unavailableReason = reason;
        }

        public void putBinding(String path, IEvalStaticBinding binding) {
            bindings.put(path, binding);
        }

        @Override
        public String getBackendId() {
            return FAKE_STATIC_ID;
        }

        @Override
        public Set<EvalBackendCapability> getCapabilities() {
            return Collections.unmodifiableSet(EnumSet.of(EvalBackendCapability.STATIC_GENERATED));
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getUnavailableReason() {
            return unavailableReason;
        }

        @Override
        public boolean isStaticCandidate(String resourcePath) {
            return resourcePath != null && scanList.contains(resourcePath);
        }

        @Override
        public IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree) {
            return bindings.get(resourcePath);
        }
    }

    public static class FakeDynamicBackend implements IEvalDynamicBackend {
        private volatile boolean available = true;
        private volatile String unavailableReason;
        private volatile EvalBackendDynamicOutcome nextOutcome =
                EvalBackendDynamicOutcome.ofValue("fake-dynamic-value", "fake-artifact");
        private int executionCount;

        public void markUnavailable(String reason) {
            this.available = false;
            this.unavailableReason = reason;
        }

        public void setNextOutcome(EvalBackendDynamicOutcome outcome) {
            this.nextOutcome = outcome;
        }

        public int getExecutionCount() {
            return executionCount;
        }

        @Override
        public String getBackendId() {
            return FAKE_DYNAMIC_ID;
        }

        @Override
        public Set<EvalBackendCapability> getCapabilities() {
            return Collections.unmodifiableSet(EnumSet.of(EvalBackendCapability.DYNAMIC_TRANSLATION));
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String getUnavailableReason() {
            return unavailableReason;
        }

        @Override
        public EvalBackendDynamicOutcome executeDynamic(EvalBackendDynamicRequest request) {
            executionCount++;
            return nextOutcome;
        }
    }

    public static class FixedValueBinding implements IEvalStaticBinding {
        private final Object value;
        private final Object artifact;

        public FixedValueBinding(Object value, Object artifact) {
            this.value = value;
            this.artifact = artifact;
        }

        @Override
        public Object execute(EvalRuntime rt) {
            return value;
        }

        @Override
        public Object getBindingArtifact() {
            return artifact;
        }
    }
}
