# 12. check-\*.mjs 迁移 manifest（roadmap item 28）

> Status: active（账本随迁移执行滚动更新）
> Last Reviewed: 2026-09-24
> 防腐门禁: `node ai-dev/tools/check-lint-migration-manifest.mjs`（exit 0 = 账本与 live 一致）
> 盘点基线: 2026-09-24 逐脚本源码盘点（两批独立子代理 + 接线点 grep 取证），`ai-dev/logs/2026/09-24.md`

## 定位与数字口径

本 manifest 是 design 02 §3 现有资产盘点表的**展开权威账本**：逐脚本枚举 `ai-dev/tools/check-*.mjs` 全集
**24 个**（替代 design 02 §3/roadmap 中笼统的"25+"口径；另有 `check-import-order.sh` 为 mjs 的已迁移孪生，
仅参考保留，无接线，不占行项）。每行含：子规则数 → 检查对象类别 → 目标能力映射 → 依赖 roadmap item →
switchover 门禁 → decommission 动作 → 状态。

**状态词表**（防腐门禁校验）：`maintain-mjs`（维持 mjs 门禁）/ `exclude`（排除，非代码检查或非 lint 关注面）/
`migrated-pending-switchover`（nop-lint 等价规则已落地，待行为对照与切换）/ `candidate`（已排期迁移，依赖 item 已
done）/ `deferred`（候选挂起，依赖 item 未 done 或无排期）。

**Switchover 门禁通用前置**（任何 `migrated-pending-switchover`/`candidate` 行的 decommission 动作生效前必须
全部满足，Minimum Rules #13——已进 CI fail-fast 的规则不得无门禁下线）：

1. 等价 nop-lint 规则已落地（主资源 + `_vfs` 夹具），RuleTester 套件全绿；
2. 行为对照通过：同一语料上 nop-lint 规则与 mjs 脚本的命中集对照记录在案（零 diff 或 delta 逐条裁定）；
3. 切换执行：调用点（CI workflow / package.json / run-*.sh）改接 nop-lint，原脚本删除或标注 `retired`；
4. 每次切换走独立 plan，经 closure audit 后回写本账本状态。

## 逐脚本账本（24 行）

| # | 脚本 | 子规则数 | 类别 | 目标映射 | 依赖 item | switchover 门禁 | decommission 动作 | 状态 |
|---|------|---------|------|---------|-----------|----------------|-------------------|------|
| 1 | check-ai-dict-consistency.mjs | 3（ORM→生成物存在/stale/option 集合一致） | XML 模型(dict)+生成物 YAML 跨文件对账 | 维持 mjs（codegen 生成物集合对账，非源码模式匹配） | — | 不适用 | 保留 | maintain-mjs |
| 2 | check-ai-tool-executor-boundary.mjs | 4（surface 标记/裸 IO 禁令/豁免登记/表完备性+边界接线） | Java 源码（nop-ai 专项） | 维持 mjs（绑定 ai 子系统 §3.3 目标集表与 gate-gaps 豁免机制，语义不可泛化） | — | 不适用 | 保留 | maintain-mjs |
| 3 | check-bean-naming.mjs | 3（BEAN-ID 前缀/REF 跨文件引用/COLLECT-PREFIX） | XML 模型(beans)，**CI compliance.yml 直连** | BEAN-ID+COLLECT-PREFIX → **XNode 规则**；REF 需跨文件 bean id 符号表 → 维持 mjs（或随迁移 plan 拆分裁定） | item 21（done） | XNode 规则 + fixtures + 全仓 beans.xml 行为对照零 diff（BEAN-ID 面）+ compliance.yml 切换提交 | REF 面脚本保留，BEAN-ID 面下线 | candidate |
| 4 | check-error-param-consistency.mjs | 4（throw-site 占位符/UNRESOLVED 强制裁定/define-face 对称差/dead-define） | Java 源码跨文件 ErrorCode 注册表语义 | **维持 mjs（本 plan Decision，见下节）** | — | 不适用 | 保留（zero-hit hard gate） | maintain-mjs |
| 5 | check-fix-commit-diff.mjs | 2（fix(nop-ai) 候选收集/实质 diff 判定） | git 提交历史 | 排除（提交历史检查，非文件内容 lint——nop-lint 检查源码不检查 git 历史） | — | 不适用 | 保留（pnpm 接线不变） | exclude |
| 6 | check-i18n-en-xml.mjs | 7 issue code（SUSPICIOUS_TEXT 内含 10 条拼写正则） | XML 模型(i18n/orm/api/xmeta) | 候选 XNode + regex constraint（item 22 已交付）；无接线、默认恒 exit 0，无门禁压力 | item 21/22（done） | 规则 + fixtures + 属性面行为对照 | 脚本下线 | deferred |
| 7 | check-ibiz-interfaces.mjs | 2（ibiz-missing-annotation/ibiz-missing-context） | Java 源码（tree-sitter AST） | **已迁移**：同名规则 item 11 落地 nop-lint-nop | item 11（done） | 对照通过（item 11 cross-check 已记录）+ 切换执行 plan | pnpm `check:ibiz` 移除、脚本下线 | migrated-pending-switchover |
| 8 | check-import-order.mjs | 1（类别单调性） | Java 源码 | 维持 mjs（import 序列单调性是序列属性非模式匹配；脚本自述 advisory） | — | 不适用 | 保留（`check-import-order.sh` 同步标注已废弃参考件） | maintain-mjs |
| 9 | check-oversized-files.mjs | 2（warn/error 双阈值行数） | Java 源码 | 维持 mjs（文件行数度量非模式检查；MetricsEvaluator 是 AST 复杂度度量，语义不同） | — | 不适用 | 保留 | maintain-mjs |
| 10 | check-plan-checklist.mjs | 4 组 + completed 结构 12 条 | 流程工具（plan markdown） | 排除（ai-dev 计划流程工具，非代码检查） | — | 不适用 | 保留 | exclude |
| 11 | check-plan-status.mjs | 0（纯报告） | 流程工具（plan markdown） | 排除（同上，且无违规判定） | — | 不适用 | 保留 | exclude |
| 12 | check-vfs-violations.mjs | 28 pattern / 21 tag + 误报排除 + 白名单 | Java 源码 | **部分已迁移**：no-vfs-violation 规则 item 11 落地（核心面）；21 tag 与规则覆盖面的逐 tag 对照归切换 plan | item 11（done） | 逐 tag 行为对照（规则覆盖面 vs 28 正则）+ 白名单 4 文件的规则侧豁免对应 | 对照后切换：脚本下线或收敛为残余 tag 的 mjs 补门禁 | migrated-pending-switchover |
| 13 | check-doc-index.mjs | 6 步骤 / 8 rule ID | 文档 md 索引 | 排除（文档一致性，design 02 §3 既有裁定） | — | 不适用 | 保留 | exclude |
| 14 | check-doc-links.mjs | 4（BROKEN_LINK/OUT_OF_PROJECT/两条 BOUNDARY） | 文档 md 链接 | 排除（同上；AGENTS.md 硬门禁继续由本脚本承担） | — | 不适用 | 保留 | exclude |
| 15 | check-docs-garbled.mjs | 1 主规则 / 12 字符分类 | 文档 md 乱码 | 排除（同上） | — | 不适用 | 保留 | exclude |
| 16 | check-nop-code-invariants.mjs | 3 family（query-limit/entity-field-min/delete-contract） | Java 源码（nop-code 专项） | query-limit 子规则 → **pattern + xscript 规则**（即 item 35 的 query-limit-required，对照归 item 35 plan）；entity-field-min/delete-contract 需方法作用域分析 → 维持 mjs（item 33 落地后重估） | item 35（todo） | 随 item 35 plan 对照裁定 | 届时收敛为残余 family 的 mjs 补门禁 | deferred |
| 17 | check-nop-stream-audit-manifest.mjs | 7 子命令 checker | 审计 manifest（@@MARKER 块） | 排除（审计证据 schema 校验，流程工具非代码检查） | — | 不适用 | 保留 | exclude |
| 18 | check-nop-stream-invariants.mjs | 14（inventory+sync/iterations/output-contract 5/wiring 5/wildcard） | Java 源码（nop-stream 专项）+ registry/pins ratchet | 维持 mjs（子系统级语义门禁：自研 extends 闭包/receiver 推断 + ratchet 机制，**CI maven.yml 直连**，不可泛化为规则） | — | 不适用 | 保留 | maintain-mjs |
| 19 | check-orm-icons.mjs | 3（orm 根图标/entity 图标/action-auth 菜单图标） | XML 模型(orm)+action-auth | **XNode 规则**（attribute 必填 = XNode 强项，nop-orm-mandatory-default 先例同形） | item 21（done） | 规则 + fixtures + 全仓 orm/action-auth 对照零 diff（或 delta 逐条裁定） | 脚本下线 | candidate |
| 20 | check-orm-unique-key-constraint.mjs | 1 规则 / 2 条件（constraint=/columns= 非空） | XML 模型(orm)，**CI invariant-gate 接线** | **已落地 xscript 通道规则**（item 29：pattern open-world + attrValue 判空——round-2 审查 N1：XML pattern/any 面无 attribute 缺失 spec，xscript 通道为 faithful 形态） | item 21/29（done） | 同语料行为对照（含空值 case）+ run-nop-metadata-invariants.sh 步骤切换 | 脚本下线、sh 步骤改接 nop-lint | migrated-pending-switchover |
| 21 | check-sensitive-literal-leak.mjs | 1 规则 / 2 模式（JDBC URL/内联 SQL） | Java 源码，**CI invariant-gate 接线** | **已落地关系式规则**（item 29：string_literal+inside+full-match regex 约束（capture 含引号、sanitized lookahead）+ xscript 通道门控日志接收方/param；已裁定 delta=AST 包含替代行级同现，覆盖全参数位） | item 22/29（done） | 同语料命中集对照 + sh 步骤切换 | 脚本下线 | migrated-pending-switchover |
| 22 | check-silent-swallow.mjs | 1（7 GOOD_SIGNALS 信号缺失） | Java 源码，**CI invariant-gate 接线** | **已迁移**：nop/silent-swallow 规则 item 23 faithful 落地（catch 七信号 all+not 形态） | item 23（done） | item 23 行为对照已记录 + 切换执行 plan（含 sh 步骤改接） | 脚本下线、sh 步骤改接 nop-lint | migrated-pending-switchover |
| 23 | check-silent-wrong-result.mjs | **5**（locale/narrowing-cast/contains-classify/delim-key/bigdec-precision） | Java 源码，**CI invariant-gate 接线**（baseline ratchet mode b） | 逐条裁定、暂全部维持 mjs：locale → pattern 可表达（豁免机制需对齐）；narrowing-cast/contains-classify → 需 L1 类型（item 12/26）；delim-key → 括号 span 分析，xscript 近似待评估；bigdec-precision → 方法作用域（item 33）。**ratchet baseline 机制无引擎对应，切换前须裁定基线对账方案** | item 26/33（todo） | 每子规则独立对照 + baseline 迁移方案裁定后逐条切换 | 逐条下线，残余子规则保留 mjs | maintain-mjs |
| 24 | check-xpl-escaping.mjs | 1（8 种上下文判定） | VFS 前端资源(view/page.yaml/xpl/json) | **deferred（item 29 round-1 spike 裁定）**：XNode text 匹配=trimmed 全等（无 contains）+ XML 路径拒 constraints ⇒ faithful 不可落地；successor = design 06 §3.3 的 xscript/AST 查询机制面（原行所引 "design 02 §7" 为悬空引用，design 02 无 §7——本行修正）；当前恒 exit 0 无接线 | Wave 5 语义面（item 33+） | 规则 + fixtures + 上下文判定面对照 | 脚本下线 | deferred |

**分类汇总**（防腐门禁核对口径）：`maintain-mjs` 7 + `exclude` 7 + `migrated-pending-switchover` 3 + `candidate` 5 + `deferred` 2 = **24**。

## errorcode-param-consistency 专项裁定（plan 2137-3 deferred 消解）

**裁定：维持 mjs 门禁，不立项引擎级 analyzer**（2026-09-24，item 28 执行期）。

- **理由**：(a) 该脚本的 faithful 语义 = 跨文件 ErrorCode 注册表分析——解析 `NopMetadataArgs.java` 的 ARG_*
  注册表 + 10 个 `*Errors.java` 子接口 + define-face 对称差校验 + 全仓 dead-define 语料 + throw 站点常量解析
  （1174 行，子系统级 analyzer 而非规则）；(b) 深度绑定 nop-metadata 专名（`NopMetadataException`/
  `NopMetadataErrors` 硬编码），全仓唯一消费者，泛化为引擎能力无第二受益方；(c) 该不变式已在
  `run-nop-metadata-invariants.sh` 作为 zero-hit hard gate 运行（CI maven.yml 间接接线），迁移只添风险不添表达力。
- **不降级承诺**：mjs 门禁保持 hard gate（Minimum Rules #13），不下线、不改 advisory。
- **重估触发条件**：(a) Wave 5 语义分析器落地（item 30 dataflow / item 33 scope analysis）**且** (b) 出现第二个
  采用 `*Errors.java` 注册表模式的模块（泛化需求 ≥2 消费者）——届时以专用 analyzer item 立项重估。
- **消解关系**：plan `2026-09-21-2137-3-core-rules-first-batch` 的 `Deferred But Adjudicated`（Successor Path =
  roadmap item 28）由本裁定消解；design 02 §3 追溯表与 roadmap item 28 注记同步。

## 已迁移待切换项的下线计划（manifest 消费入口）

`migrated-pending-switchover` 三行（#7 ibiz-interfaces、#12 vfs-violations、#22 silent-swallow）的切换执行归
**下一个 nop-lint 迁移 plan**（消费本账本，逐脚本交付）：每脚本一条切换行 = 行为对照记录 → 调用点改接
（package.json / run-nop-metadata-invariants.sh）→ 脚本删除或 `retired` 标注 → 本账本状态改 `switched-over`
（新增状态词表值，防腐门禁同步扩展）。#20/#21 两个 CI 接线脚本（orm-unique-key/sensitive-literal-leak）的
迁移优先级最高——它们在 invariant-gate 硬门禁中，等价规则落地后才能动 sh。

## 消费关系登记

| 消费方 | 消费的 manifest 行 | 触发时机 |
|--------|-------------------|---------|
| 下一个 nop-lint 迁移 plan（切换执行） | #7/#12/#22 下线计划 + #3/#19/#20/#21/#24 candidate 行 | 迁移 plan 立项时 |
| roadmap item 29（PMD/EP manifest + 9 条规则） | #16 query-limit 行（规则清单对齐 item 35 时一并裁定） | item 29/35 plan 立项时 |
| roadmap item 26/33（L2 solver / scope analysis） | #23 子规则重估 + #16 entity-field-min/delete-contract 重估 | 对应 item done 后的本账本复审 |
| roadmap item 40（checkstyle/pmd.xml 迁移 + 并行期） | 不消费本账本（checkstyle.xml/pmd-ruleset.xml 是另一资产面，06 §8） | item 40 立项时 |
