/**
 * FFM 内存映射 I/O：MappedFileReader（单次整文件映射）与 ChunkedFileReader
 * （大文件分块扫描，主区间无缝平铺 + 双向 overlap，去重归消费者）。
 */
package io.nop.rg.core.io;
