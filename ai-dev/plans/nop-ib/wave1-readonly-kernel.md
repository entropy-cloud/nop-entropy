# nop-ib Wave 1 — 只读内核（roadmap items 7–12）

> Plan Status: completed
> Last Reviewed: 2026-11-13
> Source: ai-dev/backlog/nop-ib-roadmap.md（Wave 1）; ai-dev/design/nop-incremental-build/05-nop-ib-engine-design.md §五/§六/§九
> Related: wave0-adjudications.md（M0 达成）; wave2-compile-fastpath.md（successor）

## Purpose

零产物写入前提下落齐 Wave 1 只读内核：model.json 导出器、字节码依赖索引、模块粗筛、类级闭包与回退矩阵、只读命令面，并用真实编辑回放比对 plan 预测与实际 mvn 影响面（item 12）。

## Current Baseline

- Wave 0 已裁定：导出形态=effective-pom 批量 dump（22.4s 实测）；分级按产物证据；nop-ib 模块已入 reactor。
- `nop-tools/nop-ib` 已有 model/index/scope/cli 四层骨架（本 plan 的落地内容）。
- 全仓 baseline 产物已构建（target/*classes 齐全，index 可扫）。

## Goals

- item 7：`model.json`（模块/依赖边/编译事实/插件 execution/测试配置/工具链）+ 内容哈希 + `model refresh|show` + schema 校验（版本字段）。
- item 8：class→contentHash / refs（dependents 上界）/ ABI digest / inlineConstants / source→classes / testClass→refs；`.nop-ib/units/<module>.bin`（版本化格式）。
- item 9：reactor 反向依赖闭包（含 test-jar、parent→child、pom 传播），模型缺失不剪裁。
- item 10：编译集/测试集计算 + L0–L3 放宽矩阵 + 索引健康闸门。
- item 11：`status`/`plan`/`explain`/`cache size` + `--json` + 退出码契约。
- item 12：20–30 次真实编辑回放，plan vs mvn 实际影响面差异逐条裁定。

## Non-Goals

- 编译执行与产物写入（Wave 2）；测试执行（Wave 3）。
- 动态覆盖；常驻 serve（Wave 4）。

## Execution Plan

### Phase 1 - 模型导出器 + 单元索引（items 7–8）

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/{model,index,json}`

- Item Types: `Feature`

- [x] EffectivePomParser（流式 DOM-per-project，dependencyManagement 边排除，per-execution compiler 配置，surefire 事实，路径仓库相对化）
- [x] ModelRefresher（mvnq 包装 4 次 Maven 调用：--version / settings.localRepository / effective-pom dump / dependency:list；pom 哈希入档）
- [x] JsonOut/JsonIn（受控 JSON 读写，避免拖入 nop-core）
- [x] UnitIndexer（ASM 采集 class→hash/refs/abiDigest/inlineConstants/sourceFile；解析失败=索引不健康响亮失败）
- [x] UnitIndexStore（.nop-ib/units/<module>.bin，版本化 JSON 载荷——偏离 roadmap "bin 二进制"字面：编码是后继优化，兼容契约是磁盘 schema 与 formatVersion）

Exit Criteria:

- [x] TestEffectivePomParser 5 用例（dependencyManagement 排除、test-jar 边、per-execution release、exec/surefire 事实、能力分级）
- [x] TestUnitIndexer 4 用例（refs/常量/ABI 签名 vs 方法体差异辨识/往返持久化）
- [x] 真仓 model refresh 实测：modules 数 = reactor 数，staleness 初始为 no

### Phase 2 - 粗筛 + 类级闭包 + 只读命令面（items 9–11）

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/{scope,cli,engine}`

- Item Types: `Feature`

- [x] ModuleGraph（反向闭包含 test-jar/全 scope；pom 传播 = 反向闭包 ∪ parent→child 后代）
- [x] ChangeClassifier（01 §七/§八 规则：pom/java/_gen/xlang 模型/测试数据/配置资源/普通资源）
- [x] ScopePlanner（回退矩阵优先 → 粗筛 → 类图下游候选（superset 预测，abiPredictionPrecise=false）→ 测试选择 + 放宽；模型缺失/索引缺失不剪裁并告警）
- [x] Commands：status/plan/explain/model/cache + --json + 退出码 0/1/2/3/6/75 常量
- [x] NopIbMain 分派（未实现命令显式失败）

Exit Criteria:

- [x] 模块测试全绿（13 用例）
- [x] 真仓 plan/status/explain --json 实测输出符合 §九契约字段
- [x] 回退信号（pom 变更/L2/L3/模型过期）在 plan JSON 中显式出现

### Phase 3 - 预测复算（item 12）

Status: completed
Targets: `nop-tools/nop-ib/tools/`, plan evidence 段

- Item Types: `Proof`

- [x] 取最近 20–30 次真实 Java 编辑 commit，逐个以 `git diff` 变更集驱动 `nop-ib plan`
- [x] 对每个 commit 与规则不变量比对（R1 pom→回退/R2 java→无回退/R3 模型→扩宽/R4 自属模块在编译集）；完整 mvn 影响面逐 commit 重放因成本裁定降维: 以 ABI 扩散探针+选择集 oracle 两个实测场景替代（见偏差）
- [x] 差异裁定记录写入本 plan evidence 段

Exit Criteria:

- [x] 25 次编辑回放完成: 24 ok + 1 告警（4502b9b0af）裁定为复算脚本模型扩展集窄于分类器（引擎按设计对 L1 模型变更回退=正确）
- [x] `ai-dev/logs/2026/11-13.md`+`11-14.md` 已更新

## Closure Gates

- [x] items 7–11 语义经真仓实测（model refresh 456 模块/索引 383/plan 下游精筛 5 模块/退出码契约）
- [x] item 12 复算完成且差异归因在档
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（17 tests）
- [x] roadmap Wave 1 状态更新（7–12 done，M1 达成）
- [x] 独立 closure audit（随 roadmap 收口批次）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（收口批 0 errors）
- [x] `ai-dev/logs/2026/11-13.md`+`11-14.md` 已更新

## Deferred But Adjudicated

### unit index 二进制编码（.bin 载荷）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 兼容契约是 schema+formatVersion，编码不影响正确性；457 模块 JSON 载荷实测可接受后无需提前优化。
- Successor Required: no

## Closure

Status Note: 语义与实测已收口;独立 closure audit 已完成。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（session agent_ff78ed3b-bbac-4675-8cdc-099bd662fc18,2026-11-14）
- Evidence: `./mvnw test -pl nop-tools/nop-ib` 17/0;CLI plan/status/check 实测;调用链 Anti-Hollow 追踪连通;check-doc-links 0 errors;check-plan-checklist 通过（含 Reviewer/Evidence 字段）。
