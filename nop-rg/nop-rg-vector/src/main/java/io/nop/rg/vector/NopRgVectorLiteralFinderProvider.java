package io.nop.rg.vector;

import io.nop.rg.core.search.LiteralFinderProvider;
import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.PreparedLiteral;

/**
 * Vector 字面量查找器 SPI 实现（plan 2266；plan 2267 R1 增加模式长度阈值策略）。
 *
 * <p>孵化引用纪律（audit N1）：类级成员不持有孵化类型；{@code available()} 以
 * Class.forName 探测（catch {@link LinkageError}）先行门控；Vector 实现仅在
 * available() 为 true 的分支内实例化，加载失败（LinkageError）二次兜底降级标量。
 * 强制降级 seam：包可见构造参数（测试用，audit M2）。
 *
 * <p>长度阈值策略（plan 2267 R1，基准取证见 benchmark README）：SIMD 锚点全扫描对短模式
 * 劣于 BMH 跳表跳跃——6B 实测向量仅为标量 72-80%；16B 起反超（稀疏 3.5x、密集 2.5x）。
 * 短于阈值的模式返回标量等价 {@link PreparedLiteral}（结果一致，避免 --vector 回退短模式性能）。
 */
public class NopRgVectorLiteralFinderProvider implements LiteralFinderProvider {

    /**
     * SIMD 启用的最短模式长度（字节）。6B 实测向量 = 标量 72-80%（劣化），16B 实测 = 2.5-3.5x（胜出），
     * 8-15B 段未测——保守归标量。取证：_tmp/nop-rg-bench/vec-16b.json、VectorCompareBenchmark 场景矩阵。
     */
    public static final int SIMD_MIN_PATTERN_LENGTH = 16;

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
        if (available() && pattern.length >= SIMD_MIN_PATTERN_LENGTH) {
            try {
                return new VectorPreparedLiteral(pattern, ignoreCase);
            } catch (LinkageError e) {
                unavailableReason = "vector api load failed, falling back to scalar: " + e;
            }
        }
        // 降级：标量等价实现（ignoreCase 时为标量折叠——非 SIMD，结果一致）；
        // 短于 SIMD_MIN_PATTERN_LENGTH 的模式也走此路径（长度阈值策略，非降级语义）
        return PreparedLiteral.compile(pattern, ignoreCase);
    }
}
