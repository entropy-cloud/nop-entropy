# nop-bytecode（字节码分析通道）

字节码通道是源码通道 nop-lint（见同目录 nop-lint 模块页） 之外的**第二条编码期防线**：在编译产物（Maven reactor 输出目录与 jar 产物）之上做字节码层缺陷发现。两通道**零依赖并行、并行不替代**——缺口面归属见通道缺口账本（`nop-bytecode/docs/gap-ledger.md`）；架构决策记录在本仓 ai-dev 设计目录（按仓内路由惯例查阅，不在此互链）。

## 快速开始（发现流 CLI，report-only）

```bash
./mvnw test-compile -pl nop-bytecode
./mvnw -pl nop-bytecode dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
java -cp "nop-bytecode/target/classes:nop-bytecode/target/test-classes:$(cat nop-bytecode/target/test-classpath.txt)" \
  io.nop.bytecode.cli.NopBytecodeMain <class目录或jar>... [--manifest <path>] [--json]
```

- 输出：每行一条 finding——`[severity] ruleId class#method@insnIndex (ref) message`；`--json` 输出同序数组（字段：ruleId/severity/message/className/methodName/insnIndex/ref，结构与 nop-lint `Diagnostic` 对齐，位置为字节码坐标系而非源码行）。
- 退出码（**report-only，与 nop-lint findings=1 哲学不同**）：`0` = 运行完成（有无 findings 均 0）；`2` = 失败（usage 错误 / 缺失输入 / 损坏产物 / 分析异常），fail-fast 不输出部分报告。升 hard gate 须独立 plan + 对照期误报数据。
- 当前 ruleId：`nullflow/may-null-deref`（解引用前判空路径敏感分析，v1 降配口径：分支敏感仅 IFNULL/IFNONNULL；Wave 2 升级为全条件分支敏感）。豁免面：`Objects.requireNonNull` 系语义门控、守卫分支。

## 架构要点

| 层 | 包 | 职责 |
|---|---|---|
| 采集层 | `io.nop.bytecode.collect` | class 目录/jar → 工件清单（className/major/sha256），manifest 增量；缺失/损坏响亮失败 |
| 内核层 | `io.nop.bytecode.kernel` | 方法内指令级 CFG（含 try 范围限定 handler 边）+ 槽位精确抽象解释框架 |
| 分析器层 | `io.nop.bytecode.analysis.nullflow` | 三值 nullness lattice 沿 CFG 传播，解引用命中收集 |
| 通道层 | `io.nop.bytecode.cli` | 发现流渲染（console/JSON）与退出码契约 |

规则资产与 nop-lint 相互独立（`nullflow/` 命名空间）；autofix/风格/注释依赖面永不入本通道。
