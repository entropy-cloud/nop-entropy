# nop-ai-agent Audit Findings Tracker

> 集中追踪 nop-ai-agent 各轮深度审计的发现与修复状态。每条记录发现 ID、严重程度、修复状态、落地 plan。完整修复实现细节见对应 plan 文件；原始审计证据见各审计目录。

## 2026-06-15 deep audit

来源：`ai-dev/audits/2026-06-15-deep-audit-nop-ai-agent/`

| 发现 ID | 严重程度 | 状态 | 落地 plan |
|---------|---------|------|-----------|
| AUDIT-13-15 | P0 | ✅ | plan 190（SessionIds 两层校验 fail-closed） |
| AUDIT-13-16 | P2 | ✅ | plan 191（AgentNames allow-list 校验） |
| AUDIT-13-01 | P1 | ✅ | plan 193（secure-by-default 构造期兜底） |
| AUDIT-13-02 | P1 | ✅ | plan 194（auditLogger 默认 Slf4jAuditLogger） |
| AUDIT-13-04 | P1 | ✅ | plan 199（DefaultApprovalGate 替代 AutoApproveGate） |
| AUDIT-14-01 | P1 | ✅ | plan 197（runningExecutions putIfAbsent + fail-fast） |
| AUDIT-14-04 | P1 | ✅ | plan 195（write-to-tmp + ATOMIC_MOVE） |
| AUDIT-09-01 | P1 | ✅ | plan 196（NopAiAgentException extends NopException） |
| AUDIT-09-02 | P2 | ✅ | plan 198（IllegalArgumentException → NopAiAgentException 统一） |
| L23-SDI | P1 | ✅ | plan 200（4 个 Default* secure 默认实现） |

## 2026-06-19 deep audit — security (dimension 13)

来源：`ai-dev/audits/2026-06-19-1355-deep-audit-nop-ai-agent/`

| 发现 ID | 严重程度 | 状态 | 落地 plan |
|---------|---------|------|-----------|
| AUDIT-13-1 | P1 | ✅ | plan 270（symlink 防护层 resolveSymlinkRealPath） |
| AUDIT-13-7 | P1 | ✅ | plan 270（DockerSandbox allowedBaseDirs 白名单） |
| AUDIT-13-12 | P1 | ✅ | plan 270（resumeSession 租户上下文重建） |
| AUDIT-13-6 | P2 | ✅ | plan 270（Slf4jAuditLogger 日志注入防护） |
| AUDIT-13-8 | P2 | ✅ | plan 274（SandboxConfig cpuCores 语义） |
| AUDIT-13-9 | P3 | ✅ | plan 274（环境变量键 POSIX 校验） |
| env-source | P2 | ✅ | plan 276（SandboxRequest.environmentVariables 来源追溯） |

## 2026-06-19 deep audit — reliability (dimension 14)

来源：`ai-dev/audits/2026-06-19-1355-deep-audit-nop-ai-agent/`

| 发现 ID | 严重程度 | 状态 | 落地 plan |
|---------|---------|------|-----------|
| AUDIT-14-01 | P1 | ✅ | plan 271（callAgent 超时取消子 agent） |
| AUDIT-14-02 | P1 | ✅ | plan 271（DBMessageService at-least-once 交付） |
| AUDIT-14-03 | P1 | ✅ | plan 271（LLM/工具 orTimeout 超时控制） |
| AUDIT-14-04 | P1 | ✅ | plan 271（专用 cached 守护线程池） |
| AUDIT-14-06 | P2 | ✅ | plan 273（DbSessionTakeoverLock 心跳续期） |

## 2026-06-19 adversarial review — ReAct loop message contract & lifecycle

来源：`ai-dev/audits/2026-06-19-2310-adversarial-review-nop-ai-agent/01-open-findings.md`

| 发现 ID | 严重程度 | 状态 | 落地 plan |
|---------|---------|------|-----------|
| AR-03 | P1 | ✅ | plan 277（re-enter break→continue，不丢弃同批 tool 结果） |
| AR-06 | P2 | ✅ | plan 277（reentryCounters per-iteration 重置） |
| AR-07 | P2 | ✅ | plan 277（SESSION_ESCALATED 独立事件） |
| AR-11 | P2 | ✅ | plan 277（guardrail block 不注入孤儿 tool 消息） |
| AR-12 | P2 | ✅ | plan 277（PRE 点测试修正） |
| AR-13 | P2 | ✅ | plan 277（re-enter 测试强断言） |
| AR-14 | P2 | ✅ | plan 277（truncated 状态 + post-loop gate + isTerminalStatus） |

## Rule

- 本文件只追踪发现→修复状态映射，不重复 plan 实现细节。
- 新增审计轮次：新增一个章节，指向对应 `ai-dev/audits/<date>-*` 目录。
- 所有发现修复后，对应 plan 的 closure audit 是 done 的依据。
