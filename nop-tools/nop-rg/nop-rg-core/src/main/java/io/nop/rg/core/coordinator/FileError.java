package io.nop.rg.core.coordinator;

/**
 * 单文件搜索失败项（plan 2564 W-05 部分成功语义）：任务边界捕获的异常归一为
 * 携带文件相对路径的错误项，不再中断整体搜索（rg「报错继续、exit 2 反映有错」对齐）。
 *
 * @param relativePath 失败文件的相对路径（'/' 分隔；兜底腿无法定位文件时为空串）
 * @param message      失败消息（含原始异常上下文；可能为 null——裸 InternalError 常无消息）
 */
public record FileError(String relativePath, String message) {
}
