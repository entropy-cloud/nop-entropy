# 2026-11-06 nop-report-web precompile NoClassDefFoundError（test scope 直接声明遮蔽递归依赖）

> Status: fixed
> Date: 2026-11-06

## Problem

- 全 reactor `./mvnw test`：`nop-report-web` 在 generate-sources 的 precompile（exec:java `CodeGenTask`）阶段失败：`NoClassDefFoundError: io/nop/excel/renderer/IExcelWorkbookGenerator`。
- `-fae` 下 nop-report-web 的下游（nop-report-demo/pdf/docx/app 等）全部 SKIPPED，其单测在全量门里不执行。
- install 上游后单模块 `./mvnw -pl nop-report/nop-report-web generate-sources -e` 必现，不是 `-T` 并行竞态。

## Diagnostic Method

- 难点：nop-excel 自身 BUILD SUCCESS，`IExcelWorkbookGenerator.class` 在 target/classes 里；report-web → report-service → report-core → nop-ooxml-xlsx → nop-excel 的递归链在 pom 上看起来完整。
- 排除竞态：上游 install 后单模块复现，确定性失败。
- 栈：`CoreInitialization → RegisterModelDiscovery.buildConfig`（`xlsx-loader` 注册）`→ DslModelHelper.newExcelModelLoader → XlsxDslModelLoaderFactory`（nop-report-core）`→` 解析 nop-excel 的类失败。
- 被排除的假设：「nop-report-core 在 2023-06-23（`3a353c78a4`）删了 nop-excel 直接依赖」。只补回该依赖后 nop-excel 变为 compile，但错误前移为 `io.nop.ooxml.xlsx.imp.XlsxObjectLoader` 缺失——xlsx 本身仍在 test scope。
- 决定性证据：用只含 pom 的稀疏 worktree 对 HEAD 状态跑 `maven-dependency-plugin:3.8.1:tree -Dverbose`：

  ```
  nop-report-web
  +- nop-report-service:compile
  |  \- nop-report-core:compile
  |     \- (nop-ooxml-xlsx:compile - omitted for duplicate)
  \- nop-ooxml-xlsx:test (scope not updated to compile)
     \- nop-excel:test
  ```

  对照：只删 report-web 的直接声明 → xlsx、nop-excel 均为 compile（纯递归单链）；只给 report-core 补 nop-excel → nop-excel 为 compile 但 xlsx 仍为 test。

## Root Cause

- `nop-report/nop-report-web/pom.xml` 以 `<scope>test</scope>` 直接声明了 `nop-ooxml-xlsx`。Maven 中直接声明的 scope 优先且不会被递归路径拓宽（verbose 树中的「scope not updated to compile」），经 report-core 递归带来的 compile 版 xlsx 被当作重复项剔除，其子树随之丢弃；nop-excel 只剩经 test 版 xlsx 的路径，也成了 test scope。
- report-web 主代码不用 xlsx，但运行期初始化经 report-core 的 excel 模型装载注册必然加载 xlsx / excel 类，precompile 的 `classpathScope=compile` 看不到它们。
- nop-report-core 不直接声明 nop-excel 不是原因：递归链本身完整，被截断的位置在 report-web。

## Fix

- 删除 `nop-report/nop-report-web/pom.xml` 中多余的 test scope `nop-ooxml-xlsx` 直接声明，xlsx / nop-excel 完全由 report-service → report-core 递归提供（与 nop-report-service / dao / meta / ext 的写法一致）。
- nop-report-core 的 pom 保持原样。

## Tests

- 未新增测试；保护面是全 reactor 门本身：nop-report-web 的 precompile + `NopReportWebPagesTest`，以及恢复执行的下游模块单测。
- 下游恢复执行后暴露了被掩盖的同期回归，同批修复（见 `ai-dev/logs/2026/11-06.md`）。

## Affected Files

- `nop-report/nop-report-web/pom.xml`

## Notes For Future Refactors

- 某依赖已经由主依赖链以 compile 递归引入时，不要再以 test / provided scope 直接声明它——直接声明的 scope 胜出，会把整条递归链降级。
- 依赖可见性问题先用 `dependency:tree -Dverbose` 看「omitted for duplicate」「scope not updated」，不要只读 pom 推断。
- 全量 `-fae` 结果要看 SKIPPED 模块清单：上游一个模块失败会让下游回归在全量门里不可见。
