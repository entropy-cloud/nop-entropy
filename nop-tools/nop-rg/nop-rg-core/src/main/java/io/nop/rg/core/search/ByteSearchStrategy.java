package io.nop.rg.core.search;

import java.lang.foreign.MemorySegment;

/**
 * 字节级搜索策略契约（nop-rg design 决策 5）。
 *
 * <p>语义约定（plan 2263 钉死）：
 * <ul>
 *   <li>offset 为相对 segment 起点的绝对偏移；limit 为 exclusive 上界；搜索范围为 [offset, limit)。</li>
 *   <li>返回命中模式/字节的起始偏移；未找到返回 -1。</li>
 *   <li>空 pattern 非法，实现必须抛 {@link IllegalArgumentException}（fail-fast，不静默）。</li>
 * </ul>
 */
public interface ByteSearchStrategy {

    /**
     * 在 [offset, limit) 内查找 pattern 的首次出现。
     *
     * @return 命中起始偏移（相对 segment 起点），未找到返回 -1
     * @throws IllegalArgumentException pattern 为空
     */
    long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern);

    /**
     * 在 [offset, limit) 内查找单字节 target 的首次出现。
     *
     * @return 命中偏移（相对 segment 起点），未找到返回 -1
     */
    long findFirstByte(MemorySegment seg, long offset, long limit, byte target);
}
