# sql-vision-conflict-resolution——D2 治理解冲突落档

> Status: active
> 裁定日期：2026-10-02
> 负责人：仓库 owner（授权依据见下）；执行与文本修订：ZCode AI 代理（受托）
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI0a、Purpose「必须修订的断言全集」、D2 行）
> 承载 plan: ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md

## 1. D2 裁定记录

| 项 | 内容 |
|---|---|
| 裁定点 | D2 治理解冲突范围：全解除 / 收窄表述 / 保留（roadmap 阻塞） |
| 结论 | **收窄表述**——按 roadmap Purpose「必须修订的断言全集」12 条逐条同步为窄范围 SQL 口径 |
| 负责人 | 仓库 owner（委托执行，授权证据见 §2）；roadmap 建议项即「收窄表述」 |
| 日期 | 2026-10-02 |
| 效果 | WI0a 门解除；WI6（union）、WI13（join）、WI15（设计文档）的门控前置（deps WI0a）可推进；「D2 = 保留 → 除 Phase 1 外 blocked」分支未触发 |

### 2. Owner 授权证据（原文引用）

> 用户指令（2026-10-02，会话 /goal）：「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」

该指令构成对「按 roadmap 建议项执行全部裁定（含 D2 = 收窄表述）并落地本文档所载 12 条修订文本」的委托确认。独立 closure audit 已核验该授权解释与修订文本的一致性（证据见承载 plan 的 Closure 段与 ai-dev/audits/nop-stream-sql/wi0a-closure-audit.md）。

## 3. 修订原则

- **窄范围纳入**（标注「规划」，引用 `ai-dev/backlog/nop-stream-sql-roadmap.md`）：单表查询、静态维表 lookup join、双流**等值** join（hash / window merge 两种）、窗口与分析窗口聚合。
- **保持排除**（不放松）：代价优化器；非等值 / 范围 join；广播 join 与 BroadcastState（§七 #G36 永久排除不变）；Flink SQL / Table 栈复刻与方言全覆盖；CEP 级复杂编排；异步算子。
- 不改 §三 约束与 §八 15 条设计不变量（它们是被验证对象）。

## 4. 12 条断言修订前后对照

> 行号为修订前实测行号（2026-10-02）；修订后因文本插入行号有漂移，以文本内容检索为准。

| # | 位置（修订前） | 修订前文本 | 修订后文本（摘要） | 依据 |
|---|---|---|---|---|
| 1 | `00-vision.md:47` §四 | `\| 双流 Join（interval join / window join / broadcast join） \| 复杂度极高，用例有限。可通过 CEP 或外部 lookup 替代 \|` | 行名收窄为「非等值 / 范围 / 广播 join」；正文注明等值 join（hash / window）与静态维表 lookup 已纳入规划（WI13/WI14），非等值 / 范围 / 广播仍排除 | roadmap 门控条款表「双流 Join → WI13」、§七 #G36 不变 |
| 2 | `00-vision.md:48` §四 | `\| SQL API \| Nop 平台已有 GraphQL，流式 SQL 需求不迫切 \|` | 行名收窄为「SQL API（Flink SQL / Table 栈复刻、代价优化器、方言全覆盖）」；正文注明窄范围流 SQL（EQL 复用）纳入规划，GraphQL 仍为主 API | roadmap Purpose 三层结构、Non-Goals |
| 3 | `00-vision.md:54-64` §五 | 收敛路径第 5 阶段止于「连接器、CEP、XDSL 编排」 | 新增第 6 阶段：窄范围 SQL 编译层与 join 算子（复用阶段 1-4 语义，「不可逆序」不变） | roadmap Purpose 编译层、Cross-Cuting 4 |
| 4 | `00-vision.md:70` §六 #1 | 决策点 #1 未记录 SQL roadmap 的授权范围 | 新增「#1 裁决记录（2026-10-02）」：授权新增 Transformation 类型与 StreamComponents 注册表条目（D14=(a)）；不覆盖 #2-#6 | roadmap 门控判据 (b)、D14 裁定行 |
| 5 | `00-vision.md:87` §七 | `去除：复杂 Join、广播流、异步算子` | 改为「复杂（非等值 / 范围）Join」，注明等值 join 收窄纳入规划；#G36 广播流排除不变 | roadmap 门控条款表、§七 #G36 |
| 6 | `comparison.md:187` §4.1 | `\| SQL \| ... \| ❌ 明确不实现 \|` | 改 ⚠️ 规划窄范围流 SQL（EQL 复用，无方言兼容 / 优化器） | roadmap Purpose、Non-Goals |
| 7 | `comparison.md:188` §4.1 | `\| 双流 Join \| ... \| ❌ 明确不实现 \|` | 改 ⚠️ 规划等值 join + 静态维表 lookup，非等值 / 范围 / 广播仍排除 | roadmap WI13/WI14 |
| 8 | `comparison.md:271` §5.1 | `\| TwoInputStreamOperator \| 无等价物 \| ❌ 不实现 \|` | 改 ⚠️ 规划——等值 join 需双输入算子 | roadmap WI6/WI13、门控条款表「两输入算子」 |
| 9 | `comparison.md:672` §14.2 | `\| 双流 Join \| ✅ 完整 \| ✅ SQL Join \| ❌ 不实现 \|` | 改 ⚠️ 规划（窄范围等值 join + 静态维表 lookup） | roadmap WI13/WI14 |
| 10 | `comparison.md:673` §14.2 | `\| SQL API \| ✅ Table API + SQL \| ✅ SQL Transform \| ❌ 不实现 \|` | 改 ⚠️ 规划（窄范围流 SQL） | roadmap Purpose、WI17/WI18 |
| 11 | `comparison.md:685` §14.2 | `\| 声明式编排 \| ... \| ⚠️ 规划 Phase 5 \|` | 修正过时口径：XDSL 编排已落地（Stage 50）+ 窄范围流 SQL 规划中 | vision §一/§五 既有事实 + roadmap |
| 12 | `component-roadmap.md:13` §1 | `去除复杂 Join、广播流、异步算子……聚焦单流窗口聚合 + CEP + Checkpoint 容错` | 「复杂」限定为非等值 / 范围；注明等值 join 与窄范围流 SQL 收窄纳入规划 | roadmap Purpose、门控条款表 |

## 5. 既有坏链修复（附带收口）

`ai-dev/design/nop-deepwiki/01-toolchain-absorption-design.md:75,163` 对 gitignored 目录 ai-dev/tools/node_modules 的反引号引用改为普通文本（去反引号、不改语义），消除 check-doc-links 既有 2 个 BROKEN_LINK error，使 `node ai-dev/tools/check-doc-links.mjs --strict` 全仓退出码 0 可达成。
