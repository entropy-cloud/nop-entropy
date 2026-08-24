# 346 check2 审计 P0-P3 条目全量处置

> Plan Status: active
> Last Reviewed: 2026-08-24
> Source: `ai-dev/audits/check2/`（26 份单元报告，447 条发现：P0=13 / P1≈70 / P2≈135 / P3≈229，以各报告发现列表为准）
> Related: `ai-dev/plans/344-check-audit-p1-p2-p3-remediation.md`（第一轮 check 系列处置，已完成的单元其修复可能已覆盖 check2 同位置发现，需逐条复核而非继承结论）

## Purpose

把 `ai-dev/audits/check2/` 26 份单元报告中全部 P0-P3 条目收口：每条获得明确的终态裁定并在原报告中标注，修复项附带回归测试，非修复项写清理由。check2 是对同一 live code 的独立复审（2026-08-23 基线），此后仅 nop-batch/nop-dyn 两单元在 08-24 经 plan344 修复，其余发现预计大部分仍为 live defect——每条必须对照当前代码复核，不得从 check 系列报告继承结论。

## Current Baseline

- 2026-08-23: check2 Phase 1（16 单元）+ Phase 2（5 单元）+ Phase 3 批次 3A（nop-report/nop-rule/nop-batch/nop-dyn）报告落盘；`check2/nop-metadata.md`（13 条）报告完整但未提交（前一会话中断遗留）。
- 2026-08-24: plan344 Phase 4 完成 nop-batch、nop-dyn 两单元修复（标注在 check/ 系列报告）；check2/nop-batch.md、check2/nop-dyn.md 未标注，其发现需对照已修复代码复核（预计大量"复查已修复/非问题"）。
- P0 抽查（2026-08-24）：StringHelper.parseQuery、AiAuthGatewayInterceptor Authorization 头、GlobalFunctions AND/OR subList 三处均确认仍为 live defect。
- 其余 check2 报告 0 标注；`grep '^### \[P[0123]\]' 计数` 与 `grep '处置（fix-ai-check' 计数` 的差值即剩余工作量。

## Goals

- 26 份报告中每条 P0-P3 条目末尾出现 `> **处置（fix-ai-check 分支，YYYY-MM-DD）**: ...` 标注，终态四类之一（与 plan344 惯例一致）：
  1. **已修复**：附修复说明 + 回归测试（行为类修复必须有能区分对错的测试；纯文案/死代码删除等注明免测试理由）。
  2. **复查非问题**：审计前提有误，或该位置已被后续修复覆盖（注明对应的修复 commit/测试）。
  3. **裁定暂缓**：需设计决策/大范围改造，写明决策点与影响面。
  4. **裁定不修复**：长期产品化维护角度不值得修（死代码无调用方、修复回归风险大于收益等），写明理由。
- 修复后对应模块 `./mvnw test -pl <module>`（必要时 `-am`）绿；新增错误码同步 i18n（zh-CN/en）。
- 新增错误码遵循两档策略：框架核心/公共 API 用 ErrorCode 常量，模块内部可用模块异常类；禁止 bare RuntimeException。

## Non-Goals

- 不重开 plan344 已裁定的暂缓/不修复条目（除非 check2 报告提供了新证据）。
- 不做超出条目最小修复范围的重构；修复中新发现的缺陷记录在标注里，不扩大范围。
- 不处理 check2 尚未产出报告的单元（nop-excel、format-record、file-retry-tcc 等 in-progress/pending 审计单元不在本 plan 范围）。
- 不修改 `docs-for-ai/` 规范内容（除非修复行为与 owner doc 冲突，此时按 AGENTS.md 上报）。

## Scope

### In Scope

- 26 份 check2 报告（Phase 划分见下）的 P0-P3 条目处置：复核、修复、测试、标注。
- 与修复直接相关的测试文件新增/调整、错误码与 i18n 资源。
- `ai-dev/audits/check2/ai-check2-roadmap.md` 进度日志、`ai-dev/logs/` 每日日志。

### Out Of Scope

- 各报告"补充说明（核查过但未立为发现的项）"段落。
- check2 审计本身未完成的单元（报告不存在的）。

## Execution Plan

> 单条处置流程：live code 复核（行号/证据以当前分支为准，先查该位置是否已被 plan344 等修复覆盖）→ 行为类修复先写红测试（stash 法验证红）→ 最小修复 → 绿 → 模块全量测试 → 报告条目标注。每份报告全部条目标注完成才算该单元完成。每个单元一个独立子代理执行，主会话负责验收、提交、plan/log 维护。

### Phase 1 - 含 P0 的单元（13 条 P0 优先清零）

Status: in progress
Targets: 10 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [x] nop-commons.md（P0×1 P1×5 P2×7 P3×15，共 28 条）— 2026-08-24 完成：26 修复 + 2 暂缓（IoHelper 原生反序列化 ObjectInputFilter 白名单需平台裁定 / DateHelper Locale 语义需平台决策）。P0: parseQuery 多值收集 put(key,list)。红验证失败形态逐条吻合（splitChunk 在 HEAD 上 OOM 崩 JVM 为最强红证据）；268 tests 绿（+38 新用例）；报告两处事实偏差（P3-16 死校验非误抛 NPE、P3-23 OOM 非 /by zero）在标注中纠正；28/28 标注。附带修复 FileHelper.countLines 空文件 0 容量死循环（报告外新发现）；新发现 nop-xlang EvalHelper MINUS 分支疑用 MathHelper.min 已记录待 nop-xlang 单元处置
- [x] nop-xlang.md（P0×1 P1×1 P2×3 P3×5，共 10 条）— 2026-08-24 完成：7 修复 + 3 暂缓（EvalBackendRouter 热路径锁需 benchmark+CLD 契约被 truffle 30 处测试消费 / XDslExtender 环引用需错误码归属+物理 resourcePath 键控设计 / bare ISE 16 处错误码化因 i18n 聚合在 nop-cli-core 超单元范围）。P0: AND/OR 宏 subList(1,size())（AND(true,true) 曾恒 false）。超审计新发现 2 项一并修复：EvalHelper MINUS 误用 MathHelper.min（binaryOp(MINUS,1,2) 曾得 1）+ XplParseHelper 常量折叠 OptionalValue 未解包（const x=1+2 曾折叠得 0）。596 tests 绿（+12 用例）；10/10 标注。待跟进（记录未修）：SimpleSchemaValidator.validate 调 checkRange 实参 (bizObjName,propName) 顺序颠倒（潜伏，当前调用方两值多为 null）
- [x] nop-orm-eql.md（P0×1 P2×5 P3×5，共 11 条）— 2026-08-24 完成：10 修复 + 1 复查非问题（`_all` 比较符翻转 NULL 语义——SQL Kleene 三值逻辑下翻转与 NOT 包裹等价，审计前提有误）。P0: 集合属性表源转换在外层查询生成多余 join（CollectionTableSourceHelper buildRelationJoin 后 removeIf 撤销 propJoins 注册，红验证 APP_ROLE 曾出现 2 次）。P2: returning `*` 方言检查前移/hex·bit 双重剥离改裸串直解/ILIKE 静默降级改抛 ERR_EQL_NOT_SUPPORT_ILIKE/to-one 表源丢别名（三参重载透传 userAlias）。P3: 死代码 92 行删除/聚合 `*` 越界抛错/addTable 先检查后 put/Date 字面量 ISO TIMESTAMP/SqlParameterMarker.clone 复制 masked。57 tests 绿（+12）+ nop-orm 回归 178 绿；11/11 标注。待跟进：nop-core FilterBeanToSQLTransformer contains 拼 ilike 同源问题（归 nop-core 单元记录）
- [ ] db-migration.md（P0×2 P1×4 P2×7 P3×3，共 16 条）
- [ ] nosql-cdc.md（P0×1 P1×2 P2×9 P3×13，共 25 条）
- [ ] gateway-bizauth.md（P0×2 P1×4 P2×5 P3×7，共 18 条）
- [ ] nop-job.md（P0×1 P1×2 P2×2 P3×13，共 18 条）
- [ ] nop-task.md（P0×2 P1×6 P2×6 P3×5，共 19 条）
- [ ] nop-report.md（P0×1 P1×5 P2×10 P3×10，共 26 条）
- [ ] nop-rule.md（P0×1 P1×3 P2×3 P3×8，共 15 条）

Exit Criteria:

- [ ] 10 份报告全部条目有处置标注（grep 对账：findings 计数 = 处置标注计数）
- [ ] 13 条 P0 终态均为已修复或复查非问题（P0 不允许暂缓/不修复）
- [ ] 修复项对应模块测试绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 框架层其余单元

Status: planned
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] nop-core.md（P1×3 P2×6 P3×10，共 19 条）
- [ ] nop-api-core.md（P1×2 P2×9 P3×14，共 25 条）
- [ ] kernel-small.md（P1×1 P2×6 P3×6，共 13 条）
- [ ] nop-core-framework.md（P1×4 P2×10 P3×9，共 23 条）
- [ ] nop-orm.md（P1×3 P2×8 P3×9，共 20 条）
- [ ] orm-periph.md（P1×2 P2×3 P3×8，共 13 条）
- [ ] nop-dao.md（P1×2 P2×6 P3×10，共 18 条）
- [ ] xlang-java-truffle.md（P1×2 P2×3 P3×5，共 10 条）

Exit Criteria:

- [ ] 同 Phase 1 前三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 业务层其余单元

Status: planned
Targets: 8 份报告；对应模块代码与测试

- Item Types: `Fix | Decision | Proof`

- [ ] nop-biz.md（P1×3 P2×5 P3×5，共 13 条）
- [ ] nop-graphql.md（P1×2 P2×5 P3×8，共 15 条）
- [ ] nop-auth.md（P1×2 P2×4 P3×9，共 15 条）
- [ ] nop-sys.md（P1×1 P2×4 P3×8，共 13 条）
- [ ] nop-wf.md（P1×4 P2×8 P3×3，共 15 条）
- [ ] nop-batch.md（P1×5 P2×6 P3×9，共 20 条；08-24 已修，预计大量复查已修复）
- [ ] nop-dyn.md（P1×4 P2×7 P3×5，共 16 条；08-24 已修，预计大量复查已修复）
- [ ] nop-metadata.md（P1×2 P2×6 P3×5，共 13 条）

Exit Criteria:

- [ ] 同 Phase 1 前三条
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 26 份报告全部 P0-P3 条目均有处置标注（grep `^### \[P[0123]\]` 总数 = 处置标注总数，无未标注条目）
- [ ] 13 条 P0 全部终态为已修复或复查非问题
- [ ] 所有"已修复"终态条目对应模块测试绿
- [ ] 无 in-scope 条目处于未裁定状态
- [ ] `./mvnw test`（或逐模块 `-pl` 等价覆盖全部修改模块）通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] 独立子 agent closure audit 完成并写入证据

## Deferred But Adjudicated

（执行中按报告逐条填充）

## Non-Blocking Follow-ups

（执行中填充：超出条目范围的新发现）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）
