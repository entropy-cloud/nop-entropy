package io.nop.rg.core.coordinator;

/**
 * 命中区间（文件字节域，start 含 / end 不含；plan 2273 A5：替代 {@code long[]} 魔法下标）。
 * coordinator 内部聚合的传递形态，spans 列表按 start 升序（literalSpans/regexSpans 契约）。
 */
record MatchSpan(long start, long end) {
}
