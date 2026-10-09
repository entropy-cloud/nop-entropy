# 2348 WI2 XDSL 最佳实践检查第一层（nop-lint XML 规则集）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI2 行，M1 检查门）
> Related: plan 2347（WI1 契约台账——表 B 是本规则集的同源判定面）；`nop-lint/docs/rule-catalog.md`

## Purpose

落地 XDSL 最佳实践检查的 L1 层：nop-lint XML 静态规则集（warning 上线），匹配 `.stream.xml` 声明面，与 WI1 矩阵表 B 同源，作为 WI4 门禁接线的规则供给方。

## Current Baseline（2026-10-09 live 核对）

- nop-lint 平台规则 70 条（65 Java + 5 XML：`nop-bean-naming`/`nop-orm-icons`/`nop-orm-unique-key`/`nop-orm-mandatory-default`/`nop-xbiz-auth-not-sole-guard`）；`TestProductionRuleCount` 断言 70 + 显式 id 集；`TestNopRuleSuites` 断言 suites 发现数 = `EXPECTED_RULE_IDS.size() + XNODE_RULE_IDS.size() + 2`（XNODE_RULE_IDS 现为 5 个无斜杠 id）。
- 规则格式：`.rule.yml`（id/language/severity/message/rule/xscript/metadata——metadata 必须 category/autoFixable/version/source 齐备，TestNopRuleSuites 逐条断言）；XML 规则用「pattern 锚定 + xscript 判定」通道。**xscript 脚本面是 `NodeWrapper`**：可用 `kind/text/attrValue/child(field)/children()/children(kind)/ancestor(kind)/descendant(kind)/siblings()/range()`——无 `parent()`、无属性名枚举、`descendant(kind)` 只返回首个 pre-order 命中（null=不存在，恰好是存在性判定语义）；pattern 不限定 root（`<stream/>` 匹配任意深度的 stream 元素，root/作用域守卫靠 `node.ancestor('stream')` 判定）。xscript 语言支持 let/if/for-of/字符串 `startsWith/indexOf/matches(Java 正则)`（nop-bean-naming 等先例）。**已裁定能力边界**：xscript 无多后代枚举（TestProductionRuleCount:132 finally-missing-close 先例）——全部规则按「固定元素 kind + 固定属性名候选面」设计，不做全树属性扫描。
- 扫描面：`TargetScanner.EXTENSION_TO_LANGUAGE_ID` 按末段扩展名映射（`foo.stream.xml` → xml）——`.stream.xml` 已被 xml 语言覆盖。
- 规则目录 `nop-lint/docs/rule-catalog.md` 由 `ai-dev/tools/gen-lint-rule-catalog.mjs` 确定性生成，当前 `--check` 绿（70 rows in sync）。
- suite 测试基建：`suites/nop/<rule>/<rule>.rule.yml`（`x:extends` 主规则）+ `valid/*.stream.xml`（零诊断）+ `invalid/*.stream.xml` + `*.stream.expect`（逐条 line/ruleId/messageContains）。
- WI1 矩阵表 B 的 fail-fast 面与钉子分布（同源判定基准）。

## Goals

- 6 条 `nop-xdsl-*` XML 规则（全部 warning 上线，Rule 4 两阶段）覆盖 R5/R7/R8/R9/R10/R12 的 L1 静态面；R13 的 L1 半边经机械可行性裁定移交（见 Non-Goals）。
- 每条规则配 suite（valid + invalid + expect 正控自测）。
- 两处 census 断言同步 + 规则目录再生成过 `--check`。

## Non-Goals

- **R13 测试接线静态面不在本 WI 落 nop-lint 规则**（机械可行性裁定）：L1 单文件 AST 节点模型不含文件路径与场景注册表，`fixture 清单 ↔ 测试类注册表双向比对` 在 nop-lint 内无机械判定信号；且场景注册表 WI21 才建立，本 WI 落规则只能是永不触发的空壳（违 plan guide #24）。该半边随 WI21 首个注册表落地时以 check-*.mjs 仓扫工具实现，roadmap 检查规则表 R13 行的层标记同步微调（L1+L2 → CI+L2）。此为显式裁定非静默跳过。
- 不升 error（warn→error 两阶段，消号归 WI4）。
- 不动 nop-lint core 引擎（纯规则供给）。
- **命名裁定注记**：roadmap WI2 行写 `nop/xdsl-*`（斜杠形态），落地面为无斜杠 `nop-xdsl-*`——与既有 5 条 XML 规则命名惯例（nop-bean-naming 等）一致，且斜杠 id 会落入 TestNopRuleSuites 的 categoryOf Java 通道。落地面 id 在 user-guide 矩阵 L1 映射中登记，roadmap 文本不回改。

## Scope

### In Scope

- 6 个规则文件 `nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/nop/nop-xdsl-*.rule.yml`。
- 6 个 suite 目录（`src/test/resources/_vfs/test/lint/suites/nop/<rule>/`）。
- `TestProductionRuleCount`（70→76 + id 集）与 `TestNopRuleSuites`（XNODE_RULE_IDS +5…+6 计数断言）同步。
- `gen-lint-rule-catalog.mjs` 再生成 + `--check` 门禁；`docs-for-ai/03-modules/nop-stream-user-guide.md` 矩阵与 WI1 表 B 加 L1 规则列注记。

### Out Of Scope

- L2 模型感知 lint（WI3）；CI 接线与存量消号（WI4）；任何 stream 模块代码变更。

## Execution Plan

### Phase 1 - 6 条规则 + suite 正控自测

Status: completed
Targets: `nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/nop/`、`.../test/.../suites/nop/`

- Item Types: `Feature`

- [x] `nop-xdsl-watermark-required`（R5 L1，warning）：pattern any-of `<window/>`/`<cep/>`；xscript `let root = node.ancestor('stream')` 为 null 即返回（非 stream 模型守卫），`root.descendant('timestampsAndWatermarks') == null` 即 report（descendant 返回首个命中或 null——存在性语义）。语义 = 模型含 window/cep 但无任何水位供给节点。计数口径：**逐命中节点报**（当前语料 1 文件 1 节点 = 1；未来多窗口文件产 N 条诊断，WI4 台账按诊断条数对齐）。
- [x] `nop-xdsl-credential-plaintext`（R7，warning；**固定候选面裁定**——xscript 无属性名枚举与全树遍历，全树敏感属性扫描机制不可实现，落「固定 kind + 固定敏感属性名」子面）：pattern any-of `<param/>`/`<entry/>` + `<source/>`/`<sink/>`：param/entry 判 `name`/`key` 属性值 matches 敏感正则（`(?i).*(password|secret|token|accesskey|secretkey).*`）且 `defaultValue`/`value` 非空且非 `credential:` 前缀即 report；source/sink 判固定属性名（password/secret/accessKey/secretKey/token）attrValue 非空且非 `credential:` 前缀即 report。消息点名 `credential:{id}#{field}` 形态。非候选属性名的敏感面由 L2（WI3，模型感知有完整配置面）兜底——显式收窄非静默。存量 0。
- [x] `nop-xdsl-delta-derivation`（R8，warning；**收窄 L1 子面声明**——roadmap 信号列的 fingerprint/拓扑重复度比对是跨文件面，L1 单文件可判定子面 = 派生文件整体重写父拓扑）：pattern any-of `<transforms/>`；xscript 判 `node.attrValue('x:override')=='replace'` 且 `node.ancestor('stream') != null` 且 ancestor 的 `attrValue('x:extends')` 非空即 report。仓内真实 delta 习语是 x:extends + 按 id 覆写（零 x:override 先例），存量 0。
- [x] `nop-xdsl-edge-partition`（R9 L1，warning）：pattern any-of `<edge/>`；xscript 判单元素可判定子面——(a) partition==HASH 且 keyExpr 为 null；(b) partition!=HASH 且 keyExpr 非空；(c) partition==REBALANCE 或 BROADCAST 即报（**裁定：声明即报**——builder 对两值恒 typed 拒绝，DSL 无消费方机制，与构建期语义同源；roadmap「须有消费方」的图判定归 L2）；(d) HASH 指向 keyBy/union/join 的图判定不做（需拓扑走查，归 L2）。存量 0。
- [x] `nop-xdsl-transform-naming`（R10 L1，warning）：pattern any-of `<source/>`/`<sink/>`/`<map/>`/`<flatMap/>`/`<filter/>`/`<reduce/>`/`<keyBy/>`/`<union/>`/`<window/>`/`<aggregate/>`/`<process/>`/`<cep/>`/`<custom/>`/`<sideOutput/>`/`<join/>`/`<timestampsAndWatermarks/>`；xscript **先过脚本体守卫**（`id` 与 `bean` 均为 null 即 map/filter 等 xpl 函数体内的同名子元素，静默返回——否则仓内约 40-50 处函数体 `<source>` 全部误报），再判：(a) id 非空且不匹配 `^[a-z][a-z0-9]*(-[a-z0-9]+)*$` 即 report；(b) source/sink（transform 级，有 id）缺 `name` 属性即 report。description 维度不进本轮 warning 面。**存量口径预注记**：roadmap 基线 16 按「8 个 distinct 非 kebab id + 8 未命名端点」计；按诊断站点实扫为 21（13 处坏 id——roadmap 名单漏 tagMap/mainSink/tagSink，srcL/srcR 各 2 文件——+ 8 处未命名），WI4 台账以站点口径登记并注明差异。
- [x] `nop-xdsl-undeclared-consumer`（R12 L1，warning；**固定 kind/attr 候选面**——全树扫描机制不可实现，按 WI1 表 B 逐面锚定）：pattern any-of 六注册表+回调元素（`<streams/>`/`<sideInputs/>`/`<environments/>`/`<requirements/>`/`<checkpointParticipants/>`/`<coders/>`/`<onStart/>`/`<onEnd/>`/`<onError/>`，命中即报）+ 属主元素判 attr/child——`<strategy/>`（attr `triggerId` 非空 或 attr `accumulationMode`=='ACCUMULATING_AND_RETRACTING'）、`<window/>`（child `triggerId` 存在 或 attr `parallelism` 非空）、`<edge/>`（attr flowControlPolicy/queueCapacity/receiveWindow/packetSize 任一非空）、`<source/>`/`<sink/>`（child `params`/`outputType`/`inputType` 存在 或 attr `maxParallelism` 非空非'0' 或 attr `consistencyCapability` 非空非'AT_LEAST_ONCE'）、`<custom/>`（child `params`/`source` 存在）——全部先过 `node.ancestor('stream')` 守卫。与 R9 的 partition/keyExpr 面、R5 水位面不重叠。存量 2（负控夹具，WI4 白名单化）。
- [x] 每规则一个 suite：`valid/` 至少 1 个干净 `.stream.xml`、`invalid/` 至少 1 个坏样例 + `<name>.stream.expect`（expectPathOf 剥末段扩展名拼接）；R5/R7/R8 各加边界 valid（有水位 window 的模型不报 / credential: 引用形态不报 / 无 x:extends 的 transforms x:override=replace 不报）；R10 加函数体 `<source>` 负控（无 id/bean 的体内 source 不报）。规则 metadata 齐：category: nop、autoFixable: false、version: "1.0"、source 指向本 plan（TestNopRuleSuites 逐条断言 language=XML + version + metadata 非空）。
- [x] 规则文件头注释标明对应 R 规则号、WI1 表 B 行、放行时退役义务（随 WI5–WI16 放行同步删除对应判定分支）。

Exit Criteria:

- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am -Dtest='TestNopRuleSuites,TestProductionRuleCount' -Dsurefire.failIfNoSpecifiedTests=false` 绿（census 同步后；-am 链上无匹配模块需该 flag——10-02 日志先例）
- [x] 6 个 suite 全部发现且 green（suite 数断言同步 +6）
- [x] 每规则至少一个 invalid 正控断言（已知坏样例被报）+ 至少一个 valid 负控（合法形态不误报）
- [x] 规则作用域守卫：全部规则在非 stream 模型 XML 上零诊断（suite valid 内含一个 beans.xml 形态负控或 xscript root 守卫经 invalid 边界样例证明）
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - census 同步 + 目录再生成 + doc 注记

Status: completed
Targets: `TestProductionRuleCount`、`TestNopRuleSuites`、`nop-lint/docs/rule-catalog.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Proof`

- [x] `TestProductionRuleCount`：EXPECTED_IDS +6 个 `nop-xdsl-*` id（**不得加进 TestNopRuleSuites.EXPECTED_RULE_IDS**——那里断言 language=Java 且走 categoryOf）、70→76、注释补 WI2 行
- [x] `TestNopRuleSuites`：XNODE_RULE_IDS +6（无斜杠 id 走该集合与硬编码 `/nop/lint/rules/nop/` 路径，不经 categoryOf）、计数断言注释补 WI2 行
- [x] `node ai-dev/tools/gen-lint-rule-catalog.mjs` 再生成 → `--check` 绿（76 rows in sync）→ `self-test` 正控通过；**先修 `gen-lint-rule-catalog.mjs` self-test 硬编码**（:132 附近 `rules.length !== 70` → 76，连带更新过时文案「62-rule library」）——否则 self-test exit 2
- [x] user-guide WI1 矩阵表 B 追加一列注记或在矩阵尾部加一段「L1 静态规则映射」：6 规则 id ↔ R 规则号 ↔ 表 B 行的对应关系 + R13 半边移交说明

Exit Criteria:

- [x] 两处 census 断言同步且绿
- [x] `node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 退出码 0；`self-test` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 存量试扫与收口

Status: completed
Targets: nop-lint-nop

- Item Types: `Proof`

- [x] 用新规则对仓内 `.stream.xml` 试扫（CLI 或 RuleTestRunner 途径），命中数与基线对表——预期 R5=1、R10=21（站点口径；roadmap 16 为 distinct-id+端点混合口径，差异已预注记）、R12=2、R7/R8/R9=0；偏差须解释并记入 plan 执行发现
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am -Dtest='TestNopRuleSuites,TestProductionRuleCount' -Dsurefire.failIfNoSpecifiedTests=false` 绿
- [x] 独立 closure audit

Exit Criteria:

- [x] 试扫结果对表完成且偏差有解释
- [x] 模块全量测试通过
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] 6 条规则落地且 suite 全绿（正控+负控）
- [x] R13 半边移交裁定已记录（plan + 矩阵注记 + roadmap 检查规则表层标记微调）
- [x] 两处 census + 规则目录 `--check` 同步
- [x] 存量试扫对表完成
- [x] 受影响 owner docs 已同步（user-guide 矩阵 L1 映射 + nop-lint 规则目录为生成物）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 通过
- [x] `node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2348-wi2-xdsl-lint-l1-rules.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI2 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

（无——R13 半边为显式裁定移交 WI21，非 deferred：其判定信号（注册表）在 WI21 才存在）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 6 条 nop-xdsl-* XML 规则（warning 上线）+ 6 suite 正控自测 + census 双同步（76）+ 目录门禁 + 存量试扫对表（R5=1/R10=21/R12=2/其余 0，零偏差）全部落地；R13 L1 半边移交裁定已记录（roadmap R13 行 CI+L2 + user-guide 注记）。独立 closure audit APPROVE（0 Blocker/0 Major/1 Minor 流程项已随收口补齐 + 2 Nit）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_c3314554-3404-4d6c-b6bb-1ab2ee75ee4e（fresh session，未参与实现）
- Audit Session: agent_c3314554-3404-4d6c-b6bb-1ab2ee75ee4e
- Evidence:
  - Phase 1：靶向测试 74/74 绿（audit 自行运行）；6 suite 齐（valid/invalid/expect + R5/R7/R8 边界负控 + R10 函数体负控）；规则内容审查 (a)-(e) 全 PASS（固定候选面/函数体守卫/children(kind).size()/FORWARD 默认语义/size() 循环）
  - Phase 2：census 双同步（TestProductionRuleCount 76 + id 集；TestNopRuleSuites XNODE +6）；--check exit 0（76 rows in sync）+ self-test 通过；check-doc-links --strict exit 0；全仓无硬编码 70 残留
  - Phase 3：audit 独立复现存量试扫——语料 38 份、R5=1（test-param-agg）、R10=21 站点（坏 id 名单逐一吻合）、R12=2（负控夹具）、R7=R8=R9=0，零偏差；模块全量 93/93 绿
  - diff 范围：nop-stream 零变更；并发 plan 40 会话产物已正确区分
  - `node ai-dev/tools/check-plan-checklist.mjs` exit 0
  - Anti-Hollow：每规则 invalid 正控（已知坏样例被报）+ valid 负控（合法形态零诊断）；R7/R12 固定候选面为显式收窄非静默（xscript 能力边界，plan 2348 修订段与规则头注释均记录）
  - Deferred 项分类检查：无 deferred；R13 半边为显式裁定移交（非 deferred）

Follow-up:

- no remaining plan-owned work（nop-lint-core/graphql 的 62 时代陈旧注释为并发历史遗留 Nit，不属本 plan）
