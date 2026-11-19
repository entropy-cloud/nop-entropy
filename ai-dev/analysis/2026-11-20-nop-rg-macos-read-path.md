# Analysis: nop-rg macOS 非 mmap 读路径评估（roadmap Wave 5 item 21）

> Date: 2026-11-20
> Status: closed（裁定：not-implement，mmap 保持全平台统一）

## 问题

roadmap item 21 提出「macOS 非 mmap 读路径评估」：rg 在 macOS 因 VM 锁与并发 map/unmap 竞争
（含 madvise 实测挂死）默认禁用 mmap 而走 read 路径；nop-rg 当前全平台 mmap（`MappedFileReader`/
`ChunkedFileReader` 仅 mmap 实现），且 madvise 仅 Linux（`MappedFileReader.adviseSequential`，
rg mmap.rs:73-79 同因裁定）。需评估 macOS 上是否需要 FileChannel.read 回退路径。

## 证据

本机实测（macOS darwin arm64，Zulu 26；256MB urandom 文件，4 轮，page cache 热）：

| 路径 | 每轮耗时 |
| --- | --- |
| mmap + 稀疏 touch（4KB 步长采样求和，接近搜索器实际访问模式） | 9–22ms |
| FileChannel.read 顺序读入 1MB direct buffer（全量字节求和） | 453–459ms |

mmap 快 **约 50 倍**（热缓存；冷缓存差异缩小但顺序读仍需显式拷贝全部字节，mmap 由页缓存
按需换入、搜索器稀疏访问下拷贝量更小）。脚本：`_tmp/wave5-item21/MmapVsRead.java`。

rg 禁用 macOS mmap 的动机核对：`crates/searcher/src/searcher/mmap.rs` 注释与 issue 记录均为
**可靠性**（VM 锁竞争/挂死风险），非性能；且 rg 的 macOS read 路径没有 madvise 提示可用。
nop-rg 在 macOS 不调用 madvise（已裁定），不触碰该风险面；单次 map → 扫描 → Arena 确定性
unmap 的生命周期也无 rg 的长期驻留映射问题。

## 裁定

**not-implement**：macOS 保持 mmap 读路径，不引入 FileChannel.read 回退。理由：

1. 性能证据一边倒：mmap 在 macOS 上远快于顺序 read（≈50x 热缓存）。
2. rg 换 read 路径是可靠性动机，nop-rg 的 mmap 生命周期（短驻留 + 单文件 arena + no madvise）
   已规避对应风险面，且无 macOS 相关挂死/fault 报告（plan 2273 W-03 的 truncation fault 已有
   显式错误包装钉子）。
3. 引入双路径会带来分块/命中行提取两套实现的等价维护成本（W-05 失效面扩大），收益为负。

## 影响

- roadmap item 21 以本裁定收口（walker stat 削减部分已另行落地）。
- 若未来 macOS 出现 mmap fault 稳定性报告，重新评估时本文件为基准证据。
