# nop-bytecode

字节码分析底座库（源码通道 [nop-lint](../nop-lint/) 之外的第二条编码期防线通道）。与 nop-lint **零依赖并行、并行不替代**：nop-lint 在源码层做纯源码分析，本模块在**编译产物**（Maven reactor `target/classes` 目录与 jar）之上做字节码层分析。

- 设计权威：[ai-dev/design/nop-bytecode/00-overview.md](../ai-dev/design/nop-bytecode/00-overview.md)
- 底座终裁 ADR：[ai-dev/design/nop-bytecode/substrate-adjudication.md](../ai-dev/design/nop-bytecode/substrate-adjudication.md)（ASM 自研路线）
- 缺口账本：[docs/gap-ledger.md](docs/gap-ledger.md)（行级缺口状态唯一动态载体）
- 内核性能基线：[docs/perf-baseline.md](docs/perf-baseline.md)

## 分层

| 层 | 包 | 状态 |
|---|---|---|
| 采集层 | `io.nop.bytecode.collect` | ✅ v0（plan 02） |
| 内核层（方法内 CFG + 抽象解释） | `io.nop.bytecode.kernel` | ✅ v1（plan 03） |
| 分析器层（null-flow v1 降配口径） | `io.nop.bytecode.analysis.nullflow` | ✅ 内核级（正式版 = plan 05/Wave 2） |
| 通道层（CLI 发现流） | `io.nop.bytecode.cli` | ✅ v1（plan 04） |

## 发现流 CLI（report-only）

```bash
./mvnw test-compile -pl nop-bytecode
./mvnw -pl nop-bytecode dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
java -cp "nop-bytecode/target/classes:nop-bytecode/target/test-classes:$(cat nop-bytecode/target/test-classpath.txt)" \
  io.nop.bytecode.cli.NopBytecodeMain <class目录或jar>... [--manifest <path>] [--json] <class目录或jar>... [--manifest <path>] [--json]
```

- **退出码语义（report-only，与 nop-lint findings=1 哲学不同）**：0 = 运行完成（有无 findings 均 0）；2 = 失败（usage 错误 / 缺失输入 / 损坏产物 / 分析异常），fail-fast 不输出部分报告。升 hard gate 须独立 plan + 对照期误报数据（roadmap HC6）。
- **去重口径**：ruleId 命名空间 `nullflow/`（路径敏感面单报）；同名 relativePath 跨输入 = 响亮失败；同内容经不同容器 = 按发现键去重单报；pattern 面归源码 lane，重叠归 Wave 4 item 8 对照裁决。
- **分析口径**：v1 内核为降配口径（分支敏感仅 IFNULL/IFNONNULL，substrate ADR §5）；Wave 2 正式口径（全条件分支敏感）落地后输出更全。

## 构建

```bash
./mvnw test -pl nop-bytecode -am
```

零 `nop-*` 运行时依赖（仅 ASM 9.7.1，BSD-3）。v65（Java 21 class file）解析已测。
