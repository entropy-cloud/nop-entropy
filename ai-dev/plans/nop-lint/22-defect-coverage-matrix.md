# 22 核心缺陷类清单与覆盖矩阵（roadmap item 6）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 6](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [PMD/EP coverage manifest](../../../nop-lint/nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml) · [design 11](../../design/nop-lint/11-performance-profiles.md)（L1–L4 能力面）

## Purpose

建立 roadmap 的中心工作底座：按核心缺陷类（资源泄漏 / 空指针 / 吞异常 / 错误处理契约 / 并发 / 安全面 / 注入面 / 数据流 bug）逐类盘点现有规则、manifest 机制 tier、机制缺口与优先级，矩阵落统一账本。产出直接输入 item 7（资源泄漏落地）与 item 8（空指针裁定）。

## Current Baseline（实测 2026-09-28）

- 规则库 69 条分布：antipattern 8 / quality 30 / security 7 / nop 8 / api 6 / exception 10。
- manifest 186 条：tier1 33 / tier2 134 / tier3 16 / out-of-purpose 3；机制面 L1/L2 pattern 168（含 1 条 L2 类型分析）、L3/L4 18。
- 引擎能力面（design 11）：L1 pattern/xscript（语法面）、L2 类型（TypeResolver）、L3 dataflow（方法内 def-use + 常量传播）、L4 semantic、SCOPE、METRICS——均已在引擎落地并可经 `requires` 声明。
- 已知缺口（roadmap 预判，本计划矩阵化核实）：资源泄漏（acquire/release 路径配对）与空指针解引用流（null-flow）在 L3 之上未覆盖。
- 各缺陷类现有规则盘点（**按 id 命名空间归类**——与目录前缀存在 4 条无前缀/16 条 nop 前缀的差异，矩阵以 id 为口径；**规则↔缺陷类允许多对多**，catch-npe 同时入吞异常与空指针两行）：
  - 吞异常/错误处理契约：exception/empty-finally-block、nop/no-empty-catch、nop/silent-swallow（error）、nop/no-log-getmessage、nop/no-raw-exception、exception/no-catch-throwable、antipattern/throw-in-finally、antipattern/catch-npe、exception/no-raw-throws（item 4a）
  - 空指针（语法面）：exception/equals-null、exception/no-throw-npe、exception/throw-null、antipattern/catch-npe、quality/no-return-null——**解引用流（判空后复用、参数判空、三元判空链）无 L3 null-flow 规则**（no-self-compare/collection-size-nonnegative 按分面表裁定属数据流面，不入本行——与账本分面表保持一致）
  - 并发：antipattern/empty-sync-block（no-proxy-hostile-method 按分面表属平台不变式面，不入本行）——**锁获取/释放配对、double-checked locking、wait/notify 面无规则**
  - 安全面：security/ 7 条（no-class-forname、no-des-encryption、no-hardcoded-crypto、no-md5-digest、no-runtime-exec、no-sensitive-literal、no-hardcoded-iv）+ nop-xbiz-auth-not-sole-guard（分面理由安全面，脚注入行）
  - 注入面：security/no-runtime-exec（命令注入）、security/no-class-forname（反射加载）——**SQL 注入拼接面（动态拼接进 SQL 调用）无规则**（与 no-sensitive-literal 的字面量泄漏面相邻不同）
  - 资源泄漏：**acquire/release 配对面零规则**；外围资源面 3 条（quality/no-finalize、exception/empty-finally-block、antipattern/double-brace-init）——roadmap 预判精确化
  - 数据流 bug：quality/self-assigned-local、quality/unused-local-variable、quality/no-constant-condition、quality/random-mod、quality/string-literal-equality、quality/collection-size-nonnegative、quality/no-self-compare、quality/self-comparison、quality/self-equals、exception/equals-null（多对多）
  - **平台不变式（roadmap Purpose 遗漏类，本矩阵补第 9 行）**：antipattern/system-exit、print-stack-trace、api/no-nonslf4j-logger-call、no-proxy-hostile-method、nop/no-raw-exception、quality/no-system-out、no-transactional-annotation、no-native-method、no-direct-datasource-inject、no-vfs-violation、query-limit-required、nop-bean-naming、nop-orm-icons、nop-orm-mandatory-default、nop-orm-unique-key、nop-xbiz-auth-not-sole-guard、api/bizmodel-dao-access、bizmodel-safe-api、api/ibiz-missing-annotation、ibiz-missing-context
  - **正确性/逻辑契约（补第 10 行）**：quality/no-clone-without-cloneable、clone-return-type-mismatch、proper-clone-implementation、covariant-equals、empty-while-body、no-branching-in-loop-body、no-finalize（多对多）、double-brace-init（多对多）
- 统一账本门禁 RULE_FACET_CENSUS 现值 73（live 69 + remove 4）。

## Goals

- 统一账本新增"核心缺陷类覆盖矩阵"节：8 个缺陷类 × {现有规则 / manifest 机制 tier / 机制缺口 / 优先级}，逐格证据化。
- 矩阵结论直接给 item 7/8 的机制裁定输入（资源泄漏、空指针两行的缺口描述精确到所需能力面）。
- 账本门禁扩展看守矩阵节（缺陷类钉名 + 优先级词表）。

## Non-Goals

- 落任何新规则（item 7/8/10 的职责）。
- 改 manifest 行（机制前瞻不动）。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`：核心缺陷类覆盖矩阵节（8 行钉名）。
- `ai-dev/tools/check-lint-tool-replacement-ledger.mjs`：矩阵节 checker（钉名 + 优先级 enum）+ self-test。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` item 6 状态 + `ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- 新规则/引擎机制落地；manifest 行变更。

## Execution Plan

### Phase 1 - 矩阵落账本 + 门禁扩展

Status: completed
Targets: 统一账本、账本门禁

- Item Types: `Decision` + `Proof`

- [x] 矩阵节 `## 核心缺陷类覆盖矩阵` 落账本：**10 行钉名**（roadmap 8 类 + 平台不变式 + 正确性/逻辑契约两个 Purpose 遗漏类——item 6 状态翻转时在 roadmap 行补一句说明），五列表头钉死（**缺陷类 | 现有规则**为 checker 锚），优先级词表中性 {P1, P2, P3}（理由入机制缺口/备注格）；规则↔缺陷类多对多合法；矩阵刷新归属条款入节注：**item 7/8/10 落新规则时对应行由该 plan 同步更新（与 RULE_FACET_CENSUS 同责）**
- [x] 逐格证据化：现有规则列 = 精确规则 id 清单；机制缺口列 = 所需引擎能力面描述（精确到 L 层与判定形态）
- [x] 账本门禁扩展：矩阵节 checker（锚 (缺陷类, 现有规则) 显式传 firstHeaderCell、10 类钉名、优先级 ∈ {P1,P2,P3}、机制缺口非 —、**现有规则列逐 id 交叉校验 live 规则集（scanLiveRuleIds 已在 main 可用）**）+ self-test + 负控
- [x] roadmap item 6 状态翻转（todo→planned→done 时点同前序；顺手修正 roadmap 行内"已有 6 条 security"为 7 条——no-hardcoded-iv 为 item 4a 新增）

Exit Criteria:

- [x] 矩阵 8 行落账本且逐格证据化；账本门禁主检查 + self-test exit 0；负控（非法优先级/缺行）exit 1
- [x] 资源泄漏/空指针两行的机制缺口描述可直接作为 item 7/8 的裁定输入（引用 design 11 能力面词汇；资源泄漏行措辞 = acquire/release 配对面零规则 + 外围 3 条）
- [x] 六门禁 + doc-links strict 全绿
- [x] `ai-dev/logs/2026/09-28.md` 条目完整
- [x] No owner-doc update required

## Closure Gates

> 纯文档 + 门禁脚本计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 矩阵成立且与 live 规则库/manifest 一致（抽验 3 格）
- [x] 全部门禁绿
- [x] roadmap item 6 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/22-defect-coverage-matrix.md --strict` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 矩阵 10 行落账本（8 roadmap 类 + 平台不变式/正确性两个 Purpose 遗漏类）逐格证据化，门禁扩展（钉名 + 优先级词表 + 机制缺口非 — + live 规则集交叉校验 + self-test + 负控）全绿。首轮独立 closure audit REJECT 三项（plan 生命周期漂移 / 空指针行 tier 格证据失实 / plan 21 遗留 catalog 未再生）已全部修复并复验：catalog --check 转绿、tier 格改为如实表述（null 相关行均 tier1，null-flow 无 manifest 行）、Phase 1 completed 全勾。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_19617425-9287-4882-8565-e12338f86303（fresh session closure audit，首轮 REJECT）
- Audit Session: agent_19617425-9287-4882-8565-e12338f86303
- Evidence:
  - 矩阵本体 10 行钉名/5 列表头/多对多/design 11 能力面词汇对齐——audit 逐格抽验 4 格全部实存
  - 规则 id 全 id 口径：audit 抽验 7 个 id（含 no-raw-exception→nop/no-raw-exception 跨目录例）全部一致
  - 门禁：主检查 + self-test exit 0（10 matrix rows）；checker live 交叉校验实读确认；audit 负控自做（ghost id → exit 1 → 还原）
  - audit REJECT 三项修复：①plan Phase 1 completed + 全勾；②空指针行 tier 格改如实表述（null-flow 无 manifest 行）；③catalog 再生成（plan 21 遗留）--check exit 0
  - audit Minor（五门禁/六门禁计数口径漂移）已消解（日志改为门禁清单列举）
  - 资源泄漏/空指针机制缺口格引 design 11 L3 能力面 + 判定形态三要素——item 7/8 裁定输入成立
- Follow-up:

Follow-up:

- item 7/8/10 落新规则时同步更新矩阵对应行（刷新归属条款在矩阵节注）。
