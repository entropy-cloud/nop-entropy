package io.nop.rg.core.coordinator;

import java.util.List;

/**
 * 单个命中：文件域字节区间 + 命中文本。text = 命中文本解码；
 * {@code includeSubmatchText=false} 时为空串（区间保留，plan 2273 R5）；
 * count 口径（includeLineText=false）走纯行计数路径，不构造 Submatch。
 *
 * <p>plan 2268 Phase 2：自 {@link SearchCoordinator} 嵌套类提升为顶层（纯移动，行为不变）。
 */
public record Submatch(long byteStart, long byteEnd, String text) {
}
