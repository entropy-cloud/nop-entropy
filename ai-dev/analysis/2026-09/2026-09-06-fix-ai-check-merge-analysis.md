# fix-ai-check 分支合并分析：是否合并、如何合并

> Status: resolved
> Date: 2026-09-06
> Scope: git 分支拓扑 / check2(plan346) 处置战役 / 全模块修复合并策略
> Conclusion: **需要合并**。分支含 38 个 master 没有的真实修复提交（约 19 个检查单元的 check2 处置 + plan346 收口，含多例 P0/P1），已在分支侧全量测试收口（27608/0/0）；gateway-bizauth 等另一部分已通过 08-27 的 cherry-pick 落在 master。推荐**单次 merge + 逐文件裁决**（沿用 08-23 首次合并先例），合并前必须先收口 master 工作区在飞的 nop-job 重构，nop-task 区需按 finding 级对账（master plan349 与分支 check2 双侧各修一遍）。

## Context

- fix-ai-check 分支（独立 worktree `/Users/abc/app/nop-entropy-wt/nop-entropy-fix-ai-check`）是否合并回 master、如何合并，需要裁决。
- 来料说法（有人反馈）：① 不是 rebase 后的残留；② 64 个提交涉及 1551 个 Java 文件、67920 行删除 / 23495 行新增；③ LoginServiceImpl 登录失败计数原子锁、gateway-bizauth header 大小写修复等核心修改在 master 上不存在；④ 该分支是从 master 拉出的并行 check2 审计修复分支，尚未合回。
- 本文全部结论基于本仓 git 实测（命令见 References），判断依据均可复现。

## Analysis

### 1. 分支真实形态与时间线

拓扑事实（实测）：

| 项 | 值 |
|---|---|
| 分支 HEAD | `1fe78888e2`（2026-08-31），worktree 干净（0 脏文件） |
| master HEAD | `3f8ed93a4f`（2026-09-06），**工作区有在飞未提交工作**（nop-job 8 个 untracked + 0 个 modified，另有其他模块 6 个 modified） |
| merge-base | `47cf66b865`（2026-08-23） |
| master..fix-ai-check | 64 提交（62 非 merge + 2 个 merge） |
| fix-ai-check..master | 232 提交（分支已落后 master 6 天） |

时间线（依据提交与 `ai-dev/logs/2026/08-23.md`）：

1. **08-22~08-23 第一轮（check-findings / plan344）**：在分支上完成，master 以 merge commit `7348fbc6a5` 整体合入（37 提交、7 处冲突逐一裁决），随后**分支 rebase 对齐到新 master**，对齐点即今日 merge-base `47cf66b865`。日志原话："fix-ai-check 分支 rebase onto 新 master……后续可安全推送或删除"。
2. **08-24~08-28 第二轮（check2 / plan346，56 单元 7 Phase）**：在分支上执行并收口——08-28 `dd5bfd40ed` "plan346 收口——closure audit 9 项全 PASS，Plan Status → completed"，同日全量测试 27608/0/0 绿（`96ce75e0a2`）。
3. **08-27 部分回传 master**：master 以 cherry-pick 方式落地了 Phase1/2 核心单元处置（gateway-bizauth、nosql-cdc、db-migration、nop-orm-eql、nop-xlang、nop-commons、nop-job），master 侧 plan346 副本状态停留在 **active**。
4. **08-28~09-06 master 独立演进**：nop-stream 大战役（81 提交，含合并 nop-stream-productization 分支）、plan 2260 report-pdf 质量战役（09-05~09-06）、plan 349 nop-task 全量修复（09-05，源于 master 侧 `ai-dev/analysis/2026-09/2026-09-05-nop-task-module-analysis.md`）、EQL/DAO 修复、nop-job RemoteTask 重构（**进行中、未提交**）。
5. **08-30~08-31 分支最后的活动**：反向移植 master 的跨仓 plan 产出（flux-rendering 文档、web 导出工具），此后分支闲置至今。

结论：这是一个**长期两用分支**（先修复战役、后反向移植），不是孤儿，也不是一次性的 cherry-pick 源。它经历过一次完整 merge + 一次逐单元 cherry-pick，剩余部分（约 19 个单元的处置 + 收口文档）尚未回传。

### 2. 来料说法逐条核查

| # | 说法 | 裁决 | 证据 |
|---|------|------|------|
| 1 | 不是 rebase 后的残留 | **部分成立** | 38 个提交的补丁在 master 上确实不存在（`git cherry` 38 个 `+`），是真工作。但 64 个提交中 **24 个与 master 补丁等价**（08-27 cherry-pick 回传的部分），"64 个未合并提交"高估了约 60%。且分支 08-23 曾主动 rebase 对齐 master——"残留"这个提法本身不成立 |
| 2 | 1551 个 Java 文件、67920 删 / 23495 增 | **口径误导** | 文件数 1551 与**两点 diff**（`git diff master fix-ai-check -- '*.java'`）精确吻合；本日复测为 +26306/-76348（来料读数对应数日前更早的 master HEAD，方向一致：删除≈新增 2.9 倍）。两点 diff 的"删除"绝大部分是**回退 master 232 个提交的代码**，不是分支要删的内容。**三点 diff**（分支真实改动，`git diff master...fix-ai-check`）= 730 个 Java 文件，**+28058/-1945**，以新增为主 |
| 3 | LoginServiceImpl 登录失败计数原子锁不在 master | **属实** | 分支 `72e7cc0ae3`（08-26，nop-auth 15 条处置）改 LoginServiceImpl（+67 行）+ OAuthLoginServiceImpl + 6 个新测试类（含 TestLoginFailCountAtomicity 329 行）；文件级 diff 证实 master 缺失（+64 行差）。注意：**nop-auth 是 ask-first 保护区**，合并时需 owner 确认 |
| 4 | gateway-bizauth header 大小写修复不在 master | **不属实** | master `299f7765f0`（08-27 落地）与分支 `b107a7283b` **同一作者时间戳 2026-08-24 20:48:31**，内容补丁等价（`git cherry` 判 `-`），含 AiAuth Authorization 大小写 P0 修复在内的全部 18 条 |
| 5 | 独立 worktree、并行 check2 分支、尚未合回 | **属实（需补充）** | worktree 独立属实；但"尚未合回"仅对剩余 ~19 个单元处置 + 收口成立——第一轮已整体合并、第二轮核心单元已 cherry-pick 回传 |

### 3. 真正未合并的内容（38 个唯一补丁提交）

按价值分类：

**A. check2 单元处置（主体，~21 个提交）** —— master 缺失的修复，含 P0：

- Phase 1：nop-task 19 条（**P0×2**：suspend 出口 endStep 判空 NPE、挂起持久化 SUSPENDED 不 runCleanup/恢复不短路）、nop-report 26 条（**P0**：COUNTIF/SUMIF 多字符操作符字典序错配）、nop-rule 15 条（**P0**：DecoratedExecutableRule 正常路径漏调 afterExecute）、nop-job 收口 4 条残留
- Phase 2：nop-core 19（P1×3）、nop-api-core 25（P1×2）、kernel-small 13、nop-core-framework 23（P1×4）、nop-orm 20（P1×3）、orm-periph 13（P1×2）、nop-dao 18 + xlang-java-truffle 10
- Phase 3：nop-biz 13（P1×3）、nop-graphql 15（P1×2）、**nop-auth 15（P1×2：getSessionInfoForUser 误用 setLoginType、登录失败计数非原子）**、nop-sys 13（P1）、nop-wf 15（P1×4）、nop-batch 20、nop-dyn 16、nop-metadata 13、xlang-java-e2e golden fixture 再生
- 每个处置提交自带红验证测试（如 nop-auth 新增 6 个测试类 ~1100 行）

**B. plan346 收口文档** —— master 副本仍 `active`，分支副本 `completed` + closure audit 9 项 PASS + roadmap/owner-doc 同步。

**C. 测试加固 6 个提交** —— retry 退避基准、auth-service JsonRpc 录制、stream-rocksdb 交错采样、stream 墙钟断言、auth-web 快照再生、task GraphTaskStep drain 竞态（注意与 master `afa0ad7bd3` 的"GraphTaskStep 计数竞态"修复**疑似双修**，见 §4）。

**D. 低价值/应弃项** —— 08-30~31 的"master 同源移植"反向移植提交（flux-rendering 文档、web 导出工具、bundle 刷新）：master 已有同源原版，合并裁决时应以 master 版为准，仅保留分支独有的增量。

**E. chore** —— i18n 聚合再生（合并后应重新生成而非手工合并）。

不合并的代价：master 侧 `ai-dev/audits/check2/*.md` 报告描述的 19 个单元 P0~P3 发现中，修复只存在于分支；nop-auth 登录原子性等安全修复悬空；plan346 状态与事实脱节；分叉随 master 每日前进持续放大（已 6 天）。

### 4. 合并冲突面（`git merge-tree --write-tree master fix-ai-check` 实测）

**67 个唯一冲突文件**：31 Java + 24 ai-dev 文档（audits/logs/plan346，附加式可并集解决）+ 8 个前端 hash 产物（重新构建，不手合）+ 4 i18n 聚合（再生成）+ 杂项（flux-web.xlib、v1.task.xml、index.html）。

Java 冲突按风险分层：

| 风险 | 模块（文件数） | 性质 | 裁决方向 |
|------|--------------|------|---------|
| **高** | nop-task（11 Java + 1 XML） | **双侧各修一遍**：分支 check2 19 条（SUSPEND 语义）vs master plan 349 全量 P0-P3（SUSPEND 契约化，09-05，更新更深） | 以 master plan349 为主干，按 finding 级对账表回补分支独有发现；不能按文件任选一侧 |
| **高** | nop-job（3 + modify/delete ×2） | master 已删除 HttpRpcPollTaskClient 并**在飞**重构 RemoteTask（8 个未提交文件）；分支在其上做过修复 | 等 master 在飞工作提交后，以 master 重构后结构为主干，回补分支 4 条残留修复 |
| **高** | nop-auth（LoginServiceImpl/OAuth 等，ask-first 保护区） | 分支独有安全修复，master 无冲突演进 | 提请 owner 确认后取分支侧 |
| 中 | nop-report（4） | 两条不同战役：分支 check2 26 条（公式语义）vs master plan 2260（PDF 质量，09-05~06，含今日 Blocker 修复） | 双方关注点不同，逐文件做语义合并（两边都要） |
| 中 | GraphTaskStep 疑似双修 | 分支 `7944ca8108` vs master `afa0ad7bd3` 都修了计数/drain 竞态 | diff 对比二选一或取并集，避免叠加 |
| 低 | nop-dao（2）/nop-wf（2）/nop-excel（2）/nop-dyn（2）/WebPageExporter/flux-web.xlib/stream-rocksdb 测试（1） | 分支修复 vs master 常规演进 | 逐文件以"分支修复 + master 演进"合并；反向移植文件取 master |

24 个已 cherry-pick 提交触及的文件大多可无冲突自动合并（双侧内容一致），不构成额外负担。

### 5. 方案对比

| 维度 | A. 单次 merge + 裁决 | B. 逐提交 cherry-pick 38 个 | C. rebase 后合并 | D. 不合并 |
|------|--------------------|---------------------------|-----------------|----------|
| 冲突处理量 | 一次 59 文件 | 38 次提交 × 重复冲突（task/report 区反复） | 38 次重放 × 232 提交漂移，冲突最重 | 0（但欠账留存） |
| plan346/文档 | merge 时一并取 completed + 对账 | 仍需手工合并 plan346 | 同 B | 永久 active 假状态 |
| 历史可审计性 | 好（一次 merge 节点，先例 08-23） | 差（碎片化） | 差（重写分支历史） | — |
| 风险 | 一次性 29 Java 裁决，需全量验证 | 单步可控但总量更大、易漏 | 高且无收益 | P0/P1 悬空，分叉扩大 |
| 先例 | ✅ 08-23 首次合并即此法（7 冲突裁决 + 验证 + 分支同步） | 无 | 无 | — |

被否决方案：

- **B**：38 个提交中文档/收口/chore 占比高，逐个 cherry-pick 收益低；nop-task 等双修区无论怎么合都需要 finding 级对账，cherry-pick 不减少语义工作量，反而丢失"一次合并、一次验证"的收口形态。
- **C**：rebase 会把 38 个补丁逐个重放在 232 个新提交之上，task/report/job 区每提交都可能冲突；且重写分支历史违反本仓 merge-first 惯例，无额外收益。
- **D**：见 §3 末尾，代价明确且随时间放大。

### 6. 推荐执行路径（方案 A 的操作序列）

1. **前置**：收口 master 工作区在飞的 nop-job RemoteTask 重构（提交或 stash），保证 master 干净后再合并。
2. `git merge --no-ff fix-ai-check`，按 §4 裁决矩阵处理 59 个冲突文件：
   - ai-dev 文档取并集（双侧日志条目都保留）；
   - 前端 hash 产物与 i18n 聚合**一律再生成**（重建 web bundle、重跑 i18n 聚合），不手合；
   - nop-task 生成 plan349 × check2-19条 的 finding 级对账表后语义合并；
   - nop-job 以 master 重构后结构为主干回补分支残留；
   - GraphTaskStep 双修查重；
   - "master 同源"反向移植文件取 master 版；
   - nop-auth 变更提请 owner 确认（ask-first）。
3. plan346 取分支 `completed` 状态，并与 master 侧勾选对账（同一 plan 的子集，不丢 master 独有勾选记录）。
4. 验证：`./mvnw clean install -T 1C` 全量（基线：分支侧 08-28 27608/0/0 绿；master 侧 09-05 全绿）。
5. **收尾**：合并验证通过后退役 fix-ai-check 分支与 worktree——其收口后的"反向移植"用途在 master 拥有全部内容后失去意义，保留只会诱发第三次分叉。
6. **流程**：本合并横跨 nop-core/nop-xlang（plan-first 保护区）与 nop-auth（ask-first 保护区），按 AGENTS.md 应立 `ai-dev/plans/` 执行条目并做独立 closure audit；nop-task 对账表作为该 plan 的首个交付物。

## Conclusion

- **是否合并：需要。** 分支的 38 个唯一提交是真实、带红验证测试、已全量收口的修复资产（含 nop-task/nop-report/nop-rule 的 P0 与 nop-auth 安全修复）；"尚未合回"仅指这部分。来料五条说法中两条属实、一条部分成立、一条口径误导（两点 diff 假象）、一条不属实（gateway-bizauth 已在 master）。
- **怎么合并：单次 merge + 逐文件裁决**（方案 A），先收口 master 在飞 nop-job 工作，nop-task 按finding级对账，nop-auth 走 ask-first，全量构建验证后退役分支。
- 被否决的方案：逐提交 cherry-pick（碎片化且不减语义工作量）、rebase（重放冲突最重且重写历史）、不合并（P0/P1 悬空且分叉扩大）。
- 后续工作：待建 `ai-dev/plans/` 合并执行条目（含 nop-task 对账表、裁决矩阵、验证与退役清单）。

## Open Questions

- [ ] nop-task 双修的 finding 级交集/差集清单（plan349 全量 × check2 nop-task 19 条）——合并 plan 的首个任务
- [ ] GraphTaskStep 竞态双修（`7944ca8108` vs `afa0ad7bd3`）是否同一缺陷、取哪版
- [ ] nop-auth（ask-first）owner 对登录原子锁等 15 条处置的确认
- [ ] `feat-credential-mfa-phase2` 分支与分支侧 MFA 相关文件（DbEmailCodeStore 等）是否存在后续交叉，未深查

## References

- `ai-dev/logs/2026/08-23.md` —— 首次合并（`7348fbc6a5`）7 处冲突裁决 + 分支 rebase 对齐的先例记录
- `ai-dev/plans/346-check2-audit-p0-p3-remediation.md` —— master 副本 `active` vs 分支副本 `completed`
- `ai-dev/plans/349-nop-task-analysis-remediation.md` + `ai-dev/analysis/2026-09/2026-09-05-nop-task-module-analysis.md` —— master 侧 nop-task 平行战役
- `ai-dev/logs/2026/08-28.md`（分支侧）—— plan346 收口与 27608/0/0 全量测试证据
- 复现命令：
  - `git merge-base master fix-ai-check`；`git rev-list --count master..fix-ai-check` / `fix-ai-check..master`
  - `git cherry master fix-ai-check | sort | uniq -c`（38 `+` / 24 `-`）
  - `git diff master fix-ai-check --numstat -- '*.java'`（两点：1551 文件）vs `git diff master...fix-ai-check --numstat -- '*.java'`（三点：730 文件，+28058/-1945）
  - `git merge-tree --write-tree --name-only master fix-ai-check`（59 冲突文件）
  - 双胞胎提交验证：`git log -1 --format='%ai' b107a7283b` vs `git log -1 --format='%ai %ci' 299f7765f0`
