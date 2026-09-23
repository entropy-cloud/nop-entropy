---
status: active
mission: nop-lint
work-item: "item-29"
group: "2026-09-24-0330"
verify: [test]
---

# PMD/ErrorProne 覆盖 manifest v1 + 质量/安全/XNode 规则（roadmap item 29）

## Current Baseline

以下事实均已对照 live repo（2026-09-24）核实：

- 依赖满足：item 29 deps = item 10（`done`，RuleTester 验收底座）——manifest 的 tier 1–3 行以 fixture 路径为验收锚点，RuleTester 管线（`RuleTestRunner` + `.expect` 格式 + `TestNopRuleSuites` 套件计数门禁）成熟。
- **roadmap item 29 原文口径**："PMD/ErrorProne coverage manifest v1 (tier labels + acceptance fixtures, design 06 §7) + quality/security/XNode rules (9 of the first-20 batch)"。design 02 §1 首批 20 条的未落地 9 条 = 质量 5（no-system-out / no-return-null / no-transactional-annotation / import-order / no-star-import）+ 安全 2（no-sensitive-literal / no-hardcoded-crypto）+ XNode 2（orm-unique-key / xpl-escaping）。
- **两处与既有裁定的冲突须在 Phase 1 消解**：
  1. `import-order`：item 28 迁移 manifest（`12-check-scripts-migration-manifest.md` 行 #8）已裁定 `check-import-order.mjs` **maintain-mjs**（"import 序列单调性是序列属性非模式匹配；脚本自述 advisory"）——pattern 引擎无法 faithful 表达序列单调性。本 plan 裁定该规则**不落地近似版**，design 02 §1 行注记路由，roadmap 行记录 9→8 的口径（Anti-Slacking：不得以高误报近似冒充落地）。
  2. `xpl-escaping`：忠实语义（真实锚点 = design 02 §1 行 + design 06 §3.3 + `check-xpl-escaping.mjs`；注意 design 12 行 #24 所引"design 02 §7"为悬空引用，design 02 无 §7）需要"文本包含 `${'$'}{` + XML 标签栈上下文排除 + 多文件面（.page.yaml/.json/.xpl 非 XML）"——**spike 实测**（round-1 审查）：XNode text 匹配 = trimmed 全等（design 01 §3.5），无 contains；XML 路径拒 constraints；capture 命中一切文本且无过滤面 ⇒ **faithful 不可落地**。且 design 06 §3.3 将 XPL 转义归"xscript + AST 查询"与 design 02 §1"依赖 XNode 引擎的 2 条"存在设计内矛盾——Phase 1 裁定消解：路由 deferred，successor = design 06 §3.3 的 xscript/AST 查询机制面（Wave 3+ xscript 已 done，但多文件面与上下文排除仍超 v1 xscript 单文件 node/captures 契约，须 item 33 scopeAnalyzer 或专项裁定）。
- **XNode 规则先例**：`nop-orm-mandatory-default`（item 21）示范 attribute 存在性检查的 all+not 组合形态；`orm-unique-key` 锚点语义为 **missing-or-empty**（`check-orm-unique-key-constraint.mjs`：constraint=/columns= 非空）。**两轮裁定（F5 → N1 Blocker）**：round-1 的 any 四分支（缺失/空 × constraint/columns）经 round-2 源码推演**不可实现**——XML 路径的 any 只接受 flat pattern/kind 分支（`XmlRuleCompiler` 显式拒绝 nested 与嵌套 any），attr 词表无"必须缺失"spec，缺失分支无表达形态；`constraint=""` 精确空串匹配引擎层成立（MetaVarSyntax 空串归 LITERAL("")）。**N1 裁定：走 xscript 通道**——XML 路径支持 xscript（XmlRuleCompiler 只拒 constraints/fix，precompiled 规则经同一 per-match xscript 执行管线）：pattern `<unique-key/>`（open-world 全命中 unique-key 元素）+ xscript 判 `attrValue("constraint")`/`attrValue("columns")` null 或 blank → report（Phase 1 spike 钉死 NodeWrapper 的 attr 可达性；若 facade 未暴露 attrValue，则扩展 NodeWrapper——nop-lint 模块自有面，非平台 Protected Area，带回归测试）。fallback：XNodePatternCompiler 增 attr-absence spec（专项裁定 + 回归）。XNode 规则 id 沿用先例无斜杠格式：`nop-orm-unique-key`（归 `TestNopRuleSuites` XNODE_RULE_IDS 桶）。
- **语义锚点**（不得凭空发明，Anti-Slacking）：no-sensitive-literal ← `check-sensitive-literal-leak.mjs`（JDBC URL 正则 + 内联 SQL 关键词，LOG/.param 行级同现）；no-hardcoded-crypto ← PMD HardCodedCryptoKey 语义（SecretKeySpec/IvParameterSpec 等构造的字面量 key）；no-system-out ← `check-bean-naming` 之外的常识面（System.out/err 打印）；no-return-null ← PMD ReturnNull 语义子集（v1 仅裸 `return null;` 语句，分析器面的建议性返回归 Phase 3+）；no-transactional-annotation ← Nop 平台规约（业务代码禁用 Spring `@Transactional`，平台自有事务面）；no-star-import ← `check-nop-stream-invariants.mjs` 的 wildcard-import 门禁语义（main+test 禁 `import x.y.*;`）。
- **manifest v1 权威**：design 06 §7——形式 = `nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml`（尚未创建），每条 `source_rule/tier/mechanism/fixture`，CI 校验 tier∈{1,2,3} 必须有 fixture、excluded 必须有 reason；§7.2 排除表（CPD/Dagger/Guice/javac-dataflow/Android/非 Java 语言）与 §7.3 severity 映射表是 manifest 的排除与映射依据；§1–§2 的规则表（代表性采样约 74 条）是 v1 枚举范围。
- **既有能力面**（规则机制映射依据）：pattern/any/all/not/matches/relational（items 1–7/23/24）+ constraints sameText/regex/inList/typeOf/notExists/withinDepth（item 22）+ XNode XML 规则（item 21，拒 constraints）+ xscript（item 14，拒 fix）+ autofix template（item 25）+ L2 typeOf（item 20）。
- **套件计数门禁**：`TestNopRuleSuites` 的期望集与计数（13）随新套件数量同步更新（先例：autofix demo 12→13）。
- 硬约束适用：Wave-2+ 规则必须带 RuleTester fixtures（roadmap 硬约束：无 fixture 视为未完成）；零平台改动；XNode 路径拒 constraints/fix 的既有裁定不解除。

## Goals

- **Phase 1 裁定**：9 条逐一分类（机制 + 语义锚点 + 可 faithful 落地性），产生明确的落地震单（预期 7 条：no-system-out / no-return-null / no-transactional-annotation / no-star-import / no-sensitive-literal / no-hardcoded-crypto / orm-unique-key）与路由裁定（import-order → maintain-mjs per item 28；xpl-escaping → **已裁定 deferred 路由**（round-1 spike：XNode text 全等语义无 contains + XML 拒 constraints ⇒ faithful 不可落地；successor = design 06 §3.3 的 xscript/AST 查询机制面），回写 design 02 §1/design 12 行 #24）；每条落地震单规则的 pattern/XNode 形态 + severity 映射（design 06 §7.3）记录在案。
- **7 条规则落地**（候选 8 = 9 − import-order；xpl-escaping 已裁定路由）：`nop-lint-nop` 主资源 `<category>/<rule>.rule.yml` + suite（valid ≥1 + invalid ≥2 + `.expect`，经 `x:extends` 指针零重复）+ `TestNopRuleSuites` 期望集更新；pattern 规则经真实 Java grammar，XNode 规则经真实 ORM 模型（orm-unique-key 的 fixture 用真实 `<unique-key>` 形态）。
- **manifest v1**：`nop-lint-nop/src/main/resources/manifest/pmd-errorprone-coverage.yml`——枚举 design 06 §1–§2 代表性采样的全部条目（**实测 189 行：§1 PMD 115 + §2 ErrorProne 74**；design 06 §7.1 "随 Phase 2 首版建立，Phase 3 补全"——本 plan 交付全量 v1 首版，避免部分账本破坏"完整覆盖的可验收形式"与枚举对账门禁）。**fixture 字段语义裁定（N2）**：tier 1 行 = 已落地、fixture 指向真实存在且被门禁存在性校验的 suite 路径；tier 2/3 行 = 前瞻路径（允许指向尚未存在的 suite，存在性校验不对 tier 2/3 强制），但 **mechanism 必填且须引用具体 L 层**——此为对 design 06 §7.1 原口径（tier 1–3 一律 fixture）的显式差异裁定（约 180 条 tier 2/3 无 suite，原口径首日即 fail），回写 design 06 §7 增注。每条 source_rule（PMD/EP 原名）/ tier（1|2|3|excluded|excluded-with-approximation）/ mechanism（pattern 草图 | analyzer 依赖 L1–L4）/ fixture（**tier 1 必填且指向已存在的 suite 路径；tier 2/3 前瞻路径允许尚未存在**，N2 裁定）/ reason（excluded 必填，§7.2 排除表为据）；本次落地的质量/安全规则行标 tier 1 + fixture。
- **manifest 防腐门禁**：`ai-dev/tools/check-lint-coverage-manifest.mjs`（先例 `check-lint-migration-manifest.mjs`）——校验 (a) **enum-set 对账（F7）**：manifest source_rule 集与 design 06 §1–§2 表解析出的源规则集精确 diff（多/缺/改名 fail），(b) **tier 1 行** fixture 字段指向真实存在的 suite 目录（对 live suites 枚举核对；tier 2/3 前瞻路径不校验存在性，N2 裁定），(b') **tier 2/3 行 mechanism 必填且引用具体 L 层**（N5 弱对账），(c) excluded 行有非空 reason，(d) tier 词表合法 + 分类汇总自洽；self-test 正控。门禁跑通 + 一次破坏试验 Proof。
- **规则本数可观测**：`nop-lint check` 对生产规则前缀的 rulesLoaded 计数随新规则增长（日志记录前后对照）。
- 端到端证明：新规则经 `nop-lint check`（真实 CLI，fixture 前缀）与 RuleTester 双通道验证；manifest 门禁防住 fixture 缺失的回归。
- owner docs 回写：design 02 §1（9 条行的落地/路由注记 + "design 02 §7"悬空引用修正落 design 12 行 #24）、design 06 §7（manifest v1 落地形态 + 门禁指针）、**design 12 迁移账本三行同步**（F6：#20 orm-unique-key 落地 → migrated-pending-switchover；#21 no-sensitive-literal 落地 → migrated-pending-switchover；#24 xpl-escaping 路由 deferred + 悬空引用修正）+ roadmap item 29 状态回写（draft review 置 planned，closure audit 置 done；落地/路由口径参数化记录）。

## Non-Goals

- PMD/ErrorProne 全量 800+ 条的枚举（design 06 §7 原文："完整覆盖的可验收形式是覆盖 manifest，而非把 800+ 条规则全部抄进设计文档"；v1 枚举 §1–§2 采样面，Phase 3 补全）。
- 本次落地规则的 fix 模板（autoFixable: false；规则 fix 面随后续 autofix 规则批量 plan 裁定——非 item 35（规则库 48+）/item 40（checkstyle 迁移）的范畴）。
- PMD/ErrorProne 语义的 Java 端逐条移植验证（对照锚点 = 语义来源脚本的判定逻辑，不做 PMD 跑分对照）。
- `import-order` 与 `xpl-escaping` 的近似落地（裁定路由见 Current Baseline/Goals；`query-limit-required` 是 item 35 的第 20 条，不在本 plan）。
- manifest 的 GraphQL `lint__listRules` 消费面（item 38）。

## Phase 1 — 9 条逐一裁定与形态设计（Decision）

Status: completed
Targets: `ai-dev/design/nop-lint/02-rule-library.md`（注记草稿）、`ai-dev/logs/`

- Item Types: `Decision`

- [x] 逐条裁定表：9 条 × (语义锚点 / 目标机制 / severity 映射 / 可 faithful 落地性 / 路由去向)；import-order 裁定路由 maintain-mjs（引 item 28 manifest 行 #8）并回写 design 02 §1；xpl-escaping 按已裁定 deferred 路由回写（round-1 spike 证据：XNode text 全等无 contains + XML 拒 constraints ⇒ faithful 不可落地）
- [x] 7 条落地震单的 pattern/XNode/xscript 形态草案（每条写出 rule.yml 的 pattern/constraints 草图与已知误报边界），逐条标注语义锚点出处
- [x] 盘点结论写入日志：落地 7 条 + 路由 2 条 + manifest 枚举 189 行实测（186 条去重入账）——TestRuleSetExemptions 先例的套件计数门禁同步 13→20

Exit Criteria:

- [x] 9/9 条均有裁定记录，无未裁定行；import-order 与 xpl-escaping 的路由/落地结论均有设计依据与 Anti-Slacking 自查
- [x] 每条落地震单规则的形态草图经真实语法 spike 验证（pattern 能编译、XNode 能匹配真实 ORM 模型）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Phase 2 — 规则落地 + 套件（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-nop`（主资源 + suites）、`nop-lint/nop-lint-core`（如需测试夹具）

- Item Types: `Fix | Proof`

- [x] 落地震单规则逐条落地：主资源 `<category>/<rule>.rule.yml`（severity 按 design 06 §7.3 映射，metadata.version "1.0"）+ suite（`x:extends` 指针 + valid ≥1 + invalid ≥2 + `.expect`）
- [x] `TestNopRuleSuites` 期望集与计数门禁更新（13 → 13+N）；主资源加载断言循环覆盖新规则（category 映射）
- [x] 每条规则的 invalid fixture 至少覆盖：核心命中形态 + 一个边界形态（如 no-return-null 的 return null 在 void 方法、no-sensitive-literal 的 sanitized 例外形态若锚点脚本有豁免语义）
- [x] 单元测试矩阵（Minimum Rules #25）：全部新套件经 `TestNopRuleSuites` 真实管线跑绿；nop-lint-core 侧如需 grammar 夹具（pattern 编译 spike 固化）则落 `nop-lint-core` 测试

Exit Criteria:

- [x] 全部落地震单规则带套件且 `TestNopRuleSuites` 全绿（计数门禁更新）
- [x] `./mvnw -pl nop-lint/nop-lint-nop -am test` 退出码 0
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [x] 规则本数增长可观测（TestProductionRuleCount 断言生产前缀 11→18 全量枚举）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Phase 3 — manifest v1 + 防腐门禁 + 收口（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-nop/src/main/resources/manifest/`、`ai-dev/tools/`、design docs、roadmap

- Item Types: `Fix | Proof`

- [x] manifest v1 落盘：`pmd-errorprone-coverage.yml` 枚举 design 06 §1–§2 采样全量 189 行（source_rule/tier/mechanism/fixture/reason），本次落地规则行 tier 1 + 真实 fixture 路径（F1 裁定：全量首版）
- [x] **tier 归入 rubric 钉死（N4）**：tier 1 = 本 plan 已落地带 live fixture 的规则；tier 2 = 机制面 L1/L2（pattern+constraint+声明类型，design 06 §4.7 映射）；tier 3 = 机制面 L3/L4（数据流/语义分析）；excluded = §7.2 能力性排除（CPD/Dagger-Guice/javac-dataflow/Android/非 Java）；excluded-with-approximation = javac dataflow 类（Phase 3 近似子集）；tier 2/3 行 mechanism 必须引用具体 L 层（N5 弱对账，门禁校验），tier 归入语义抽检由 closure audit 承担
- [x] 防腐门禁 `check-lint-coverage-manifest.mjs`：enum-set 对账（design 06 表 diff）/ tier 1 fixture 存在性 / tier 2/3 mechanism-L 层 / excluded reason / tier 词表 + 汇总自洽校验 + self-test 正控；跑通 exit 0 + 破坏试验 Proof（临时抽掉一个 fixture 字段 → fail → 恢复）
- [x] 一致性核对：manifest 的 tier 1 行 vs 实际落地套件目录 vs design 02 §1 注记 vs design 06 §7 门禁指针——四处一致
- [x] **端到端双通道验证**（Minimum Rules #22 前移，F8）：新规则经真实 CLI（fixture 前缀 `nop-lint check`）与 RuleTester 双通道各有一条断言
- [x] 收口项：roadmap item 29 状态回写（draft review 置 planned，closure audit 置 done；**口径：候选 8 / 落地 7 / 本 plan 路由 1**——9 条未落地中 import-order 已由 item 28 裁定 maintain-mjs 不入候选，xpl-escaping 本 plan 路由 deferred）；核对 items 26/27/28 与 M4 未受扰动
- [x] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-nop -am test` 与 `-pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0；`node ai-dev/tools/check-lint-coverage-manifest.mjs` 退出码 0；owner-doc 与 live 一致；`ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] manifest 枚举 design 06 §1–§2 采样全量（189 行 enum-set 对账通过），无缺行；tier 1 行 fixture 全部真实存在；excluded 行全部有 reason；tier 2/3 行 mechanism 均引用具体 L 层
- [x] **端到端双通道验证**（Minimum Rules #22，F8）：新规则经真实 CLI（fixture 前缀 `nop-lint check`）与 RuleTester 双通道各有一条断言
- [x] 门禁 exit 0 + 破坏试验 Proof 在案（Minimum Rules #24：缺失/非法显式报错）
- [x] 四处一致性核对记录在案（manifest/套件/design 02/design 06）
- [x] `./mvnw -pl nop-lint/nop-lint-nop -am test` 与 `-pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [x] roadmap item 29 状态回写正确，周边 item 与 M4 未受扰动
- [x] owner-doc 与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。

- [ ] in-scope 行为结果已达成：落地震单规则全部带套件落地、manifest v1 枚举完整、防腐门禁运行且防住破坏
- [ ] fail-closed 无降级：9/9 条裁定无未决行，路由裁定有设计依据（import-order/xpl-escaping 不以近似冒充落地）
- [ ] 端到端验证（Minimum Rules #22）：新规则经真实 CLI（fixture 前缀 nop-lint check）与 RuleTester 双通道验证
- [ ] 无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up
- [ ] owner docs（design 02 §1、design 06 §7）与 live 一致；roadmap item 29 状态回写正确
- [ ] 独立子 agent closure-audit 已完成并记录证据到 `## Closure`
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）新规则经真实规则管线（XDSL → LintEngine → RuleTester）运行时消费，（b）manifest 门禁真实拒绝坏输入，（c）无空方法体/静默跳过
- [ ] `./mvnw -pl nop-lint/nop-lint-nop -am test` 退出码 0
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0
- [ ] `node ai-dev/tools/check-lint-coverage-manifest.mjs` 退出码 0
- [ ] checkstyle / 代码规范检查按 mission `commands.lint` 既有裁定记录

## Verification

- `./mvnw -pl nop-lint/nop-lint-nop -am test -q` 退出码 0（TestNopRuleSuites 22 + TestProductionRuleCount 1 + autofix demo 2 + bootstrap）
- `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C -q` 退出码 0（回归基线）
- `node ai-dev/tools/check-lint-coverage-manifest.mjs` 退出码 0（186 entries vs 189 design rows；tiers 1×4/2×165/3×17）；self-test 退出码 0
- 破坏试验 Proof：删除 tier-1 fixture 字段 → 门禁 fail（SystemPrintln 缺 fixture）→ 恢复 → exit 0
- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- pre-commit ast-grep Java lint 通过

## Closure

（待独立子代理 closure audit 通过后填写）

Follow-up:

- no remaining plan-owned work（manifest Phase 3 补全与 GraphQL lint__listRules 消费面归 item 38/40；切换执行归迁移 plan）

## Draft Review Record

- 2026-09-24：iteration 3，增量复核（agent_7e01caea-a4c4-4d90-a636-53d698700ca1）——N1/N3/N4/N5/F8 全 PASS；N2 FAIL 原因为行 31 尾部旧字段规格（"tier 1–3 必填指向已存在"）与行 32(b) 门禁（"tier∈{1,2,3} 真实存在"）两处旧口径残留与新裁定正面冲突，属机械性文本修订（审查者明示 fix_effort=mechanical），连同推荐项（行 52 条件句残留、行 53 "7+"陈旧、门禁枚举补 mechanism 项、"§design 12"笔误）全部按指定修复（复核确认：全文唯一残留匹配为 iteration-3 记录对旧文本的历史引用，应保留）。
- 2026-09-24：iteration 2，全新子代理复核（agent_a23dcce6-e0cb-4cbb-90e5-f2a93e597587）——round-1 修复 7/9 PASS：F1/F2/F3/F4/F6/F7/F9 落字属实（F3 关系式形态经引擎源码推演可行：$LIT single root 合法、CaptureIndex 贯穿 all/relational、capture 文本含引号印证 full-match regex 必要）；**N1 Blocker**：orm-unique-key any 四分支的"缺失"分支在 XML 路径不可表达（XmlRuleCompiler 拒 nested/嵌套 any、attr 词表无 absence spec）→ 重裁定走 xscript 通道（pattern open-world + attrValue 判空 report，spike 钉死可达性，fallback attr-absence spec 扩展）；**N2 Major**：189 全量首版 × fixture 存在性门禁互斥 → tier 2/3 fixture 前瞻语义 + mechanism 必填 L 层 + 与 §7.1 差异裁定；N3 算术修正（候选 8/落地 7/路由 1）；N4 tier rubric 钉死；N5 mechanism 弱对账 + 审计抽检；F8 补 Phase 3 EC 勾选项。全部采纳修订。
- 2026-09-24：iteration 1，独立子代理审查（agent_c6b7e865-9868-4253-9f45-01337bd890bd）——**spike 驱动审查**（真实 tree-sitter java grammar + XNode 内核实测）：1 Blocker（F1 manifest 枚举基数 74→实测 189 行）+ 5 Major（F2 xpl-escaping faithful 不可落地实测 + 悬空引用"design 02 §7"与 02§1/06§3.3 设计内矛盾；F3 no-sensitive-literal 首参漏检/三明治 0 命中/constraint 拒 multi-capture/full-match+含引号四坑 → 关系式形态裁定；F4 no-hardcoded-crypto 变量 key 过匹配 → regex 约束钉字面量；F5 orm-unique-key 空值缺口 → any 四分支 faithful 组合；F6 design 12 账本三行回写漏项）+ 3 Minor（F7 门禁加 enum-set 对账、F8 双通道 e2e 前移 Phase 3 EC、F9 口径参数化/XNode id 格式钉死）。全部按审查者指定采纳修订。
