# nop-ib Wave 2 — 编译快路径（roadmap items 13–18）〔补录〕

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: ai-dev/backlog/nop-ib-roadmap.md（Wave 2）; 设计 05 §7.1/IB-D5/IB-D7

## Purpose

选择性编译执行链：作用域→nop-javac 执行→classpath 物化→compile 命令→对拍 oracle→并发锁回归。实现随 Wave 2 提交（3b5d1482f7）落地,本 plan 为补录归档（执行在 wave1 plan 之后连续进行,未先建独立 plan 文件——偏差已登记）。

## Execution Plan（补录）

### Phase 1 - 编译执行链

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/{compile,javac,lock}`

- Item Types: `Feature`

- [x] ClasspathMaterializer（intra-project jar 禁入断言,reactor 目录替换,外部 jar 本地仓解析,scope 过滤）
- [x] IncrementalCompileExecutor（变更源编译+精确 ABI 扩散+I4 删除清扫+单元索引刷新）
- [x] JavaCompilerFacade（-implicit:none/-g/--release 按模型,Maven 4 source→release 推导,proc 通道按模型）
- [x] BuildLock（与 mvnq MvnqLock 同文件同 FileChannel 语义,超时→退出 75）
- [x] compile 命令（--paths/-pl/--on-fallback=fail→退出 2;回退默认委托 mvnq）

## Closure

Status Note: 对拍 oracle 实测达成（详见下）;独立 closure audit 已完成。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（session agent_ff78ed3b-bbac-4675-8cdc-099bd662fc18,2026-11-14）
- Evidence: 对拍 BYTES_SAME（nop-javac 单文件+整模块）;TestBuildLock 3 用例实跑;ABI 扩散 5 下游 11 文件;L1 nop-core 841/842+1 已声明偏差;`./mvnw test -pl nop-tools/nop-ib` 17/0。

### Evidence（实测）

- 对拍: nop-javac 单文件（body 探针/ABI 探针）与整模块 byte 级零差异;A/B 直接对拍 BYTES_SAME。
- ABI 扩散: JdkJavaCompiler 公有方法探针→abiChanged=1→5 下游模块 11 源文件重编 1.37s,模块集合与索引 refs 精确一致。
- 删除清扫: 删除源文件→旧索引反查删除 .class+嵌套类前缀清扫（单测覆盖语义）。
- 并发: TestBuildLock 互斥/超时/跨进程互通 3 用例;修复自死锁（外层 BuildLock×mvnq 同锁嵌套）。
- L1 整模块: nop-core 842 类,fast path 841/842 byte 零差异+1 个 package-info 轮次偏差（javac 对无注解 package-info 在 -implicit/-proc 语义下不产出,ABI 集合等价,对拍 harness 如实报告,设计 05 §12.2 已声明该代价类别）。
- 已知偏差: 本 plan 在执行后才补录归档（连续执行节奏裁定）,checklist 补录时全项已完成并实测。
