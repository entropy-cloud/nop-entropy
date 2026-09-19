package io.nop.rg.vector;

import io.nop.rg.core.search.LiteralFinderProvider;
import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.PreparedLiteral;

/**
 * Vector 字面量查找器 SPI 实现（plan 2266）。
 *
 * <p>孵化引用纪律（audit N1）：类级成员不持有孵化类型；{@code available()} 以
 * Class.forName 探测（catch {@link LinkageError}）先行门控；Vector 实现仅在
 * available() 为 true 的分支内实例化，加载失败（LinkageError）二次兜底降级标量。
 * 强制降级 seam：包可见构造参数（测试用，audit M2）。
 */
public class NopRgVectorLiteralFinderProvider implements LiteralFinderProvider {

    private final boolean forceFallback;
    private String unavailableReason = "";

    public NopRgVectorLiteralFinderProvider() {
        this(false);
    }

    NopRgVectorLiteralFinderProvider(boolean forceFallback) {
        this.forceFallback = forceFallback;
        if (forceFallback) {
            this.unavailableReason = "forced fallback (test seam)";
        }
    }

    @Override
    public boolean available() {
        if (forceFallback) {
            return false;
        }
        try {
            Class.forName("jdk.incubator.vector.ByteVector");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            unavailableReason = "jdk.incubator.vector not present (run with --add-modules jdk.incubator.vector): " + e;
            return false;
        }
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public PreparedFinder compile(byte[] pattern, boolean ignoreCase) {
        if (available()) {
            try {
                return new VectorPreparedLiteral(pattern, ignoreCase);
            } catch (LinkageError e) {
                unavailableReason = "vector api load failed, falling back to scalar: " + e;
            }
        }
        // 降级：标量等价实现（ignoreCase 时为标量折叠——非 SIMD，结果一致）
        return PreparedLiteral.compile(pattern, ignoreCase);
    }
}
