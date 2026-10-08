# 05 WI25 watermark 与 lateness 上浮 sql 声明面

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI25 行 P0 首选、Risks Q1）；`ai-dev/design/nop-stream/window-failfast-decisions.md` §1（D9 allowedLateness 放行——运行时管线已交付）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-feldera-analysis.md`（LATENESS 列注解形态，转引）
> Related: `ai-dev/plans/nop-stream-sql/12-wi10-window-parameterization.md`（D9 实施）；ai-dev/plans/nop-stream-sql-enhancement/04-wi37-emit-two-tier.md（SPI 加参先例）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

把 lateness 声明上浮到 `<sql>` schema 面：`<schemas><field lateness="ms">` 列级注解（Feldera LATENESS 形态），编译产物映射到既有 windowingStrategy 的 allowedLateness（D9 已放行并接线的运行时管线）——**零运行时改动**的声明面加法。plan 内裁定 Q1（声明形态选型）。

## Current Baseline

- **运行时管线已放行且接线**（D9/WI10/WI38）：`<strategy allowedLateness>`（xdef `!long=0`）→ buildWindow 消费（节点级覆盖策略级）→ `windowed.allowedLateness(...)` → WindowOperator（窗口关闭点推迟至 end+lateness、迟到记录 fire-and-update、skipPurgeForLateness）。
- **SQL 面缺口**：编译产物 strategy 不设置 allowedLateness（xdef 缺省 0）——SQL 用户无法声明迟到容忍；`<schemas>/<field>` 现仅 `name`/`type` 两属性（stream.xdef `xdef:name="StreamSqlFieldModel"`）。
- **SPI 形状**（WI37 后）：`compileSql(sql, schema, sinkBean, emit)`，schema 为 `Map<String,String>`（name→type）；`expandSqlModel` 从 `StreamSqlFieldModel` 提取 name/type。lateness 需新增传递通道。
- **编译路径与 strategy 关系**（WI37 已钉门）：仅 compileWindowedAggregate 生成 strategy；plain（含 plain-TUMBLE）/continuous/join 无 strategy——lateness 声明落在无 strategy 路径即无载体，须显式拒绝（同 WI37 no-silent 门模式）。
- **Q1（roadmap 风险表）**：声明形态选列级 lateness 还是查询级水位表达式——本 plan 裁定，不阻塞 roadmap。

## Goals

1. **Q1 裁定**：**选列级 lateness（Feldera LATENESS 形态）**——理由：(a) 直接兑现 D9 已放行的迟到更新管线，用户价值/成本比最高（roadmap P0 定位）；(b) 查询级水位延迟表达式（arroyo/risingwave `WATERMARK FOR col AS col - INTERVAL`）需改产物 watermarkGenerator xpl 并为「推迟 fire」语义单独设计判别性 E2E，且其乱序容忍场景与 lateness 的更新面在本子集重叠（sink 为 D1 last-value-wins，无丢失语义压力）；(c) 列级注解与 schema 内聚原则（D7）一致。**查询级水位延迟声明进 Follow-up Backlog（FU-E6）**，触发条件=真实乱序场景诉求。
2. **声明面**：stream.xdef `<schemas>/<field>` 增 `lateness="long"` 可选属性（maxDelay 同款可选 long 形态；未声明=无迟到容忍，维持现状）。
3. **贯通**：SPI 增 `fieldLateness` 参（`Map<String,Long>` name→millis，空 map=无声明）；expandSqlModel 提取（null/>0 判定）；编译器**仅接受 TUMBLE 时间列的 lateness**（时间列由 `TUMBLE(t, ...)` 编译器已知——R1-M-1 选项 i），写产物 strategy `allowedLateness={该列值}`；**非时间列声明 lateness fail-fast**（本子集把 Feldera per-column 语义子集化收敛为时间列单值——映射对象是窗口单值 allowedLateness，不发明「任一列=记录级容忍」语义）；校验：负值 fail-fast；lateness 声明落在无 strategy 路径（plain/continuous/join）fail-fast（no-silent，同 WI37 门模式）。
4. **零运行时改动**：不改 WindowOperator/flow builder/DSL 面——产物 allowedLateness 属性由既有管线消费。

## Non-Goals

- 查询级水位延迟声明（FU-E6）；watermarkGenerator 产物形态改动；raw DSL 面改动（strategy allowedLateness 已有）；join/连续聚合的 lateness 语义。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：stream.xdef `<field>` 增 lateness 属性（_StreamSqlFieldModel 再生成）。
- `nop-stream/nop-stream-flow`：ISqlStreamCompiler 加 `fieldLateness` 参（与 emit 参同模式：新参数入抽象签名，旧签名兼容重载保留）；expandSqlModel 提取；FakeSqlStreamCompiler 适配。
- `nop-stream/nop-stream-sql`：compile 加 fieldLateness 参；校验（负值/无 strategy 路径）+ strategy 写点（max 值 >0 时）；Provider 适配。
- 测试：shape（strategy allowedLateness）、门（负值/无 strategy 路径）、**端到端**（迟到记录在 lateness 界内更新已 fire 窗口并重发更新值——对照无 lateness 时被丢弃）；flow 侧 schema 提取断言。
- 文档：`docs-for-ai/03-modules/nop-stream-sql.md`（lateness 声明）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b（schema 行/窗口声明行）。
- roadmap：FU-E6 登记、WI25 状态行；当日日志。

### Out Of Scope

- WindowOperator/AdvancedTransforms 运行时与 DSL 消费；水位发射形态；列级 lateness 之外的新声明面。

## Execution Plan

### Phase 1 - 声明面 + 贯通 + 判别测试

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef`、`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/`、`nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/`

- Item Types: `Feature`

- [x] xdef：`<field name type>` 增 `lateness="long"` 可选属性（maxDelay 同款可选形态，未声明=无容忍；注释记 Q1 裁定、毫秒语义、仅时间列生效+非时间列拒绝、无 strategy 路径拒绝）；flow compile 验证 `_StreamSqlFieldModel` 再生成
- [x] flow：ISqlStreamCompiler 增 `fieldLateness`（`Map<String,Long>`）参数——新参入抽象 5 参签名，既有 4 参保留 default 重载（空 map 委托）；expandSqlModel 提取 `f.getLateness()`（>0 才入 map）；FakeSqlStreamCompiler 适配（记录 lastFieldLateness）
- [x] sql：compile 增 fieldLateness 参（兼容重载保留）；ctx 透传；校验——任一 lateness<0 fail-fast；**非时间列 lateness fail-fast**（时间列名 = TUMBLE source getTimeColumn()）；`failFastOnFieldLateness` 门挂 plain/continuous/join 三路径（载体缺失显式拒绝）；windowed aggregate 的 strategy 在时间列 lateness>0 时写 `allowedLateness="{value}"`（>0 才写，缺省产物 shape 不回退）
- [x] sql 测试（`TestSqlLatenessDeclaration`）：shape——时间列 `<field lateness>` → 产物 strategy allowedLateness=声明值；缺省产物无 allowedLateness 属性；**emit=UPDATE+lateness 组合 shape 钉码**（strategy 同时携带 emit=UPDATE 与 allowedLateness——R1-m3，语义叠加已由 plan 03/04 运行时测试覆盖不做组合 E2E）；兼容重载保留；负值 fail-fast；非时间列 lateness fail-fast；plain-TUMBLE/continuous/join 三路径声明 lateness fail-fast
- [x] 端到端（**两个测试类**，R1-M-2——既有 E2E javadoc 钉死 one-execute-per-test-class 且既有数据无迟到记录）：新增专用迟到 source bean（注册进测试 `sql-compile.beans.xml`，OrdersSourceFunction.FIXED_DATA 不动——R1-m2）：序列 ts=1000 a=1 → ts=6000 b=8 → **ts=3000 a=5（迟到，lateness=20000 时窗口 [0,5) cleanupTime=25000 未过）** → ts=17000 c=1（R1-m1 数值钉死）。`TestSqlLatenessE2E`（声明 lateness）：断言迟到更新重发可见——a=1 初发 + **a=6 更新重发** + b=8 + c=1；`TestSqlLatenessDroppedE2E`（同 source 不声明 lateness）：断言迟到记录被丢弃——仅 a=1 + b=8 + c=1。各自独立容器各一次 execute
- [x] flow 测试：expandSqlModel 对 `<field lateness>` 的提取断言（FakeSqlStreamCompiler.lastFieldLateness==声明值）；无 lateness 声明时 lastFieldLateness 为空 map
- [x] 全量回归：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am`——执行者两轮因上游偶发中断，独立 auditor 以 `-fn` 完整跑齐补证 BUILD SUCCESS（core 1754/flow 202/runtime 1336/sql 110 全 0 失败，含 nop-batch flake 通过；_tmp/wi25-closure-audit-regression-fn.log）

Exit Criteria:

- [x] **端到端验证**（guide #22）：lateness 声明的查询经真实执行，迟到更新可观测（TestSqlLatenessE2E a=6 重发），零声明路径行为不变（TestSqlLatenessDroppedE2E 对照 + 既有 E2E 全绿）
- [x] **接线验证**（guide #23）：`<field lateness>` → expandSqlModel → SPI → 产物 strategy → 既有 D9 管线连通——flow 层提取断言 + sql 层产物 shape 断言 + E2E 行为断言三段拼接（audit 实测 expandSqlModel L334-335 为 5 参调用——接缝事故已修复并有判别测试钉住）
- [x] **无静默跳过**（guide #24）：lateness 在无 strategy 路径显式 fail-fast（三路径）；负值 fail-fast；非时间列 fail-fast
- [x] 新功能测试覆盖（guide #25）：声明面/提取/门/写点/端到端五处有测试（+11 例）
- [x] 零运行时改动确认：WindowOperator/AdvancedTransforms/WindowedStreamImpl 零 diff（audit git 全量核查）
- [x] `./mvnw test -pl <四模块> -am` 全绿（auditor 补证）
- [x] owner doc 更新：docs-for-ai/nop-stream-sql.md、sql-subset §4b
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: in progress
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit（与起草审查不同 task）；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi25-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap：WI25 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)`，含 Q1=列级 lateness + FU-E6 转出 + 日期；**流程注记（audit M-4）**：翻转实际先于独立 audit 结论（audit 对翻转后状态做了事后追认核验），收口记录如实记载）；Follow-up Backlog 登记 FU-E6；重跑解析器核对 items 18 milestones 4（实测 items 18 milestones 4 wi25 done）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

Exit Criteria:

- [x] roadmap WI25 行 + FU-E6 同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 audit 文件
- [x] 三工具退出码 0
- [x] 日志收口记录与 plan 状态一致

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无——本项为 Feature）
- [x] 行为/契约结果已达成：SQL 面可声明迟到容忍，产物映射既有管线，零运行时改动
- [x] 必要 focused verification 已完成（shape/门/E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（两处）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证声明→提取→产物→运行时全链连通（E2E 行为断言）
- [x] `./mvnw compile -pl nop-stream/nop-stream-core,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` 通过
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 时间列 lateness 上浮 sql 声明面交付（Q1 裁定=列级形态，查询级水位延迟转 FU-E6）——`<field lateness>` 仅 TUMBLE 时间列生效映射产物 strategy allowedLateness，D9 管线零运行时改动兑现；三重 fail-fast 门（非时间列/负值/无 strategy 路径）；判别性 E2E 双档（界内重发 vs 丢弃）。roadmap WI25 → done。独立 closure audit APPROVED（6 Minor 全处置）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent_e3170646 均不同 task）：agent_d9f77cc8-d39b-4190-8a2e-c0123c5a8554
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi25-closure-audit.md
- Evidence:
  - Exit Criteria 逐条 PASS：声明面→提取→SPI→写点→D9 管线全链源码级追踪（audit 实测 expandSqlModel L334-335 为 5 参调用——执行中接缝编辑未持久化事故由判别测试按设计抓住后修复）；判别测试 +11 例断言实质；E2E 双档（[a=1,a=6,b=8,c=1] vs [a=1,b=8,c=1]）；零运行时改动 git 全量核查
  - 回归：auditor 以 `-fn` 完整跑齐四模块 BUILD SUCCESS（core 1754/flow 202/runtime 1336/sql 110 全 0 失败；nop-batch 偶发 flake 解释经多轮交叉验证成立且该轮通过）
  - Q1 裁定落档四处一致（plan/roadmap 括注/FU-E6/docs）
  - audit 6 Minor 全处置：M-1 空 map 断言、M-2 日志数字更正、M-3 事故链如实记录、M-4 翻转顺序注记（本 plan Phase 2 条目内）、M-5 checklist 卫生（本节勾选）、M-6 gate-inventory 归属注记随提交
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/05-wi25-lateness-declaration.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work；查询级水位延迟声明由 roadmap FU-E6 承接
