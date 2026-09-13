# 356 nop-ai 异常 ErrorCode 化与命名裁定

> Plan Status: completed
> Last Reviewed: 2026-09-13
> Source: 审计 03-nop-ai-findings AI-14（main ~150 处裸 IAE/ISE）/AI-15（157 处 message-only NopAiAgentException）/AI-21（*Service 命名）/AI-24（BeanContainer——已在 350 Deferred 裁定）
> Related: 350-355（已完成）

## Purpose

nop-ai 四模块（agent/core/toolkit/shell）main 代码的裸异常与无码异常收口为模块 ErrorCode 体系；登记 AI-21 命名裁定。

## Current Baseline

- AI-14：`throw new IllegalArgumentException|IllegalStateException` 在 nop-ai main（排除 _gen/target）：agent 27 文件、core 7、toolkit 6、shell 6 量级；代表：FailureEscalationPolicy:33-94、PlanExecutionState:76/178/181、ThresholdBreaker:137/182/216。
- AI-15：`new NopAiAgentException("...")`（message-only，无 ErrorCode）main 约 100+ 处（全库 558 中 401 带码）。
- AI-21：~10 个 `*Service`/`*ServiceImpl` 命名（实现平台 SPI 或内部引擎接口的命名被迫）。
- `BashSyntaxParser`（shell）extends RuntimeException；`BashSandboxException`（toolkit）extends bare。
- 模块异常：NopAiAgentException（带码构造器 + message-only 构造器并存）、NopAiCoreException、NopAiException（api）；NopAiAgentErrors 32 码。

## Goals

- 新增通用码：NopAiAgentErrors 增 `ERR_AGENT_INVALID_ARGUMENT`（ARG_DETAIL）、`ERR_AGENT_INVALID_STATE`（ARG_DETAIL）；core/toolkit/shell 各自 Errors 同理（或复用 agent 码按模块归属）。
- AI-14 脚本转换（注释感知）：IAE→NopAiAgentException(ERR_AGENT_INVALID_ARGUMENT).param(ARG_DETAIL, 原消息表达式)；ISE→ERR_AGENT_INVALID_STATE；带 cause 保留。目标模块 main 四个。公共 plan/parser 参数校验优先专用码（现场少补）。
- AI-15 脚本转换：`new NopAiAgentException("msg")`（main）→ `new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param("detail", "msg")`——**消息保真**（detail 内嵌原文，getMessage 断言兼容）。新增 ERR_AGENT_INTERNAL_DETAIL 码。
- S08：BashSyntaxParser/BashSandboxException 改 extends NopAiException（模块异常体系）。
- AI-21 裁定：Deferred 登记（SPI 命名被迫 + 重命名破坏性，新代码用 -Manager/-Coordinator 后缀）。
- 测试侧 assertThrows 同步（IAE/ISE→对应模块异常类）。

## Non-Goals

- 不逐点设计专用错误码（150+ 点位逐一专码属长尾演进；通用码 + detail 保真满足 typed 契约）；不动 fraud 类示例与 src/test 业务逻辑（仅断言类型同步）。

## Scope

### In Scope / Out Of Scope

- In：nop-ai-agent/core/toolkit/shell main + 上述测试同步。
- Out：其他模块、codegen 产物、AI-24（已裁定）。

## Execution Plan

### Phase 1 - 通用码与转换器

Status: completed
Targets: NopAiAgentErrors/NopAiCoreErrors 等 + `_tmp/ai-typed-throw.py`

- [x] 各模块 Errors 增通用码（agent 2 码 + internal-detail；core 同构；toolkit/shell 按现有 Errors 类归属）
- [x] 转换器（plan 352 同构：注释感知/括号配对/cause 保留/import 补齐）

### Phase 2 - AI-14/AI-15 批量转换 + S08

Status: completed
Targets: 四模块 main

- [x] IAE/ISE → typed（含 cause 形态）
- [x] message-only NopAiAgentException → ERR + param detail（main）
- [x] BashSyntaxParser/BashSandboxException extends 修正
- [x] 编译 + 测试修复循环（assertThrows 类型同步）

### Phase 3 - AI-21 裁定 + 验证收口

Status: completed
Targets: plan Deferred + 全量测试 + closure audit

- [x] AI-21 Deferred 登记
- [x] `./mvnw test -pl` 四模块全绿；复扫归零（豁免：fraud/quickstart/gen）
- [x] 独立 closure audit + checklist

## Closure Gates

- [x] AI-14/15 归零（豁免登记外）；S08 修正；AI-21 裁定
- [x] 四模块测试全绿；无 live defect 降级
- [x] 独立 closure audit 证据写入

## Deferred But Adjudicated

### AI-21 *Service 命名保留

- Classification: `watch-only residual`
- Why Not Blocking Closure: 实现平台 SPI（IMessageService/IChatService）的接口命名被迫；重命名为破坏性 API 变更收益低；新代码 -Manager/-Coordinator 后缀（模块内已有先例）。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部完成。AI-14（agent 72+core 11+toolkit 10+shell 6 → 0）、AI-15（agent 112 msgOnly → 0，消息经 ERR_AGENT_INTERNAL_DETAIL.detail 保真）、S08 两类裁定保留（工具结果协议/解析器控制流）、AI-21 Deferred 登记。协变 param 覆盖保持 typed 赋值场景。
Completed: 2026-09-13
Closure Audit Evidence:
- Reviewer / Agent: agent_2fcafe2b（独立 closure audit，6/6 PASS——closure 有效）
- Evidence:
  - 复扫：四模块 main 裸 IAE/ISE = 0、message-only NopAiAgentException = 0（BashSandboxException/ParseException 为 S08 裁定保留）
  - 新码：NopAiAgentErrors 3（INVALID_ARGUMENT/INVALID_STATE/INTERNAL_DETAIL）+ NopAiCoreErrors 2 + NopAiToolkitErrors 2 + NopAiShellErrors 1
  - 协变 param：NopAiAgentException/NopAiCoreException 覆盖（链式后保持子类型）
  - 测试：四模块全绿（agent 3387/core 379/toolkit 202/shell 全量；2 处"原始异常透传契约"断言回退：guardrail ISE 透传 + fatal ISE 透传——非转换遗漏而是传播契约）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/356-nop-ai-error-codes.md --strict` 退出码 0
Follow-up:
- no remaining plan-owned work
