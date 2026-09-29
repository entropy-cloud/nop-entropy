# nop-bytecode

字节码分析底座库（源码通道 [nop-lint](../nop-lint/) 之外的第二条编码期防线通道）。与 nop-lint **零依赖并行、并行不替代**：nop-lint 在源码层做纯源码分析，本模块在**编译产物**（Maven reactor `target/classes` 目录与 jar）之上做字节码层分析。

- 设计权威：[ai-dev/design/nop-bytecode/00-overview.md](../ai-dev/design/nop-bytecode/00-overview.md)
- 底座终裁 ADR：[ai-dev/design/nop-bytecode/substrate-adjudication.md](../ai-dev/design/nop-bytecode/substrate-adjudication.md)（ASM 自研路线）
- 缺口账本：[docs/gap-ledger.md](docs/gap-ledger.md)（行级缺口状态唯一动态载体）

## 分层（Wave 0 ADR §架构概览）

| 层 | 包 | 状态 |
|---|---|---|
| 采集层 | `io.nop.bytecode.collect` | ✅ v0（plan 02） |
| 内核层（方法内 CFG + 抽象解释） | 待建 | plan 03（item 4） |
| 分析器层（null-flow / 资源配对） | 待建 | plan 05/06（Wave 2/3） |
| 通道层（CLI / 诊断输出） | 待建 | plan 04（item 5） |

## 采集层 v0

- 输入：class 目录（reactor `target/classes` 形态）与 jar（`CollectInput.directory` / `CollectInput.jar`）。
- 工件：`ClassArtifact`（relativePath / className / majorVersion / size / sha256）——className 与版本经 ASM `ClassReader` 解析字节码得出，非文件名猜测。
- 增量：`CollectManifest`（path → size;mtime;sha256;className;majorVersion），未变工件不重读文件；`CollectResult` 给出 added / changed / removed 三类增量与 unchanged 计数。
- 失败语义：缺失输入 → `MissingInputException`（消息含路径清单）；损坏 class → `NopBytecodeException`（消息含路径）——**响亮失败，不静默跳过**。
- mtime 粒度假设与强保证兜底方式见 `CollectManifest` javadoc。

## 构建

```bash
./mvnw test -pl nop-bytecode
```

零 `nop-*` 运行时依赖（仅 ASM 9.7.1，BSD-3；裁定见 plan 02 / ADR）。v65（Java 21 class file）解析已测（JDK ≥ 21 时运行对应测试）。
