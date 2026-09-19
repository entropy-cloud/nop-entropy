# 2266 nop-rg Wave 4 — Vector 加速（nop-rg-vector + CLI --vector）

> Plan Status: completed
> Last Reviewed: 2026-09-19
> Source: `ai-dev/backlog/nop-rg-roadmap.md`（Stage 14-15 + 里程碑 M4「Vector加速可用」）、`ai-dev/design/nop-rg/01-architecture-baseline.md`（决策 5 修订版：VectorByteSearcher）、`00-vision.md` 成功标准 4（可降级）
> Related: Plan 2263/2264/2265（Wave 1-3，已完成）

## Purpose

交付 Vector API（SIMD）加速的搜索策略：`nop-rg-vector` 模块运行时检测 Vector API 可用性并自动降级到标量；CLI `--vector` 开关接入策略选择；结果与标量路径完全一致。

## Current Baseline

- Wave 1-3 已完成（Work Item 1-13 + M1/M2/M3 全 done）：coordinator 两条字面量路径（整文件 `literalSpans`、分块 `searchFileChunked`）均消费 **precompiled `PreparedLiteral`**（`find(seg, offset, limit)` + `patternLength()`）；`ByteSearchStrategy` 接口在生产代码已无调用方（-i 也走 PreparedLiteral 折叠）。VECTOR 策略当前显式抛 NopRgException（本计划接上真实实现）。
- **编译配置（审查实测，JDK 26.0.1 Zulu + JDK 25.0.1 双验）**：`maven.compiler.release=22` + compilerArgs `--add-modules jdk.incubator.vector` **可行**（ct.sym 含 release-22 孵化历史，API 形态与运行时一致，`ByteVector.fromMemorySegment(species, seg, offset, ByteOrder[, mask])` 兼容）；无 add-modules 则编译失败。surefire fork 需各模块 `<argLine>--add-modules jdk.incubator.vector</argLine>`（fork JVM 不继承；注意会**整体替换**根 POM 字面量 argLine——jacoco/堆参数丢失，有意接受）。
- 运行期事实（审查实测）：无 flag 时 `Class.forName` 抛 **NoClassDefFoundError**（探测须 catch `LinkageError`）；带 flag 时 SPECIES_PREFERRED=128-bit（Apple NEON）；stderr 有 incubator WARNING（对比测试不得 redirectErrorStream 比对 stderr）。
- `-i` 组合：VECTOR 的折叠 SIMD 化不在本计划——工厂对 ignoreCase 请求返回标量折叠等价 PreparedFinder（行为 = FoldingByteSearcher，注明"非 SIMD 加速"）。

## Goals

- core 新增 `PreparedFinder` 接口（`find(seg, offset, limit)` + `patternLength()`），既有 `PreparedLiteral` 实现之——为 Vector 提供可插拔的预编译形态（B1 裁定：coordinator 两条路径消费 PreparedFinder，VECTOR 经工厂产出的 PreparedFinder 无缝接入两条路径）。
- `nop-rg-vector` 模块（JDK ≥ 25 激活 profile 挂入 reactor；release=22 + add-modules 编译配置）：
  - `VectorByteSearcher`/`VectorPreparedLiteral`：SIMD `findFirstByte`（向量化 memchr）与 `findPattern`（SIMD 锚点扫描 + 逐字节校验，最罕见字节启发式与标量一致）；语义与标量完全一致（offset/limit/-1/空模式抛异常）；
  - **SPI**：core 定义 `LiteralFinderProvider`（`compile(pattern, ignoreCase) → PreparedFinder` + `available()`），vector 经 `META-INF/services` 注册（ServiceLoader，不引入 Class.forName 字符串探测）；provider 内部 Vector API 不可用（catch `LinkageError`）时降级返回标量等价 PreparedFinder，并暴露降级原因（供测试/日志，不静默吞）；
  - ignoreCase 请求：【偏差已裁定】实现为 SIMD 双大小写掩码（anchorByte2 OR 掩码，fuzz 实证正确）——优于原计划的"返回标量折叠"，仍满足结果一致契约。
- coordinator VECTOR 策略：`ServiceLoader.load(LiteralFinderProvider)` → 有 provider：compile 并用于**两条路径**（含 >chunkedThreshold 大文件——VECTOR 支持分块路径，若 provider 内部降级为标量则性能不加速但结果一致）；无 provider（classpath 无 vector 模块）→ 显式 NopRgException（报明条件：classpath 需 nop-rg-vector + `--add-modules jdk.incubator.vector`），不静默。
- CLI `--vector`：`Strategy.VECTOR`；**`--regex` 优先于 `--vector`**（同时给出时 REGEX 生效，文档注明）；`--vector` 与 `-i` 组合 = 工厂返回标量折叠（注明非 SIMD）；README 补 `--add-modules` 前置条件。
- 集成测试：`--vector` 与标量模式同 corpus 结果完全一致；VECTOR 不可用路径显式报错断言。
- roadmap Work Item 14/15 与里程碑 M4 → done。

## Non-Goals

- Vector 相对标量的加速比达标线（正确性为本 wave gate；性能数据后续进 benchmark）。
- Folding 的 SIMD 化（注：字面量 -i 已实现 SIMD 双大小写掩码；本条指其余折叠路径）。
- findFirstByte 生产接线（isBinary 保持私有标量实现——VEC-03 为契约对齐实现，仅单测覆盖，注明避免 Anti-Hollow 误判）。

## Scope

### In Scope

- core：`PreparedFinder` 接口 + `LiteralFinderProvider` SPI（公共契约新增，按 guide Rule 17 回写 design 决策 5：发现机制 = ServiceLoader SPI）。
- `nop-rg/nop-rg-vector/` 模块（SPI 实现 + SIMD 搜索 + 降级）及测试。
- `nop-rg/nop-rg-bom`：dependencyManagement 补 nop-rg-vector。
- `nop-rg-cli`：`--vector` 开关（对 vector 模块 optional 依赖——分发缺省不带 vector，报错路径真实可达；README 写明启用方式）；测试 argLine add-modules。
- roadmap 状态更新、daily log、repo-map 补 vector。

### Out Of Scope

- Vector 性能达标线；Folding SIMD 化；findFirstByte 生产接线。

## Execution Plan

### Phase 1 - core PreparedFinder + SPI 与 nop-rg-vector 模块（VEC-01..04）

Status: completed
Targets: `nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/search/`、`nop-rg/nop-rg-vector/`

- Item Types: `Fix`（接口抽取 + 新模块 + 新算法）、`Decision`（ServiceLoader SPI 发现机制）

- [x] core：`PreparedFinder` 接口；`PreparedLiteral implements PreparedFinder`（行为不变，既有测试守护）；`LiteralFinderProvider` SPI 接口（`compile`/`available`/降级原因）
- [x] `nop-rg-vector` 模块：release=22 + add-modules 编译配置 + JDK ≥ 25 激活 profile + surefire argLine；依赖 nop-rg-core；META-INF/services 注册
- [x] `VectorPreparedLiteral`（SIMD findPattern：锚点字节向量化扫描 + 逐字节校验）与 `VectorByteSearcher.findFirstByte`（向量化 memchr）；空模式/边界语义与标量一致
- [x] 降级：provider.available() 探测（catch `LinkageError`）；不可用 → 返回标量等价 PreparedFinder + 降级原因字段；测试 seam = provider 构造参数强制降级
- [x] 单元测试：Vector vs Scalar 等价 fuzz（≥500 例：随机数据/模式/offset/limit 逐 case 对齐）；强制降级 → 标量实例且结果一致；边界（模式长于数据、limit 裁剪、高字节数据）；`findFirstByte` 契约对齐（注明无生产消费者）
- [x] nop-rg-bom 补 nop-rg-vector

Exit Criteria:

- [x] `./mvnw compile -pl nop-rg/nop-rg-vector -am` 通过（release=22 + add-modules 实测配置，审查验证）
- [x] 等价性 fuzz 测试通过（500 例随机对齐，含 ignoreCase 双大小写掩码修复）
- [x] 降级测试通过（强制降级 → 标量等价、结果一致、原因可获取）
- [x] **无静默跳过**：探测失败原因可获取（不吞）
- [x] **接线验证**：SPI 注册经 ServiceLoader 可发现（testSpiProviderDiscoverable）
- [x] owner docs：design 决策 5 已回写（ServiceLoader SPI 发现机制 + PreparedFinder 抽象 + VectorPreparedLiteral 入表，审查 N4）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - coordinator 接入 + CLI --vector（VSW-01..03）

Status: completed
Targets: `nop-rg-core/coordinator/`、`nop-rg-cli/`

- Item Types: `Fix`（接线 + 开关）

- [x] coordinator VECTOR 策略：ServiceLoader 发现 provider → compile → 两条路径（整文件 + 分块 >阈值）消费 PreparedFinder（VECTOR 支持大文件；工厂内降级则性能不加速但结果一致）；无 provider → 显式 NopRgException（缺失条件：classpath nop-rg-vector + --add-modules）
- [x] CLI `--vector`：`strategy = regex ? REGEX : (vector ? VECTOR : LITERAL)`（--regex 优先，注明）；cli 对 vector 模块 optional 依赖（显式报错路径真实可达）
- [x] surefire argLine：nop-rg-vector 与 nop-rg-cli 两模块 `--add-modules jdk.incubator.vector`（接受替换根 argLine；core 不需要）
- [x] 集成测试：`--vector` 与标量模式同 corpus 输出一致（进程内 main）；VECTOR 无 provider 显式报错断言（core 测试天然无 vector 依赖）；canary：cli 测试内 Class.forName 断言孵化模块可加载（argLine 配置错即 fail）
- [x] repo-map nop-rg 条目补 vector；nop-rg-cli README 补 --vector 用法（--add-modules 前置 + incubator stderr 说明）

Exit Criteria:

- [x] `./mvnw test -pl nop-rg/nop-rg-cli -am` 通过（24 tests 含 VectorModeTest 4；add-modules 下 canary + 集成测试生效）
- [x] `--vector` 与标量输出一致（归一化日志后逐行断言；count/regex 优先级组合覆盖）
- [x] **接线验证**：VECTOR 策略运行时真实经 SPI 调用 Vector 实现（--vector 输出一致 + ServiceLoader 发现断言）；无 provider 显式报错（core 侧 testVectorStrategyExplicitlyFails——core 测试 classpath 天然无 vector 模块）
- [x] owner docs：repo-map 补 vector + design 决策 5 已回写（Phase 1 项）——audit B1 后补齐
- [x] `ai-dev/logs/` 已更新
- [x] **无静默跳过**：classpath 缺失报错含条件；孵化模块缺失走降级且 stderr 提示（provider 原因可获取）
- [x] owner docs：repo-map 补 vector + design 决策 5 已回写（Phase 1 项）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] roadmap Work Item 14/15 → `done`；M4 依赖项全 done 后标 `done`
- [x] 所有 in-scope confirmed live defects 已修复
- [x] 行为/契约结果已达成：--vector 可用（或明确报错），结果与标量一致
- [x] 必要 focused verification 已完成（Phase 1-2 Exit Criteria 全勾）
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] owner docs：repo-map 补 vector；design 决策 5 回写（SPI + PreparedFinder）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：VECTOR 策略运行时真实经 SPI 调用 Vector 实现（或显式报错）；降级路径真实可走；无空方法体
- [x] `./mvnw test -pl nop-rg/nop-rg-cli -am` 通过（24 tests）
- [x] `./mvnw compile -pl nop-rg/nop-rg-vector -am` 通过
- [x] 代码规范检查：imports 分组、无裸 RuntimeException、错误消息英文
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-rg --severity high` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Vector 相对标量的加速比基准（ ScalarSearchBenchmark 对比），性能数据后续进 benchmark。
- Folding（-i）SIMD 化。
- design 测试策略表"标量 vs Vector"JMH 行随加速比基准一并落位。

## Closure

Status Note: Wave 4 交付落地：core PreparedFinder 抽象 + LiteralFinderProvider SPI（ServiceLoader 发现，双失败面可区分）；nop-rg-vector（SIMD findPattern 锚点扫描 + findFirstByte 向量化 memchr + 降级 seam 与原因可获取；孵化引用纪律 per audit N1）；coordinator VECTOR 经 SPI 接入两条路径（含大文件，工厂内降级不加速但结果一致，classpath 缺失显式报错）；CLI --vector（--regex 优先，README 启用说明）。--vector 与标量输出一致（归一化后逐行断言）。
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure auditor（fresh session，agent_2b5a92be-666d-4ba8-bdbe-816f23dfef3b，未参与实现）；draft review 由另一独立 agent 两轮完成（agent_afcb8983-0574-4c8d-bb29-8efa23bd9066，含实测探针）
- Audit Session: agent_2b5a92be-666d-4ba8-bdbe-816f23dfef3b
- Evidence:
  - 独立复跑：cli 24/24（NopRgMainTest 9 + RgComparisonTest 9 + JfrSwitchTest 2 + VectorModeTest 4）、core 52/52、vector 8/8 全过
  - Anti-Hollow：端到端调用链逐环核实（NopRgMain:144 → coordinator:87 → ServiceLoader:253 → VectorPreparedLiteral 两条路径消费）；降级路径真实（LinkageError catch + 强制降级 seam + stderr 提示）；孵化引用纪律合规；scan-hollow EXIT=0
  - SIMD 算法抽查：掩码边界/firstTrue 语义/坏字符推进 Horspool 论证/ignoreCase 双大小写掩码——逐项正确
  - Closure Gates：14 项全部 PASS（audit 报告第五节逐条 + 本轮修复后复核）
  - 工具门禁：checklist/doc-links/scan-hollow EXIT=0（审计者复跑 + 修复后复核）
  - 审计问题处置：B1 repo-map vector 条目补齐（python 替换静默失败，audit 拦截）；B2 Phase 2 EC 4/5 勾选；M1/M2 两处 plan 文本与实现的偏差注明（JDK profile / -i SIMD 双大小写掩码优于原计划）；M3 全部变更提交（本 commit）；m1 陈旧 javadoc 更新；m2 本证据段回写
  - Deferred 项分类检查：加速比基准/-i SIMD 其余路径/设计表行均为合法 out-of-scope 或 optimization 分类

Follow-up:

- Vector 相对标量的加速比基准（后续 benchmark 增强）
- Folding（-i）SIMD 化
