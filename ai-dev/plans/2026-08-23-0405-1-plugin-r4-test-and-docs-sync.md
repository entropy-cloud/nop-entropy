# 1. nop-plugin 定位反转 R4：测试矩阵终态对齐 + docs-for-ai 同步

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: R4（测试改造 + docs-for-ai 同步）
> Last Reviewed: 2026-08-23
> Source: `ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（R4 条目）；`ai-dev/design/nop-plugin/01-architecture-baseline.md`（2026-08-22 版，权威契约）；`ai-dev/logs/2026/08-23.md`（R3 冻结基线引用点 + R1 Ownership Deviation 回读义务 + R2 执行裁定记录）；R1/R2/R3 plan Closure 段（successor 义务清单）
> Related: 前置 `2026-08-22-2309-1-plugin-api-shrink-single-state-machine.md`（R1）、`2026-08-22-2309-2-manager-orchestration-simplification.md`（R2）、`2026-08-22-2309-3-compat-path-command-routing-regression.md`（R3）

## Purpose

R1-R3 已冻结反转后的实现面（单层六态 API、插件级编排、双轨兼容语义）。本 plan 把**测试面与使用者文档**对齐到该契约并收口全部 successor 义务：测试矩阵终态审计（死 fixture 清理 + 陈旧措辞修正 + roadmap 断言清单覆盖核对）、`docs-for-ai/` 四处同步（nop-plugin.md 全文重写 + INDEX/source-anchors/module-groups）、`ai-dev/design/` 回填（dsh 失效语义终核 03/04 + 01 锚点表 Ownership Deviation 回读修正）、（可选裁决）demo 依赖收窄。本 plan 结束时反转重构 R1-R4 全部收口，里程碑条件成立。

## Current Baseline

（2026-08-23 依据 live repo 核验）

- **R1-R3 状态**：三个 plan 均 `completed` 且有独立 closure audit 证据；roadmap R4 为唯一 `todo` work item（反转里程碑 `todo` 为派生态）。
- **测试树现状**（R1-R3 已完成主要改造，90/90 绿于 R3 closure 时点）：
  - api：`TestPluginDefaultMethods`（6 test，六态枚举/default 显式抛错/最小实现可编译）
  - manager：`TestPluginLifecycle`(14)/`TestCommandRoutingRegression`/`TestReconcileTopologicalOrder`(6)/`TestCoeffectReconcile`(7)/`TestCoeffectConfigMatching`(3)/`TestReloadPlugin`(4)/`TestChangeDetection`(3)/`TestPluginManager`(7)/`TestPluginXdef`(3，含 `testAttributeSetFrozen` 冻结守护)/`TestPluginCoeffectSpec`(4)/`TestHttpPluginResourceResolver`
  - support：`TestAbstractPlugin`(15)/`TestHelloCommand`/`TestHostCommand`
  - 已废止机制在测试树 grep 零命中：`createInstance|destroyInstance|IPluginInstance|InstanceState|hasActivatedInstanceByName|InstanceConfig|DEFAULT_INSTANCE_KEY`（TestParentHierarchy/TestPluginInstanceLifecycle 等已随 R1 删除）
- **测试侧残留债**（2026-08-23 二轮评审核验，grep 精确命中 6 文件 8 行）：
  - 死 fixture：`IsolatedCommand.java`（Javadoc 自述"per-instance 命令隔离测试 bean…同一定义的两个实例"）+ `agent-instance.plugin.xml` 中 `nopPluginCommand_isolated` bean 声明——manager 测试树无任何测试调用 "isolated" 命令（grep 仅命中声明处），为无消费者的死 fixture
  - 陈旧/失实注释（5 文件）：`HelloCommand.java:11`（"per-instance 命令路由测试 bean"）；`ConfigReaderBean.java:4-5`（现在时态描述已废止机制"实例配置域…实例容器…实例合并视图/实例覆盖全局/仅实例键"——现行 = 定义级 `DefinitionConfigProvider` 配置域）；`FailActivator.java:10`（**事实矛盾**：注释称"实例回退 DEACTIVATED…错误带实例 key 参数"，实际行为 = 激活失败置 `FAILED` 可重试，见 `TestPluginLifecycle` 失败阈值断言）；`AgentInstanceRecorder.java:9`（"实例生命周期事件记录器"）；`AgentInstanceActivator.java:11`（"自动注册为实例 effect"）
  - 保留不改：配置键名 `agent.only-instance` 与 fixture 标识符名（`AgentInstanceRecorder`/`agent-instance.plugin.xml`）——改名无行为收益且波及既有断言（裁定随 Phase 1 执行记录于 daily log）
- **docs-for-ai 滞后集**：
  - `docs-for-ai/03-modules/nop-plugin.md`：全文为反转前版本（两层状态机/多实例/实例配置域/per-instance 命令路由/reconcileInstances/HMR 快照重建），§源码锚点 头部有"全面重写归 roadmap R4"横幅
  - `docs-for-ai/INDEX.md:199`：条目描述仍为"两态生命周期/多实例/coeffect/getService/HMR/SHA256 校验"
  - `docs-for-ai/04-reference/source-anchors.md`：PLG-001（IPlugin 仍描述 getInstance/getInstances）、PLG-005（IPluginManager 仍描述 createInstance/parent/HMR 快照重建）、PLG-006（`checkChangedAndReload:320` 行号锚点漂移——实际 `:605`，且该行未反映 R2 过滤器修正（允许 ACTIVATED/中间态进入变更检测））、PLG-009（AbstractPlugin 仍描述 load+createInstance）均为反转前措辞；PLG-002 含"本表其余反转前措辞的重写归 roadmap R4"注记
  - `docs-for-ai/01-repo-map/module-groups.md:12`：仍写 `IPluginInstance` 契约与"实例生命周期"
- **ai-dev/design 待回填**：
  - dsh 失效语义终核：`03-coeffect-and-agent-example.md:290`（§5.3 统一注解"R4 阶段对照 cordis 源码终核后回填本文与 04 的对应行"）、`:299`、`:326`；`04-interface-comparison.md:50`、`:132`——五处"待 R4 终核"标记待 cordis 源码终核结论替换
  - Ownership Deviation 回读修正（R1 plan 裁定义务）：roadmap R2 条目文字已随 R2 closure 修正（Work Items 已记录执行裁定），**剩余对象 = 01-architecture-baseline.md 锚点表 R2 行（:351，仍写"parentToChildren 映射、级联销毁、快照/pending 重建逻辑归 R2 移除…"，与实际"随 R1 编译依赖删除、R2 为残留审计+重命名+错误码退役"不符）**；另 01 `:336`"待 R1 更正"措辞已过时（05 §七已含 2026-08-22 审计更正注解，01 侧只需同步措辞）
- **demo 现状**：`nop-demo/nop-quarkus-demo/pom.xml:236` 依赖 `nop-plugin-manager`；demo src 仅 `DemoPluginCommand.java` import `io.nop.plugin.api.{IPluginCancelToken,IPluginCommand}`；宿主 `app-demo.beans.xml:16` 注册 `nopPluginCommand_default` 兜底 bean；demo 全模块无 `io.nop.plugin.manager` 引用——收窄至 `nop-plugin-api` 编译面可行
- **R3 携带项**（docs 必须写明的 documented behavior）：jar 轨 aware `start(gav, config)` 在已 LOADED/ACTIVATED 时忽略 config（jar 轨 aware 无定义级配置域，`load(config)` 不消费 config）——两轨固有差异
- **冻结基线引用点**：plugin.xdef 属性集 = name（必填 `!string`）/requires（csv-set）/if-property（string）/activator（bean-name）+ 唯一子元素 `<beans>`，无 requires-service；机器守护测试 `TestPluginXdef.testAttributeSetFrozen`（`ai-dev/logs/2026/08-23.md` R3 段）
- 验证基线命令：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C`

## Goals

- 测试矩阵与单层六态契约**终态对齐**：死 fixture 清零、陈旧措辞归位定义级语义、roadmap R4 所列断言类（六态转换、门控激活/去激活、quiescence、HMR、INACTIVE 代理失效、requires 定义级 ACTIVATED 迁移断言）逐项核对并产出**覆盖对照表**（预期 R1-R3 focused tests 已覆盖，缺则补）
- `docs-for-ai/` 四处同步到反转后契约：`03-modules/nop-plugin.md` 全文重写（单层六态状态机 + 插件级依赖定位 + 兼容双轨 + jar 轨契约 + HMR 重放语义 + SHA256 保留），INDEX:199、source-anchors PLG 表、module-groups:12 同步；重写内容包含 R3 携带项（jar 轨 config 忽略 documented behavior）与冻结属性集引用
- `ai-dev/design/` 回填收口：dsh 失效语义**对照 cordis 源码终核**（03/04 五处"待 R4 终核"标记以终核结论+源码引用替换）；01 锚点表 R2 行按 Ownership Deviation 实况修正、01 `:336` 措辞同步——R1/R2/R3 plan 的全部 successor 义务清零
- （可选裁决，默认执行）`nop-quarkus-demo` 直接依赖收窄 `nop-plugin-manager` → `nop-plugin-api`，编译验证；若暴露真实 manager 依赖则回退并记录裁定理由
- closure audit 通过后 roadmap R4 → `done`，反转里程碑（R1-R4）派生 `done`

## Non-Goals

- 三模块 **main 源码功能变更**（R1-R3 已冻结实现面；本 plan 仅动测试/文档/demo pom。若测试审计暴露实现 live defect：显式记录并按 guide 裁定升级为 Fix item 或 successor plan，不静默降级、不顺手大改）
- `plugin.xdef` 属性集任何变更（冻结，有守护测试）
- jar 轨门控载体立项（R3 deferred，Successor Required: no）
- reconcile 触发频率优化、失败阈值参数配置化、updateConfig 不触发依赖方重门控（R2 deferred 项延续，非本 plan 义务）
- `05-artifact-loading-design.md` 修改（§七 已含正确反转注解，不动）
- 新测试框架/测试基建引入（沿用 JUnit 5 + 既有 fixture 风格）

## Scope

### In Scope

- `nop-plugin-manager`/`nop-plugin-support` 测试树：死 fixture 删除（IsolatedCommand + plugin.xml bean 声明）、陈旧 Javadoc/注释措辞修正、（若有缺口的）补充 focused tests
- `docs-for-ai/`：`03-modules/nop-plugin.md` 重写、`INDEX.md`（nop-plugin 条目行）、`04-reference/source-anchors.md`（PLG-001~009 表终态化）、`01-repo-map/module-groups.md`（核心框架行 plugin 子模块描述）
- `ai-dev/design/nop-plugin/`：`03`/`04` dsh 失效语义终核回填（五处）、`01` 锚点表 R2 行 + `:336` 措辞修正
- `nop-demo/nop-quarkus-demo/pom.xml`：依赖收窄（可选裁决项）
- roadmap Work Items 状态更新（closure 后）

### Out Of Scope

- 同 Non-Goals

## Execution Plan

### Phase 1 - 测试矩阵终态对齐

Status: completed
Targets: `nop-core-framework/nop-plugin/nop-plugin-manager/src/test/` 下 7 个文件——java/io/nop/plugin/test/ 下：IsolatedCommand.java、HelloCommand.java、ConfigReaderBean.java、FailActivator.java、AgentInstanceRecorder.java、AgentInstanceActivator.java；resources/_vfs/nop/plugin/test/ 下：agent-instance.plugin.xml（缺口时新增 focused test 文件）。（`TestPluginLifecycle`/`TestReloadPlugin` 非修改目标——2026-08-23 复核其注释已为定义级措辞，仅作为覆盖核对对象）

- Item Types: `Fix | Proof`

- [x] 死 fixture 清理：删除 `IsolatedCommand.java` 与 `agent-instance.plugin.xml` 中 `nopPluginCommand_isolated` bean 声明（删除前复核零测试引用；若复核发现引用，改为定义级单激活等价断言并记录裁定）——`Fix`
- [x] 陈旧/失实注释修正（Current Baseline 所列 5 文件）：`HelloCommand.java` Javadoc 改定义级路由表述；`ConfigReaderBean.java` 改定义级配置域（`DefinitionConfigProvider`）表述；`FailActivator.java` 修正为实际语义（激活失败置 `FAILED` 可重试、错误经定义级激活错误通道——**事实矛盾必须修**，非仅措辞）；`AgentInstanceRecorder.java`/`AgentInstanceActivator.java` 改单激活表述。config 键名与 fixture 标识符名保留（裁定随本 Phase 记录于 daily log）——`Fix`
- [x] roadmap R4 断言清单覆盖核对：逐项核对现有测试——六态转换、门控激活/去激活、quiescence、HMR（reload 重放 + checkChangedAndReload）、INACTIVE 代理失效、requires 定义级 ACTIVATED 迁移断言——产出**覆盖对照表**（断言类 → test 类#方法）写入 daily log；任何缺口补 focused test。对照表含两条映射注记：(a) roadmap 原文"两态转换"为反转前措辞，对应现契约 = 六态转换；(b) roadmap 交付物"删除 parent 层级/级联销毁/实例配置域相关测试"已随 R1 完成（Current Baseline grep 零命中即证据，对照表记"已完成于 R1"行）——`Proof`
- [x] 全量回归：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿——`Proof`

Exit Criteria:

- [x] `grep -rn "IsolatedCommand\|nopPluginCommand_isolated" nop-core-framework/nop-plugin --exclude-dir=target` 零命中（排除构建产物 stale class/surefire 报告；即 main + test 源树零残留）
- [x] 陈旧措辞门禁：`grep -rn "per-instance\|实例配置域\|实例容器\|实例合并视图\|实例覆盖全局\|仅实例键\|实例生命周期事件\|实例 effect\|实例回退" nop-core-framework/nop-plugin/*/src/test --include="*.java"` 零命中（2026-08-23 基线 = 8 行命中，全部位于本 Phase 修改/删除的 6 文件内——门禁可达零命中；fixture 标识符名不在禁词集）
- [x] 覆盖对照表存在于 daily log 且 roadmap 断言清单每项均有 test 类#方法对应；缺口为 0 或已补测试并通过
- [x] 新增（若有）测试断言真实行为（状态迁移/事件序/异常类型），非仅"编译通过+不抛错"的弱断言
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [x] **端到端验证**（适用性裁定：本 Phase 为测试整理，端到端链路已由 `TestPluginLifecycle.testEndToEndSingleActivationFlow`/`TestCommandRoutingRegression.testEndToEndLoadActivateInvokeDeactivate` 覆盖并在覆盖对照表中引用——不重复新建）
- [x] owner docs：No owner-doc update required（纯测试整理；docs-for-ai 重写归 Phase 2，裁定记录于 daily log）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs-for-ai 同步（nop-plugin.md 重写 + 三处索引/锚点）

Status: completed
Targets: `docs-for-ai/03-modules/nop-plugin.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/04-reference/source-anchors.md`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix | Proof`

- [x] `03-modules/nop-plugin.md` 全文重写，内容以 `01-architecture-baseline.md`（2026-08-22 版）+ live 代码为准：单层六态状态机（转换图、unload 守卫、FAILED 可重试、失败阈值暂停）、插件级 coeffect（requires = 依赖定义 ACTIVATED、if-property 全局配置宽松比较、reconcile 拓扑序：批量激活正拓扑/去激活逆拓扑/同 pass 先去激活组）、activator 参数传递模式、getService 激活态绑定代理（多候选规则、INACTIVE 快速失败）、**定义级命令路由**（含 getCommandBean 回退链：插件容器 → 宿主回退 → default 兜底）、兼容双轨（非 aware 旧语义零感知；aware start = load + activate / stop = deactivate + unload + 状态边界三断言）、**jar 轨契约**（无条件激活、激活回调跳过、R3 携带项：aware `start(gav, config)` 在已 LOADED/ACTIVATED 时忽略 config 为 documented behavior）、HMR（`checkChangedAndReload` 显式入口 + reload = deactivate→unload→load 重放 updateConfig 累积值→reconcile；jar 轨显式失败）、artifact 加载 + SHA256（现有正确内容保留）、冻结属性集（引用 `TestPluginXdef.testAttributeSetFrozen` 守护）、源码锚点表（终态文件清单，删 R4 横幅）——`Fix`
- [x] `INDEX.md` nop-plugin 条目描述更新为反转后定位（单层六态/插件级依赖/兼容双轨/HMR/SHA256）——`Fix`
- [x] `source-anchors.md` PLG 表终态化：PLG-001（IPlugin 单层六态 + activate/deactivate/getService）、PLG-005（IPluginManager：loadPlugin/unloadPlugin/activatePlugin/deactivatePlugin/getPlugin/getLoadedPlugins/reloadPlugin/reconcilePlugins）、PLG-006（描述改为"允许 ACTIVATED/中间态进入变更检测"+ 行号锚点刷新）、PLG-009（start/stop = load+activate / deactivate+unload）；移除 PLG-002"重写归 R4"注记——`Fix`
- [x] `module-groups.md` 核心框架行 plugin 子模块描述修正（删 `IPluginInstance`/“实例生命周期”表述）——`Fix`
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0——`Proof`

Exit Criteria:

- [x] `docs-for-ai/03-modules/nop-plugin.md` 中已废止机制描述清零：`grep -n "IPluginInstance\|InstanceState\|createInstance\|destroyInstance\|per-instance\|实例配置域\|parent 层级\|reconcileInstances" docs-for-ai/03-modules/nop-plugin.md` 零命中；R4 横幅移除
- [x] 文档-live 一致性抽查：六态枚举名、`IPluginManager` 方法面、`PluginState` 语义、错误码（ERR_PLUGIN_INACTIVE/ERR_PLUGIN_RELOAD_NOT_SUPPORTED 等）与源码逐项一致，抽查清单（文档段落 ↔ 源码 file:line）记录于 daily log
- [x] INDEX/source-anchors/module-groups 三处 grep `IPluginInstance|createInstance|两态生命周期|多实例`（nop-plugin 相关行）零命中。**注**：历史迁移信息（如 PLG-002 现有"原实例级 IPluginInstance 契约已删除"注记）须改写为不含已删符号名字面量的正向表述（如"getService/INACTIVE 语义并入 `IPlugin`（R1 反转）"）——信息保留但不依赖禁词
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ai-dev/design 回填（dsh 失效语义终核 + Ownership Deviation 修正）

Status: completed
Targets: `ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`、`ai-dev/design/nop-plugin/04-interface-comparison.md`、`ai-dev/design/nop-plugin/01-architecture-baseline.md`

- Item Types: `Decision | Proof | Fix`

- [x] dsh 失效语义终核（Decision + Proof）：对照 cordis 源码（GitHub `koishijs/cordis`，重点：ctx traceable proxy 实现、`ctx.get`/属性弱读路径、INACTIVE 错误抛出点；本地起点可参考 `ai-dev/analysis/2026-08/2026-08-21-dsh-plugin-system-reference.md`）确定 dsh 非活跃插件访问的真实语义（两路径并存/单路径/条件）；结论回填 `03:290`（§5.3 统一注解替换"R4 阶段对照 cordis 源码终核后回填"）、`03:299`、`03:326`、`04:50`、`04:132`——注解落款终核结论 + cordis 源码定位（文件路径/链接）——`Decision`+`Proof`
- [x] Ownership Deviation 回读修正（R1 plan 裁定义务）：`01` 锚点表 R2 行（`:351`）措辞改为实况——IPluginInstance 型结构（parentToChildren/级联销毁/快照/pending/isInstanceConfigMatched 签名）随 R1 编译依赖删除，R2 = 残留审计 + InstanceConfigProvider→DefinitionConfigProvider 重命名 + 宽松比较迁移 + 错误码族退役 + reconcile 拓扑序；与 roadmap R2 条目（已修正）及 R1/R2 plan Closure 记录一致——`Fix`
- [x] `01:336` "待 R1 更正"措辞同步为已完成表述（05 §七已含 2026-08-22 审计更正注解）——`Fix`

Exit Criteria:

- [x] `grep -rn "待 R4\|待终核\|R4 对照\|R4 阶段" ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md ai-dev/design/nop-plugin/04-interface-comparison.md` 零命中（模式覆盖五处标记的实际措辞变体；限定 03/04 避免误报 01 中合法的 R 系列编号注记）
- [x] `grep -n "待 R1 更正" ai-dev/design/nop-plugin/01-architecture-baseline.md` 零命中（`:336` 措辞同步项的可观测验证）
- [x] 终核结论有 cordis 源码定位支撑（文件路径或链接），核验过程（查了哪些源码位置、依据）记录于 daily log；若终核结论为"两路径并存确认"或"单路径更正"，03/04 五处表述随之统一且相互一致
- [x] `01` 锚点表 R2 行与 `ai-dev/logs/2026/08-23.md` R1 段 Ownership Deviation 记录、R2 条目执行裁定三方一致
- [x] **无静默跳过**：若 cordis 源码无法获得确定性结论（源码不可达/版本歧义），统一注解改为如实记录"终核未定 + 依据 + 保留两表述"并升级裁定（Deferred 显式化），不允许无依据地单方面改写结论
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - nop-quarkus-demo 依赖收窄（可选裁决，默认执行）

Status: completed
Targets: `nop-demo/nop-quarkus-demo/pom.xml`

- Item Types: `Decision | Proof`

- [x] Decision：`nop-plugin-manager` 直接依赖改 `nop-plugin-api`（现状依据 Current Baseline：demo src 仅 `DemoPluginCommand` import api；无 `io.nop.plugin.manager` 引用；宿主兜底 bean 不依赖 manager 类。评审核验补充：manager main 无 beans 资源发布、全仓除 demo/bom 外无模块依赖 manager——收窄三面安全）。执行后验证编译；若编译/装配暴露 manager 真实依赖（如 starter 传递需求），回退变更并在 daily log 记录保留理由——`Decision`
- [x] Proof：`./mvnw compile -pl nop-demo/nop-quarkus-demo -am -DskipTests` 通过。demo 侧测试现状 = `TestDemoBizModel`/`TestFluxYamlPages`（均非 plugin 相关，评审核验）——裁定 compile 为充分验证，不强制跑 demo 测试（执行者选择跑则一并记录结果，不作为本项门禁）——`Proof`

Exit Criteria:

- [x] demo pom 直接依赖为 `nop-plugin-api`，或 daily log 存在保留 `nop-plugin-manager` 的明确裁定与理由
- [x] `./mvnw compile -pl nop-demo/nop-quarkus-demo -am -DskipTests` exit 0
- [x] owner docs：No owner-doc update required（pom 依赖收窄不改契约面；module-groups 对 demo 无 plugin 依赖描述）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本 plan 为测试/文档/pom 收口计划（**非纯文档**：含 test 源码注释修正与 demo pom 变更，不改三模块 main 源码）。门禁按实际变更面保留：三模块全量测试、checkstyle（main，先例同 R1-R3）、demo 编译、doc-links、hollow-scan 复跑。

- [x] roadmap R4 断言清单覆盖对照表齐备且全部断言类有对应通过中的测试
- [x] `docs-for-ai/` 四处（nop-plugin.md/INDEX/source-anchors/module-groups）与 live 契约一致，废止机制描述清零
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/design/nop-plugin/` 03/04 五处终核标记（"待 R4/待终核/R4 对照/R4 阶段"措辞变体）清零；Ownership Deviation 修正与 R1/R2 记录三方一致
- [x] demo 依赖收窄落地或裁定记录在案
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项（含：测试审计若发现实现 live defect 已显式裁定归属）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（含文档-live 一致性抽查复核）
- [x] **Anti-Hollow Check**（适用性裁定）：本 plan 无 main 代码变更，接线/调用链验证以 Phase 1 覆盖对照表引用的既有端到端测试为准；`node ai-dev/tools/scan-hollow-implementations.mjs <三模块 src/main> --severity high` 退出码 0 复跑确认
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过
- [x] `./mvnw checkstyle:check -Dcheckstyle.config.location=file://<repo-root>/checkstyle.xml -pl nop-core-framework/nop-plugin/nop-plugin-api,nop-core-framework/nop-plugin/nop-plugin-manager,nop-core-framework/nop-plugin/nop-plugin-support -am` 通过（**main 源码门禁**，R1-R3 同先例保持。如实说明：仓库 checkstyle 默认不含 test 源码——`includeTestSourceDirectory=true` 仅存在于根 pom 中被注释掉的 pluginManagement 死配置，qa profile 亦未启用且 `failOnViolation=false`，test 源码无仓库级 checkstyle 硬门禁；本 plan 的 test 改动均为 Javadoc/注释级，风格一致性由人工审查与全量测试编译保证，此裁定记录于 daily log）
- [x] roadmap Work Items：R4 → `done`、反转里程碑（R1-R4）→ `done`（closure audit 通过后执行）

## Deferred But Adjudicated

### reconcile 触发频率优化与失败阈值参数配置化

- Classification: `optimization candidate`
- Why Not Blocking Closure: R2 deferred 延续项；按事件触发 + 实现层常量语义正确，性能与配置化不影响 supported baseline。
- Successor Required: `no`

### updateConfig 热应用不触发依赖方重门控（01 §五已知限制 1）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 01 §五终裁注解明文化的设计限制（当前无此需求），非缺陷。
- Successor Required: `no`

### jar 轨门控载体

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 01 §二 jar 轨契约裁决（门控恒空集、激活回调跳过）为 supported baseline；未来需要门控须先为其定义载体立项（R3 deferred 延续）。
- Successor Required: `no`

### cordis 失效语义终核未定（条件性——仅当 Phase 3 终核无法获得确定性结论时生效）

- Classification: `watch-only residual`
- Why Not Blocking Closure: dsh 失效语义是对照性注解（描述 dsh 行为，非 Nop 契约）；Nop 侧语义单一且严格（任何路径调用已 deactivate plugin 的代理一律抛 INACTIVE）不受影响。终核未定时 03/04 统一注解如实记录"未定 + 依据 + 保留两表述"，无信息丢失，不稀释 Nop 侧契约。
- Successor Required: `no`（若未来 cordis 语义演进需复核，届时按 01 §九的立项原则重新评估立项）

## Non-Blocking Follow-ups

- 实现产物提交交由 mission-driver 统一 commit（W1-W7/R1-R3 先例）。
- `agent.only-instance` 等 fixture config 键名保留不改（Phase 1 裁定，无行为收益）。

## Closure

Status Note: R1-R3 已冻结反转后实现面，本 plan 把测试面与使用者文档终态对齐并收口全部 successor 义务。四个 Phase 全部落地：死 fixture 清零 + 陈旧/失实注释归位定义级语义（覆盖对照表缺口=0）；docs-for-ai 四处同步到反转后契约且废止机制描述清零；dsh 失效语义对照 cordis 4.x 源码终核（确定性结论）回填 03/04 五处 + Ownership Deviation 三方一致修正；demo 依赖收窄至 nop-plugin-api 编译验证通过。反转重构里程碑（R1-R4）条件成立。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh read-only explore session，task id `ses_fd4c50d65ffeE2UbAdSLgLvfVZ`）
- Evidence:
  - 16 项审计全部 PASS（每条 Exit Criterion/Closure Gate 的 live 验证 + 证据引文），verdict = **CLOSURE APPROVED**。要点：
    - Phase 1：IsolatedCommand 零命中（文件已删）、陈旧措辞 grep 零命中、覆盖对照表 6 断言类齐备且抽检 16/16 映射测试方法实存、FailActivator Javadoc 为 FAILED 态语义
    - Phase 2：nop-plugin.md 废止机制零命中 + R4 横幅移除、三索引零命中、文档-live 五项抽查一致（PluginState 六态/IPluginManager 8 方法/checkChangedAndReload:605/AbstractPlugin 仅 UNLOADED 消费 config/错误码）
    - Phase 3：03/04 终核标记零命中、01 "待 R1 更正"零命中、统一注解为确定性结论含 cordis 源码定位、01 R2 行三方一致
    - Phase 4：pom 直接依赖 nop-plugin-api、demo src 仅 api import
    - Plan 维护：四 Phase Status=completed、Phase 1-4 内 checkbox 0 残留
    - Deferred 诚实性：条件性 deferred（终核未定）未触发（确定性结论已获）；无 in-scope live defect 被静默降级
  - `node ai-dev/tools/scan-hollow-implementations.mjs <三模块 src/main> --severity high` 退出码 0（0 findings）
  - `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` BUILD SUCCESS（0 failures/errors）
  - `./mvnw checkstyle:check -Dcheckstyle.config.location=file://<repo>/checkstyle.xml -pl <三模块> -am` exit 0（main 源码门禁）
  - `./mvnw compile -pl nop-demo/nop-quarkus-demo -am -DskipTests` BUILD SUCCESS
  - `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（见 daily log 收口条目）
  - Anti-Hollow：本 plan 无 main 代码变更；端到端链路以覆盖对照表所引 `TestPluginLifecycle#testEndToEndSingleActivationFlow` / `TestCommandRoutingRegression#testEndToEndLoadActivateInvokeDeactivate` 为证（audit 第 4 项抽检实存）
- Roadmap：R4 work item `planned` → `done`；反转里程碑（R1-R4）`todo` → `done`（派生态）

Follow-up:

- no remaining plan-owned work（R1-R4 全部收口；Deferred But Adjudicated 四项均 watch-only/optimization/out-of-scope，Successor Required: no）
- 实现产物提交交由 mission-driver 统一 commit（W1-W7/R1-R3 先例）
