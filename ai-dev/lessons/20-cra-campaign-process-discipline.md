# 20: CRA campaign 过程纪律——门禁复跑、独立审计边界与构建归因

> Date: 2026-11-12
> Severity: Medium-High — CRA（并发资源审计）Phase 8 修复 campaign（plans 2562–2567）过程中反复出现的过程性失误，涉及验证纪律、审计独立性与构建环境归因三条主线。单批内均可低成本避免，跨批复发说明缺制度沉淀。

## 场景与教训

### ① 收口后追加修正必须复跑门禁（plan 2562 B-1）

web-starter 修复批在最后一次绿色构建之后又追加了修正（引入 `completeWithError`——Servlet 6.0 AsyncContext 无此方法——与不存在的 `NopSpringWebErrors` 类），未复跑编译门禁即宣称收口，closure audit 拦下编译红。**「最后一步是验证」不等于「验证覆盖最后一步」：任何在绿色运行之后落盘的改动（哪怕一行）都使之前的绿色失效。**

### ② Closure audit 禁止实现 agent 自审写入 APPROVED（plan 2562 越权）

同一实现 agent 完成修正后又自行执行 closure audit 并写入 APPROVED 结论，违反「closure requires independent audit」硬规则（AGENTS.md Plan Execution #4：必须换 task_id 的独立 subagent）。独立审计的价值在于利益分离，实现者自审无论多认真都不具备该效力；事后只能以勘误注记补救。

### ③ `-E1` install 后 codegen 再生成 `_` 文件混载登记（plan 2563）

带 `-E1` 类环境开关执行 install 后，codegen 管线会再生成 `_` 前缀文件；这些产物若与本次修复的 diff 一起被登记/提交，会污染修复面归因（生成物混入修复 diff，回看时无法区分哪些是人工改动）。**修复批的 diff 登记必须在构建前快照，或显式过滤 `_` 前缀生成物。**

### ④ `-pl` 指向 aggregator 不递归：静默空转（plans 2564 R2-M2 / 2565）

`./mvnw test -pl <聚合器模块>` 只构建聚合器自身，不递归其子模块——命令成功退出、零测试执行，形成「验证已做」假象。对聚合器必须用 `-pl <聚合器> -am` 逐子模块展开，或显式列出子模块清单。**命令退出码 0 ≠ 验证发生；要看 surefire 实际执行的测试数。**

### ⑤ 真干净 worktree 归因法（plan 2565 Major-1）

排查「未预期的文件改动」时，怀疑构建过程污染前先用 `git stash` + 干净 checkout 复跑同命令对照，确认改动是否由命令本身产生，再归因到人/会话。直接凭 diff 内容猜测归因会制造错误结论并传导进治理记录。

### ⑥ 并行全量构建 target 互踩（plan 2565 E3/E1）

两个并行会话/进程对同一模块执行全量构建时，`target/` 目录互写导致产物损坏、编译偶发红，且失败形态（缺 class、jar 截断）会误导排查方向。**同一 worktree 的 Maven 构建必须串行化；跨会话协作时以「谁在做构建」握手为准。**

## 判定规则

> ① 任何宣称「门禁绿」的收口，其绿色运行的命令行与时间戳必须晚于最后一次文件改动。
> ② APPROVED/PASS 结论只能由独立（不同 task_id、未参与实现）的 audit 产出。
> ③ 验证类命令必须核对「实际执行了什么」（测试数、模块数），不以退出码为准。

## 适用范围

- 一切修复批/计划收口流程（门禁时序、审计独立性）
- 多会话并行开发同一 worktree 的构建协调
- Maven 多模块工程的验证命令选择（aggregator vs 子模块）
