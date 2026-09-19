package io.nop.rg.core.search;

import java.lang.foreign.MemorySegment;

/**
 * 预编译字面量查找器的抽象（plan 2266 B1 裁定）：coordinator 两条字面量路径
 * （整文件/分块）经本接口消费，标量（PreparedLiteral）与 Vector（nop-rg-vector 模块）
 * 实现可互换。语义与 {@link ByteSearchStrategy} 的 findPattern 一致：
 * offset 绝对、limit exclusive、未找到 -1。
 */
public interface PreparedFinder {

    /**
     * 在 [offset, limit) 内查找模式的首次出现。
     *
     * @return 命中起始偏移（相对 segment 起点），未找到返回 -1
     */
    long find(MemorySegment seg, long offset, long limit);

    int patternLength();
}
