package io.nop.rg.core.coordinator;

import java.util.List;

/**
 * 单个命中：文件域字节区间 + 命中文本（count 口径下 text 为 null）。
 *
 * <p>plan 2268 Phase 2：自 {@link SearchCoordinator} 嵌套类提升为顶层（纯移动，行为不变）。
 */
public record Submatch(long byteStart, long byteEnd, String text) {
}
