package io.nop.rg.core.coordinator;

/**
 * 任务级联合返回载体（plan 2564 W-05）：单文件任务正常返回 matches（可为 null = 无命中/
 * 二进制跳过）或失败返回 error（见 {@link FileError}）；两者互斥。
 * 收集循环按 error 优先分流：error 入 errors、matches 入 results。
 *
 * <p>形态裁定登记：task 级联合 record（设计裁定 C 留实现评审项）——三字段平铺
 * （rel/matches/error）而非 sealed 层级，保持任务 lambda 单点构造与收集循环单点分流的
 * 最小形态。
 */
record FileTaskOutcome(String relativePath, FileMatches matches, FileError error) {
}
