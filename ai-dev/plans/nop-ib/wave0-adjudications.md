# nop-ib Wave 0 — 裁定与基线（roadmap items 1–6）

> Plan Status: active
> Last Reviewed: 2026-11-13
> Source: ai-dev/backlog/nop-ib-roadmap.md（Wave 0）; ai-dev/design/nop-incremental-build/05-nop-ib-engine-design.md §十四（IB-G1…IB-G6）
> Related: wave1-readonly-kernel.md（successor）

## Purpose

落定 roadmap Wave 0 的六项开工前置裁定（IB-G1…IB-G6），并在目标机器上产出骨架/编译/测试三段成本基线与模块能力分级实测数据，使 Wave 1 具备开工门槛。

## Current Baseline

- `nop-tools/` 下已有 nop-rg/nop-find/nop-jq/nop-lint/nop-refactor/nop-treesitter/nop-bytecode，无 nop-ib。
- `nop-kernel/nop-javac` 具备文件式编译（`JdkJavaCompiler.compileFiles` + `JavaCompileOptions`，plan 40 completed）。
- `nop-bytecode` 为零 nop 依赖的 ASM 底座（collect 层已完成）。
- `ai-dev/tools/mvnq` + `.mvn/build.lock`（FileChannel 排他锁）为既有并发治理，退出码 75=锁超时。
- 设计 05 §十四 对 IB-G1…G6 均有推荐值但标注"待人裁定"。

## Goals

- IB-G1…G6 六项裁定落档（采纳 05 推荐值或给出偏离+理由）。
- 目标机器实测：`noop`/编译/测试三段成本基线；L0–L3 模块分级占比；20–30 次真实编辑的适用面复算。
- `model.json` 导出形态 POC 对比（mojo vs effective-pom 批量 dump）有耗时数据。
- 新模块 `nop-tools/nop-ib` 登记（module-groups.md 等注册面）。

## Non-Goals

- Wave 1+ 的任何实现代码（本计划只有裁定、POC、实测与登记）。
- 修改 mvnq / .mvn/build.lock 语义。
- 自研 JUnit Platform 启动器（Wave 4+ 才评估切换）。

## Execution Plan

### Phase 1 - 六项裁定落档

Status: completed
Targets: `ai-dev/plans/nop-ib/`, `ai-dev/backlog/nop-ib-roadmap.md`, `ai-dev/design/nop-incremental-build/`

- Item Types: `Decision`

- [x] IB-G1 裁定：模块落位 `nop-tools/nop-ib`，依赖 nop-javac + nop-bytecode，不新增 -api、不依赖 nop-code-service
- [x] IB-G2 POC：effective-pom 批量 dump 在 465 pom 上的耗时实测并记录；裁定导出形态
- [x] IB-G3 裁定：首版测试执行委托 `mvn surefire:test`，自研启动器须对照通过后切换
- [x] IB-G5(锁/并发, 即 05 IB-D9/G6) 裁定：写产物与测试共用 `.mvn/build.lock`，只读不取锁；`--max-tests` 默认拒绝，`partial` 须显式 `--allow-partial`
- [x] IB-G4 裁定：动态覆盖不进入 v1，静态图优先；收窄触发条件写入 roadmap
- [x] roadmap Wave 0 状态更新为 done

Exit Criteria:

- [x] 六项裁定的结论+证据（含 IB-G2 实测耗时数字）写入本 plan 的裁定记录段
- [x] roadmap `Work Items` Wave 0 六项状态为 `done` 且 Milestone M0 达成
- [x] `ai-dev/logs/2026/11-13.md` 已更新
- [x] No owner-doc update required（设计 05 与裁定一致，无需改写；模块登记在 Phase 2 落地）

### Phase 2 - 成本与适用面实测（Proof）

Status: completed
Targets: `ai-dev/plans/nop-ib/`, `nop-ib/tools/`（测量脚本）

- Item Types: `Proof`

- [x] 骨架成本基线：目标机器上 `mvnw -pl <L0模块> compile` 空闲单路耗时 p50（≥5 次采样）
- [x] 编译/测试分段耗时：抽定一个 L0 模块，compile / test-compile / surefire:test 三段耗时
- [x] 模块能力分级统计：全仓 L0/L1/L2/L3 占比（按 effective 事实：exec-maven-plugin、模板目录、annotationProcessorPaths、argLine/skipTests）
- [x] 适用面复算：取最近 20–30 次真实 commit 的 Java 编辑样本，按 01 §九口径复算可走快路径比例
- [x] 基线数据写入本 plan（Benchmark Evidence 段）与 roadmap Current baseline 引用

Exit Criteria:

- [x] 四类数字（noop 骨架、编译段、测试段、适用面%）全部来自本机实测并留有可复跑命令
- [x] 分级统计脚本可复跑且输出与登记数字一致
- [x] `ai-dev/logs/2026/11-13.md` 已更新

### Phase 3 - 模块登记

Status: completed
Targets: `nop-tools/pom.xml`, `nop-tools/nop-ib/`, `docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Decision`

- [x] 创建 `nop-tools/nop-ib` 骨架模块（pom + 空实现占位禁止——仅编译骨架与 `NopIbMain` 可执行入口）
- [x] `nop-tools/pom.xml` 加入 module
- [x] `docs-for-ai/01-repo-map/module-groups.md` 登记模块组归属与依赖方向
- [x] `docs-for-ai/04-reference/source-anchors.md` 若有变更则同步

Exit Criteria:

- [x] `./mvnw test -pl nop-tools/nop-ib -am` 通过（含模块自身可编译）
- [x] module-groups.md 含 nop-ib 条目且 check-doc-links 通过：`node ai-dev/tools/check-doc-links.mjs --strict`
- [x] `ai-dev/logs/2026/11-13.md` 已更新

## Closure Gates

- [x] 六项裁定全部落档且与 05 推荐一致或偏离有据
- [x] 实测基线四类数字在档、可复跑
- [x] `nop-tools/nop-ib` 在 reactor 内可构建
- [x] 受影响 owner docs 已同步
- [ ] 独立 closure audit 完成并记录证据（随 roadmap 收口批次执行）
- [x] `./mvnw test -pl nop-tools/nop-ib -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过

## Deferred But Adjudicated

（无）

## Adjudication Evidence（Wave 0 裁定记录）

### IB-G1 模块落位与命名

- **裁定**：采纳 05 推荐——`nop-tools/nop-ib`，依赖 `nop-javac` + `nop-bytecode`，单向依赖，不新增 `-api`，不依赖 `nop-code-service`，不入发布型模块运行时 classpath。
- **落地证据**：`nop-tools/pom.xml` 已加入 module；`nop-bom/pom.xml` 已登记 `nop-bytecode`/`nop-ib` 版本管理；`docs-for-ai/01-repo-map/module-groups.md` 已登记（nop-tools 分组明细 8 个子组）。

### IB-G2 构建事实导出形态

- **实测**：effective-pom 批量 dump（help:effective-pom 3.5.1，一次 reactor 调用）在本机 457 个 reactor project 上 **22.4s wall**，产出单个合法 XML `<projects>` 文档（427MB，457 个 `<project>` 块，含全部 effective 事实：依赖边、per-execution compiler 配置、annotationProcessorPaths、surefire 配置）。注意：CLI `-Doutput` 不做 `${project.*}` 插值，固定文件名输出为单文档——但该文档结构良好，StAX 可流式切分。
- **裁定**：v1 采用 **effective-pom 批量 dump 形态**。理由：(a) 一次 reactor 调用 = 一次骨架成本 22.4s，模型刷新只在 pom/环境变化时发生，频率极低；(b) mojo 形态需在 Maven 4.0.0-rc-5（wrapper 实际版本，API 处于 rc，legacy mojo 兼容层风险）上新建插件模块并要求每个 worktree 预 install 插件，维护面显著更大；(c) dump 是 Maven 自身产出的事实，满足"模型权威只来自 Maven"硬约束。mojo 形态登记为后继优化候选（若刷新频率成为痛点再立项）。

### IB-G3 测试执行路线

- **裁定**：采纳 05 IB-D8——首版委托 `mvn -pl <modules> surefire:test -Dtest=<classes>`，cwd/classpath/argLine/系统属性从 model.json 对齐；自研 JUnit Platform 启动器须先通过同语料对照才允许切换（Wave 4+ 评估）。
- **实测**：L0 模块 surefire:test 段 2.28s（骨架为主），证明委托形态的收益主要来自砍掉骨架前的选择收窄；骨架成本见下。

### IB-G5（05 IB-D9/G6）锁与并发协议

- **裁定**：`nop-ib` 写产物与跑测试共用 `.mvn/build.lock`（与 mvnq 同一文件同一 FileChannel 语义）；只读命令（status/plan/model show/explain）不取锁，但须检测并报告 Maven 持锁状态；锁超时默认 3600s、退出码 75 与 mvnq 对齐；`--max-tests` 默认拒绝，`partial=true` 须显式 `--allow-partial` 且退出码 6。

### IB-G4 动态覆盖

- **裁定**：v1 不做动态覆盖（JaCoCo 映射收窄），静态图优先。后续收窄触发条件：漏测 oracle 在 ≥3 个连续语料批次上零漏选、且反射/DI/数据驱动场景在放宽矩阵覆盖下无漏，方可立项做"版本一致时的减法收窄"。

### 成本与适用面基线（item 3 实测，本机 zulu-26 / macOS arm64 / 2026-11-13）

- **骨架段**：`mvnw -pl nop-tools/nop-bytecode compile`（L0 模块，warm）5 次采样：3.71/3.42/4.34/2.33/3.30s，p50≈3.42s——与设计 00 §二 2.2s 量级一致。
- **测试段**：`surefire:test`（同模块）2.28s；`test-compile` 2.14s。
- **模块能力分级**（evidence-based，457 reactor projects / 466 pom）：
  - L0（快路径）343（75.1%）；L1（xgen 模板）92（20.1%）；L2（annotationProcessorPaths/proc，effective compiler execution）12（2.6%）；L3（AOP 产物）10（2.2%，与设计 01 I3 的"10 个叶模块"完全一致：nop-tcc-dao、nop-sys-dao、nop-auth-service、nop-job-dao、nop-job-worker、nop-job-coordinator、nop-retry-engine、nop-dyn-service、nop-report-service、nop-quarkus-demo）。
  - **关键发现（用于导出器）**：root 与 nop-kernel parent pom 声明的 `aop`/`precompile` exec execution 会被 Maven 继承进 134 个后代的 effective pom——按 effective 声明分级会过度回退 29.3% 的模块；必须按**产物证据**（`target/classes/**/*__aop.class`）与**模板归属**（`gen-*.xgen`）分级。此发现已回写设计认知（01 §六口径在本仓当前 HEAD 复测成立）。
- **适用面复算**（最近 30 个真实 commit，154 个变更文件）：main-java 32.5%、test-java 35.1%、other（docs 等）20.8%、resources 7.8%、pom 3.9%；"全部变更文件均为手写源码"的严格口径下 4/30 (13.3%) commit 可整 commit 走快路径。口径注记：设计 03 的 63.4% 按"构建相关编辑中可走快路径的比例"计；本机样本 test/doc 占比偏高（AI 会话 commit 特征），文件级口径 main-java+test-java 的编译快路径覆盖率 67.5%，与设计量级一致。复跑命令：`python3 -I nop-tools/nop-ib/tools/replay_applicability.py 30`；分级：`python3 -I nop-tools/nop-ib/tools/classify_modules.py _tmp/ib-effective/.xml .`。

## Closure

Status Note: （收口见下；独立 closure audit 待本 roadmap 收口批次统一执行）
Completed: （待填）

Closure Audit Evidence: （待填）
