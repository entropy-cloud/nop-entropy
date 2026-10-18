# nop-entropy 文档体系 SSOT 清理 roadmap

> Last updated: 2026-10-18（v1 初稿。与 ai-dev/analysis/2026-10/2026-10-18-doc-system-redundancy-and-ssot.md 配套；治理规则已落 docs-for-ai/00-start-here/truth-and-precedence.md 与 AGENTS.md）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号。
> mission-driver 兼容：状态行尾部括注必须单层非嵌套；里程碑行按 `- ★ **里程碑：名**（解锁条件）：状态词` 格式，状态词用反引号包裹。
> 目标：把"同一事实 3–5 份副本"收敛为"一事实一出处"，并用可执行门禁防回归；不做一次性全仓重写，按模块/内容类型分片。

## Work Item Status

- WI1 SSOT 归属矩阵与 AGENTS 硬规则落档: `done`
- WI2 冗余基线扫描与清理清单: `todo`
- WI3 nop-stream 试点收敛: `todo`
- WI4 模块总表（module-groups）瘦身: `todo`
- WI5 owner doc 与 design 边界清理: `todo`
- WI6 docs-for-ai 内部去重与导航派生标记: `todo`
- WI7 check-doc-ssot.mjs 与 CI 门禁: `todo`
- WI8 收口审计与规则复盘: `todo`
- WI9 规范性/时效性分离审计与去历史化: `todo`
- ★ **里程碑：M1 规则与试点**（WI1—WI3 全部 done）：`todo`
- ★ **里程碑：M2 全仓结构清理**（WI4—WI6、WI9 全部 done）：`todo`
- ★ **里程碑：M3 门禁与收口**（WI7—WI8 全部 done）：`todo`

## Phase A——治理层（完成门：M1）

### WI1 SSOT 归属矩阵与 AGENTS 硬规则落档

- 在 docs-for-ai/00-start-here/truth-and-precedence.md 增加 Content Ownership Matrix 与 Single-Source Rules；在 AGENTS.md 增加 Document Authority (SSOT) 与 Large Design / Review Tasks 节；在 ai-dev/lessons/19-doc-ssot-and-evidence-first.md 记录过程教训。
- 验证：三处内容互相链接、不复制矩阵；doc-links exit 0（仅本任务新增文件）。
- 状态：done（2026-10-18）。

### WI2 冗余基线扫描与清理清单

- 对 docs-for-ai/、ai-dev/design/、各模块 README 做一次机械扫描，产出「事实 → 出现位置 → 建议 owner」清单；重点扫描：模块清单/子模块职责、能力与限制矩阵、配置默认值、指标名、状态 token、错误码表、测试计数。
- 每次扫描结果入库（ai-dev/analysis/2026-10/ 或 ai-dev/audits/），并给出按模块/文档类的清理批次排序。
- 验证：扫描脚本可复跑；清单覆盖率（按文档类型/模块抽样）可自证；不含推断项。

### WI3 nop-stream 试点收敛

- 以 nop-stream 为试点（冗余最集中）：
  - 模块清单/子模块职责只保留在 module-groups.md 或 owner doc 一处，其余改链接；
  - CEP×RocksDB 等能力/限制只保留在 owner doc 或 design 一处，README/module-groups 改链接；
  - roadmap 状态从 design/README/module-groups 中移除，改为链接到对应 roadmap；
  - 修正 nop-stream/README.md 的入口类 test-scope 过期表述、模块数 11→13。
- 验证：check-doc-links --strict exit 0；module-groups.md 的 nop-stream 行显著缩短（目标 ≤500 字符）；nop-stream 相关文档不再出现重复的完整模块表/能力限制矩阵；人工抽查 10 个事实只有一个 full form。
- 依赖：WI2。

## Phase B——结构清理（完成门：M2）

### WI4 模块总表（module-groups）瘦身

- 按模块分批把 module-groups.md 中"内部实现细节/plan/roadmap 编号/benchmark/技术债"迁移到对应 owner doc 或 design；模块总表只保留稳定事实：路径、定位、子模块清单（或子模块清单链接）、owner doc 链接。
- 每批一个 commit，变更前备份原文到 ai-dev/analysis/ 的迁移记录（不保留第二份 active 副本）。
- 验证：每个模块行 ≤N 字符（由 plan 定）；grep 'plan [0-9]|WI[0-9]' module-groups.md 命中数下降为零；抽查模块数与 pom <modules> 一致。

### WI5 owner doc 与 design 边界清理

- owner doc（docs-for-ai/03-modules/*.md）：只保留已实现能力 + 运维/使用契约；移除设计理由、历史演进、roadmap 状态、执行证据（改链接到 design/roadmap/audits）。
- design（ai-dev/design/*）：只保留决策 + 目标架构 + 拒绝方案 + 约束；移除实现状态、测试计数、plan 编号式的进度叙事（改链接到 roadmap/plans）。
- 验证：按内容类抽样检查；doc-links exit 0；至少 3 个模块/子系统完成并记录（nop-stream、nop-ai、nop-job 优先）。

### WI6 docs-for-ai 内部去重与导航派生标记

- 处理 INDEX.md、各 README.md、02-core-guides 之间的重复：INDEX 只保留路由，不复制规则正文；guide 之间同一规则只保留一个 owner，其余链接。
- 给导航/总表加「派生视图，以 X 为准」标记；评估哪些可由 front-matter/脚本生成。
- 验证：抽样规则（如错误处理、测试约定、权限）只在一个 owner 出现；INDEX 行均为路由；doc-links exit 0。

### WI9 规范性/时效性分离审计与去历史化

- 按 `docs-for-ai/00-start-here/truth-and-precedence.md` 的 **Document Classes** 与 `ai-dev/design/00-design-writing-guide.md` 规则，扫描规范性文档中的时间性内容："Created/Updated/更新于/历史版本/Stage N/plan NNN/WI N/第 N 轮审计/变更记录"，以及以时间顺序叙述的决策演进。
- 处理方式：把历史/演进叙事迁移到时效性文档（analysis/logs/plans/audits）或删除（git 可追溯）；规范性文档原地改写为"当前结论 + 情境化理由"。
- 优先范围：`ai-dev/design/**`（尤其 nop-stream 各设计档）、`docs-for-ai/03-modules/**`、各 subsystem README；至少完成 nop-stream 全目录 + 2 个其他子系统。
- 验证：抽样规范性文档不再含变更历史/日期字段；去历史化后的设计文档仍能独立解释"选了什么、为什么、拒绝了什么"；doc-links exit 0。

## Phase C——门禁与收口（完成门：M3）

### WI7 check-doc-ssot.mjs 与 CI 门禁

- 新增 ai-dev/tools/check-doc-ssot.mjs（或扩展既有 checker）：
  - roadmap/plan 外出现状态 token（todo/planned/done 形式的状态行）→ 报错；
  - 模块总表出现 plan/WI 编号或 benchmark/限制长段 → 报错/警告（按基线 allowlist 过渡）；
  - 同一 canonical claim 关键词在禁止位置出现 → 警告；
  - 导航文档缺少“派生视图”标记 → 警告。
- 先以基线 allowlist 落成（不阻塞存量），再逐批清空；目标最终默认 CI 运行。
- 验证：self-test 正反例；基线 allowlist 有 owner/到期条件；CI 接入后 exit 0。

### WI8 收口审计与规则复盘

- 独立审计：抽查 20 个事实，确认只有一个 full form；checker 无 red flag；doc-links exit 0。
- 复盘：统计清理前后重复副本数、模块总表长度、审计中重复出现的不一致类；更新 lesson/规则（若规则需要调整，只改 canonical owner，不复制）。
- 验证：审计报告 APPROVED；roadmapAllDone；日志记录执行事实。

## 执行约定

- **E1 分片**：每次只处理一个模块或一类事实；禁止一次性重写全仓。
- **E2 链接优先**：删除副本前先建立链接；删除后跑 doc-links。
- **E3 规则单一出处**：SSOT 矩阵只在 truth-and-precedence.md 维护，AGENTS/lesson/roadmap 只引用。
- **E4 不制造新副本**：迁移记录放 ai-dev/analysis/ 且标 Status: resolved/superseded，不作为规范引用。
- **E5 与既有 roadmap 边界**：本 roadmap 只做文档体系；不修改产品能力/代码。
