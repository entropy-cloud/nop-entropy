# 2026-07 博客项目调研汇总：44 份分析报告 + 46 个新项目入库

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/xuqi-blog/2026-07/`（60 篇文章）涉及项目的批量调研；新增 46 个项目克隆到 `~/ai/`（共 241 目录）；44 份深度/简略分析报告；已有报告项目代码更新核查
> Conclusion:

## 一、本次新增报告清单（44 份，本目录 2026-08-01-*）

### A. 计划运行时主线（8 份）—— 汇聚为 nop plan 包从静态模型→运行时计划器
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 1 | planning-with-files-persistent-plan | planning-with-files | 计划注入/完成门/会话恢复 |
| 2 | spec-kit-workflow-engine | spec-kit | 四阶段状态机+RunState+gate |
| 3 | jcode-dag-first-agent | jcode | DAG 任务图+门节点+压缩算法化 |
| 4 | codewhale-workflow-ir-gate | codewhale | Gate 门控+有界 review-repair |
| 5 | archon-yaml-dag-workflow | archon | Trigger Rule+声明式 Hook+双层组合 |
| 6 | conductor-decider-replay | conductor | Decider 状态机+TaskMapper+三级重放 |
| 7 | autoresearch-minimal-longrun | autoresearch | 不可变评估契约+单一指标 |
| 8 | desloppify-quality-harness | desloppify | 活计划+工作队列+skip attestation |

### B. 可靠性/Checkpoint/恢复主线（7 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 9 | hatchet-durable-execution | hatchet | 多版本行+idempotency_key+WAIT_FOR |
| 10 | grok-build-deterministic-replay | grok-build | Journal 确定性重放+多域 checkpoint |
| 11 | rivet-actor-runtime | rivet | 四通道+sleep/keepAwake+saveState |
| 12 | exo-self-evolving | exo | 不可变日志+durable fire/wake redelivery |
| 13 | hive-dual-middleware | hive | 双层中间件+is_clean checkpoint+FanOutTag |
| 14 | dapr-agents-hooks-durable | dapr-agents | 决策钩子双轨同构验证 |
| 15 | mission-control-control-plane | mission-control | 质量门+完成收据签名 |

### C. 上下文工程/压缩/记忆主线（8 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 16 | trustgraph-context-graph | trustgraph | 消息来源元数据 provenance |
| 17 | context-mode-compaction | context-mode | 引用式压缩（双轨保真/摘要） |
| 18 | beads-versioned-graph-memory | beads | 可逆压缩+租约并发+依赖图 |
| 19 | minecontext-context-engineering | minecontext | 类型感知合并+双阈值 |
| 20 | txtai-embeddings-factory | txtai | Factory+混合融合策略 |
| 21 | docsgpt-workflow-cel | docsgpt | CEL 条件路由+per-source 预算 |
| 22 | pageindex-vectorless-rag | pageindex | 无向量树检索+提示注入三重防护 |
| 23 | browser-use-agent-loop | browser-use | 工具 schema 自动生成+plan 状态机 |

### D. 安全/治理/Guardrail 主线（8 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 24 | agent-governance-toolkit | AGT | 策略外置接口+熔断+审批流 |
| 25 | mcp-gateway-session-security | mcp-gateway | Session 三元组多租户 |
| 26 | opensandbox-deny-default | opensandbox | Deny-by-default+Always-rules+Credential Vault |
| 27 | parlant-conversation-control | parlant | Guideline 关系图+BAIL 语义 |
| 28 | promptfoo-redteam-eval | promptfoo | Plugin+Grader 分离测试闭环 |
| 29 | go-micro-tool-middleware | go-micro | 工具中间件栈+checkpoint 状态机 |
| 30 | goose-provider-hook-recipe | goose | Recipe 配方组合层 |
| 31 | gstack-sprint-workflow | gstack | WIP checkpoint+分层 injection 防御 |

### E. Hook/生命周期/编排主线（6 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 32 | litellm-hook-lifecycle | litellm | 22+ hook 分类法+CustomBatchLogger |
| 33 | plano-declarative-filter-chain | plano | 声明式 filter chain+input/output 双链 |
| 34 | trellis-state-injection | Trellis | 状态注入 hook 模式（同构验证） |
| 35 | baml-typed-llm-language | baml | Salsa 增量编译+类型化 LLM 调用 |
| 36 | openscience-declarative-agent | openscience | 声明式 agent+glob 权限规则集 |
| 37 | orca-orchestration | orca | Run/mailbox 联邦化原语 |

### F. Provider 路由/可观测/工具（4 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 38 | cc-switch-provider-circuit-breaker | cc-switch | 熔断器+有序故障转移 |
| 39 | claude-code-router-context-archive | claude-code-router | Context Archive+failure-classifier |
| 40 | helicone-gateway-observability | helicone | 分布式限流+Wallet 预算 |
| 41 | agent-browser-daemon | agent-browser | 破坏性动作门禁+restore 校验 |

### G. 低相关/简略（3 份）
| # | 报告 | 项目 | 核心借鉴 |
|---|------|------|----------|
| 42 | insforge-baas | insforge | provider/service 分层+中间件链 |
| 43 | open-autoglm-vlm | open-autoglm | 动作分发表+敏感操作接管 |
| 44 | low-relevance-projects-survey | vibevoice/mlflow/pathway/ponytail/PEG/ecc | 实验追踪/增量计算/skill（低相关汇总） |

## 二、已有报告项目代码更新核查（无需更新）

| 项目 | 已有报告 | 报告日期 | 代码最后提交 | 结论 |
|------|---------|---------|-------------|------|
| deepagents | 2026-06-05-deepagents-analysis | 06-05 | 2026-02-07 | 无更新 |
| omnigent | 2026-06-15-omnigent-vs-nop | 06-15 | 2026-06-15 | 无更新 |
| OpenHarness | 2026-06-08c-openharness-survey | 06-08 | 2026-06-04 | 无更新 |
| agno | 2026-06-13-agno-vs-goal-driver | 06-13 | 2026-06-12 | 无更新 |

## 三、7 月文章项目全景（对照 `~/ai/README.md` 11 大类）

60 篇 7 月文章涉及项目全部覆盖（已有报告的标注 R，本次新增标注 ✅）。完整分类见 `~/ai/README.md`。本次新增 46 个项目已按类入库（共 241 目录）。

## 四、核心洞察（44 份报告共识）

nop-ai-agent 下一步工作的**优先级**（按多份报告交叉共识排序）：

1. **PlanRun 运行时计划器**（A 主线 8 份共识）：spec-kit 状态机骨架 + jcode DAG 任务结构 + planning-with-files 注入机制 + conductor Decider 执行器 + codewhale Gate 门控。
2. **Checkpoint 升级**（B 主线 7 份共识）：hatchet 多版本+idempotency_key+WAIT_FOR + grok-build Journal 确定性重放 + hive is_clean+索引/全量分离。
3. **Actor 运行时补全**（rivet 四通道 + exo durable fire/wake）。
4. **上下文工程**（C 主线 8 份共识）：trustgraph 来源元数据 + context-mode 引用式压缩双轨 + beads 可逆压缩。
5. **安全/治理**（D 主线 8 份共识）：AGT 策略外置接口+熔断+审批流 + opensandbox Always-rules + parlant Guideline 关系图 + promptfoo 测试闭环。
6. **Hook 体系**（E 主线）：litellm 22+ 分类法补全 + plano 声明式 filter chain。
7. **Provider 弹性**（F 主线）：cc-switch 熔断+有序故障转移。

## References
- `~/ai/README.md`（分类索引，本次新增 46 项 ✅）
- `~/ai/xuqi-blog/2026-07/`（60 篇文章原文）
- 本目录 44 份 `2026-08-01-*` 报告
- `ai-dev/design/nop-ai-agent/`（对应设计文档，各报告尾段已索引）
