# 00 Bug Fix Note Writing Guide

> Status: active workflow guide
> Last Reviewed: 2026-04-14
> Source: adapted from `nop-chaos-flux/docs/bugs/00-bug-fix-note-writing-guide.md`

## Purpose

`ai-dev/bugs/` 记录重要的 bug 修复过程，使其在代码大幅变更后仍然有意义。

目标不是重复 diff，而是保留：

- 出了什么问题
- 怎么定位的（尤其是定位困难的情况）
- 为什么会出问题
- 怎么修的
- 测试如何保护这个修复

## When To Write A Bug Fix Note

以下条件满足至少一条时写：

- bug 的根因不明显
- bug 跨模块
- bug 表面是一个问题，实际由另一层引起
- 修复新增或修改了回归测试
- 未来重构容易重新引入同样的问题

简单 typo 或一行小修不需要写。

## File Naming Rule

使用 `YYYY-MM-DD-` 日期前缀加简短描述：

- `2026-04-02-stream-operator-npe-fix.md`
- `2026-04-05-cron-expression-timezone-fix.md`
- `2026-04-10-dialect-exists-sql-compat-fix.md`

名称应包含用户可见症状或修复主题，简短可搜索。

## Required Sections

### 1. Title

使用日期和清晰名称。

```md
# 2026-04-02 Stream Operator NPE Fix
```

### 2. Problem

2-5 行描述观察到的症状：

- 用户或开发者看到了什么
- 在哪里发生
- 最小可复现行为

### 3. Diagnostic Method

**必填。** 描述定位过程，不只是最终根因：

- 诊断难点
- 先查了什么，为什么
- 被排除的假设
- 确认根因的直接证据

诊断简单时也至少写 2-4 条。

### 4. Root Cause

1-3 条，解释真正原因：

- 涉及的实际包或子系统
- 多个原因时分开列出

### 5. Fix

从设计意图描述解决方案：

- 改了什么
- 在哪里改的
- 为什么这样改能解决根因

### 6. Tests

列出新增或更新的回归测试：

- 测试文件路径
- 测试保护了什么

### 7. Affected Files

只列重要的代码和测试文件，不要贴大段 diff。

### 8. Notes For Future Refactors

1-3 条，说明未来变更应注意不要破坏什么。

## Recommended Template

```md
# YYYY-MM-DD Short Bug Fix Title

## Problem

- what broke
- where it broke
- minimal visible symptom

## Diagnostic Method

- diagnosis difficulty (why this was hard)
- investigation path (what was checked first)
- rejected hypotheses
- decisive evidence that confirmed the issue

## Root Cause

- actual cause 1
- actual cause 2

## Fix

- main change 1
- main change 2

## Tests

- `path/to/test-file` - what it verifies

## Affected Files

- `path/to/file`

## Notes For Future Refactors

- risk or invariant 1
- risk or invariant 2
```

## Writing Style

- 段落简短，优先用 bullet points
- 陈述事实，避免叙事
- 避免大段代码块
- 避免重复 architecture 文档中已有的内容
- 让几个月后读的人能理解问题，不需要重建整个事件

## Scope Boundary

- `ai-dev/bugs/` — bug 历史和修复记录
- `ai-dev/design/` — 当前设计 truth
- `ai-dev/discussions/` — 方案探索和决策过程
- `ai-dev/plans/` — 执行计划
