# 2026-09-28 jq halt 被 try/catch 误捕修复

## Problem

- jq 引擎（nop-jq）中 `try (halt) catch "caught"` 输出 `caught`，而 jq 1.7.1 官方语义（`/usr/bin/jq` 实测）为：立即终止程序、无任何输出、exit 0。
- `halt` 在 `?` 错误抑制、`?//` 备选解构、`|=` 路径求值恢复等所有语言级错误处理点都会被静默吞掉或改道。
- 官方测试套件 430/430 通过——套件不含任何 halt 用例，缺陷无暴露面。

## Diagnostic Method

- 起因是异常处理方案合规排查（用户问 JqRuntimeException 是否应继承 NopException）：清点 nop-jq 全部异常继承链时发现 `JqHaltException extends JqRuntimeException`，而引擎语言级捕获点全部按 `catch (JqRuntimeException)` 匹配。
- 先查 `visitTryCatch`（JqExecutor.java:250-253）：已对 `JqBreakException | JqStopException` 显式 rethrow，唯独没有 halt——排除"缺一个 rethrow"的表层修法（捕获点共 11 处，逐点补会漏）。
- 用 `/usr/bin/jq` 建立语义基准（关键一步）：`try (halt) catch "c"` → 无输出 exit 0（不可捕获）；`try (halt_error(3)) catch .` → 输出输入值（**可捕获**，捕获后就是普通错误值）；未捕获 `halt_error` → exit 5。
- 写探针测试（`ScratchHaltProbeTest`，确认后删除）复现引擎行为 `[caught]`，与基准对照定性为 confirmed live defect。

## Root Cause

- `halt` 与 `halt_error` 共用同一个异常类型（`JqHaltException extends JqRuntimeException`），而 jq 1.7.1 中二者语义相反：`halt` 是不可捕获的程序终止；`halt_error` 是可捕获的普通错误值（携带仅在未捕获终止时生效的退出码）。
- 官方套件无 halt 用例，类型层级错误无测试守护。

## Fix

- `JqHaltException` 改为直接继承 `RuntimeException`——脱离所有 `catch (JqRuntimeException)` 的匹配范围，11 个语言级捕获点（try/catch、`?`、`?//`、path-eval 恢复、递归下降过滤）一次性全部修正，未来新增捕获点也不会再误捕。
- 新增 `JqHaltErrorException extends JqRuntimeException`：`halt_error` 改抛此类型，携带 exitCode（仅未捕获终止时生效），可捕获性、catch 收到的值（= 输入）与系统 jq 一致。
- `JqBuiltins`：`halt` 改抛 `JqHaltException("halt", 0)`；`halt_error` 改抛 `JqHaltErrorException(input, exitCode)`。
- `JqToolExecutor`（AI 工具边界）增加 `catch (JqHaltException)` 显式分支，保持工具错误响应语义。
- 顺带修复 JqExecutor.java:1016 无消息的 `IllegalStateException`。

## Tests

- `nop-jq/src/test/java/io/nop/jq/TestJqHaltSemantics.java`（6 用例，语义逐项对照 /usr/bin/jq）：halt 不被 try/catch 捕获、不被 `?` 吞掉、数组构造内不可捕获、`halt_error(3)` 可捕获且 catch 收到输入值、未捕获 halt_error 携带 exitCode、`|=` 路径求值恢复不拦截 halt。

## Affected Files

- `nop-jq/src/main/java/io/nop/jq/runtime/JqHaltException.java`
- `nop-jq/src/main/java/io/nop/jq/runtime/JqHaltErrorException.java`（新增）
- `nop-jq/src/main/java/io/nop/jq/runtime/JqBuiltins.java`
- `nop-jq/src/main/java/io/nop/jq/runtime/JqExecutor.java`（ISE 消息）
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java`
- `nop-jq/src/test/java/io/nop/jq/TestJqHaltSemantics.java`（新增）

## Notes For Future Refactors

- 不变量：`JqHaltException` 必须永远不进入 `JqRuntimeException` 继承树；任何"引擎统一捕获 JqRuntimeException"的新代码天然不会拦截 halt，不要为它单独加 catch。
- 官方套件（jq-official.test）是 vendor 原文，不要为补 halt 用例而编辑它；halt 语义守护在本回归测试类。
- `halt_error` 的 exitCode 只在进程级终止时可观测（未来 CLI 入口消费），JVM API 层不模拟 exit code。
