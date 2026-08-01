# Gstack Sprint 数据流转与 WIP Checkpoint 分析 & Nop AI Agent Team/压缩/安全

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/gstack`（Garry Tan/YC，TS+Markdown，Claude Code 技能集合，~1085 文件）vs `nop-ai-agent`（team 包 + compact + security）
> Conclusion:

## 一、总览

**Gstack** 把单 agent 变成"虚拟工程团队"：23 个专家技能（纯 Markdown slash command，无运行时代码框架）。

| 维度 | gstack | nop-ai-agent |
|------|--------|--------------|
| 技能形态 | 纯 Markdown slash command（零代码） | DSL + Java |
| 团队流水线 | Sprint: Think→Plan→Build→Review→Test→Ship→Reflect | team 包静态配置 |
| Checkpoint | WIP commit + 结构化 [gstack-context] body | checkpoint append-only |
| 安全 | 分层 prompt injection 防御（ML 分类器+Haiku 投票+canary） | security + ContentOrigin |
| 记忆 | Taste memory（5%/周衰减） | memory 适配器 |

## 二、核心机制详解

### 2.1 23 专家技能（纯 Markdown）
- `/office-hours`、`/review`、`/qa`、`/ship`、`/cso`（首席安全官）、`/designer` 等 slash command。
- 无运行时代码框架——技能即 prompt（Markdown 定义角色/规则/流程）。

### 2.2 Sprint 流水线 + 技能间数据流转
- Think→Plan→Build→Review→Test→Ship→Reflect 七阶段。
- **技能间 artifact 数据传递**：design doc 喂 plan，plan 喂 review，review 喂 test——上游 agent 产出作为下游 agent 的结构化输入。

### 2.3 持续 WIP Checkpoint
- 自动 WIP commit 含结构化 `[gstack-context]` body：决策/剩余工作/失败方法。
- `/context-restore` 恢复：从 WIP commit body 重建上下文。
- `/ship` filter-squash：保留非 WIP commit，squash WIP commit——**清理工作痕迹但保留正式提交**。

### 2.4 分层 Prompt Injection 防御
- **22MB ML 分类器**：检测 prompt 注入。
- **Haiku transcript 投票**：第二个分类器交叉验证。
- **canary token**：水印追踪信息泄露。
- **verdict combiner**：双分类器**共识才阻断**（降低误报）。

### 2.5 Taste Memory
- agent 偏好学习作为一等公民。
- **5%/周衰减**：偏好随时间淡化（防止过时偏好固化）。

## 三、对 nop-ai-agent 的借鉴要点

1. **Sprint 技能间 artifact 数据流转**（高价值，team 包）——上游 agent 产出作为下游 agent 结构化输入，对应 nop team 包 agent 协调。与 conductor 的动态子工作流（`2026-08-01-conductor-decider-replay-analysis.md`）互补：conductor 是工作流引擎驱动，gstack 是 artifact 驱动。
2. **WIP checkpoint + filter-squash**（高价值）——结构化上下文自动提交 + 按需压缩，直接增强 checkpoint 的人可读性与 git 集成（与 planning-with-files 文件三件套 `2026-08-01-planning-with-files-persistent-plan-analysis.md`、beads 可逆压缩 `2026-08-01-beads-versioned-graph-memory-analysis.md` 呼应）。`[gstack-context]` body 的"决策/剩余工作/失败方法"三段式是 checkpoint 元数据的实用结构。
3. **分层 injection 防御（多分类器 + canary + verdict combiner）**（中价值，security 包）——双分类器共识才阻断，降低误报。可用于 middleware 洋葱链的安全层。与 pageindex 的提示注入三重防护（`2026-08-01-pageindex-vectorless-rag-analysis.md`）互补。
4. **Taste memory + 衰减**（中价值，team 包）——agent 偏好学习，team 包实现 agent 个性化。5%/周衰减防止过时偏好固化——简单的衰减策略可直接落地。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **WIP checkpoint + /context-restore**：自动 WIP commit（结构化 `[gstack-context]` body）→ `/context-restore` 恢复——**会话级断点续跑**。
- **/ship filter-squash**：保留非 WIP commit，squash WIP——失败痕迹清理后重试。
- **Sprint 阶段回退**：Review 未过 → 回 Build 重试（技能间数据流转支持阶段 replan）。
- **Taste memory 衰减**：偏好 5%/周衰减——重试时过时偏好不固化。
- **对 nop 的启示**：`[gstack-context]` body 三段式（决策/剩余/失败）是 nop checkpoint 元数据的参考；/context-restore 对应 nop session 恢复。

## 四、优缺点

### 优点
1. 流程优先而非框架优先——23 技能即 Markdown，极简。
2. Sprint 生命周期内技能间数据流转清晰。
3. 分层 injection 防御的"双分类器共识"降低误报。
4. WIP checkpoint + filter-squash 是人机协作的实用模式。

### 缺点
1. 非引擎/框架——是 Claude Code 的 prompt 集合，无程序化控制、无持久化执行。
2. 强耦合 Claude Code 技能系统。
3. 高度流程主观（Garry Tan 个人风格）。

## 五、结论

Gstack 的 Sprint 数据流转 + WIP checkpoint + 分层 injection 防御是 nop team/compact/security 的实践参考。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D8**（23 专家技能纯 Markdown）、**D5**（Sprint 流水线+artifact 数据流转）、**D6**（分层注入防御双分类器）、**D4**（WIP checkpoint）、**D12**（/context-restore）。缺失/薄弱：D1（非引擎）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **team 协调**：nop team 包（TeamTask/blockedBy/nop-task DAG）比 gstack 的 23 技能 Markdown 更工程化（nop 是代码 + DSL，gstack 是纯 prompt）。
- **checkpoint**：nop `DBCheckpointManager` append-only 比 gstack 的 WIP commit 更结构化。
- **安全**：nop security 6 层（ContentOrigin/AutoApproveGate）比 gstack 的双分类器更系统化。

**必要参考的增量（以超越方式吸收）**：
- **artifact 数据流转**（上游 agent 产出作为下游结构化输入）：nop team 包 agent 协调可增加显式 artifact 传递——真正增量。
- **Taste memory + 衰减**（偏好 5%/周衰减）：nop team 包 agent 个性化可增加——增强。

**总评**：nop-ai-agent **全面超越** gstack（team 包/checkpoint/安全更工程化）；artifact 流转 + Taste memory 两个增量吸收。

## References
- `~/ai/gstack/`（commands/、skills/、security/）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`、`nop-ai-agent-react-engine.md`（compact 包）、`nop-ai-agent-security-and-permissions.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`、`2026-08-01-beads-versioned-graph-memory-analysis.md`、`2026-08-01-pageindex-vectorless-rag-analysis.md`、`2026-08-01-conductor-decider-replay-analysis.md`
