# 2026-11-21 nop-ib 单模块项目缺陷族（END_DOCUMENT / 0-module / 影子类）修复

## Problem

用一个最小单模块 Maven 项目（1 pom + 1 主类 + 1 JUnit 测试，`~/ibtest/p1-single`）实测 nop-ib，全链路四连崩：

1. `model refresh` 直接抛 `java.util.NoSuchElementException: END_DOCUMENT reached: no more elements on the stream.`（exit 1），**所有非聚合器项目都无法建立模型**。
2. 修掉崩溃后 refresh 报 `model refreshed: 0 modules` —— 模块事实为空。
3. `check --paths` 报 `cannot map changed path to a reactor module` + `stale: pom missing: /pom.xml`。
4. `test` 把测试源当主源编译进主输出目录 target/classes，报 `程序包 org.junit.jupiter.api 不存在`（主 classpath 无 JUnit）——影子类缺陷的单模块变体。

此前未暴露的原因：现有测试 fixture 全部使用多模块 `<projects>` 包裹形态的 effective-pom dump；主仓（nop-entropy）及所有实测项目都是多模块 reactor。

## Diagnostic Method

- 服务报错无堆栈且 `logPath` 指向不存在的文件（次要缺陷：JobLog 惰性创建，job 失败于任何输出前则无日志文件），于是改用 `ib` CLI 直跑拿到完整堆栈：`EffectivePomParser.parse(EffectivePomParser.java:52)`。
- 读 Maven help:effective-pom 的真实 dump（`.nop-ib/tmp/ib-effective.xml`）：单模块形态是**裸 `<project>` 文档根 + 无任何尾随内容**。StAXSource transform 消费唯一的 project 时驱动 reader 越过 END_DOCUMENT，JDK stax 实现此后 `next()` 抛 NoSuchElementException；多模块形态因 dump 有 `<projects>` 包裹根与尾随节点，transform 恰停在 END_ELEMENT 之后，循环正常退出。
- 0-module：`parseProject` 的 basedir 推导用 `contains("/target/")`——单模块 dump 路径被 strip 成 repo 相对的 target/classes（无前导 `/`），推导失败 → basedir=null → ModelRefresher 按「root aggregator 无源码事实」跳过。
- pom missing `/pom.xml`：`collectPomHashes` 用 `basedir + "/pom.xml"` 拼键，空 basedir 得到绝对路径形态的键 `/pom.xml`，staleness 校验 `repoRoot.resolve("/pom.xml")` 永远 miss。
- 影子类：`splitBySourceRoot` 判 `contains("/src/test/")`——repo 根模块的源路径无前导斜杠，测试源归入主源，以主 classpath 编进主输出目录 target/classes。注意修复缺陷 2 之前的错误路径形态 `/src/test/...` 反而"意外满足"了这个判定——同一族缺陷互为掩体。

## Root Cause

`EffectivePomParser`/`ModelRefresher`/`IbModel`/`IncrementalCompileExecutor` 各处隐含「模块 basedir 非空」与「dump 有 `<projects>` 包裹」两个假设；单模块项目（repo 根即模块）同时打破两个假设，四个症状同源。

## Fix

- `EffectivePomParser`：循环加 `hasNext()` + NoSuchElementException 双守卫；新增 `hasProjectsWrapper()` 区分两种 dump 形态；basedir 推导补 `target/...` → `""`（repo 根模块）。
- `ModelRefresher`：仅当 `hasProjectsWrapper()` 为真才跳过空 basedir 模块（root aggregator 只存在于多模块形态）；`collectPomHashes` 空 basedir 键归一为 `pom.xml`。
- `IbModel.moduleForPath`：空 basedir 作为全路径候选，最长前缀规则仍优先更深模块。
- `IncrementalCompileExecutor.splitBySourceRoot`：补 `startsWith("src/test/")` 分支。
- nop-tools/nop-ib/bin/package-ib.sh 的 ib-run 薄壳：取消/无 exit 的 job 不再打印空 `exit=` 并误退 0（非 done 状态兜底 exit 1，附带 `state=`）。

## Tests

- `TestEffectivePomParser.testSingleModuleDumpWithoutProjectsWrapper`（真实单模块 dump 形态逐字复刻：注释头 + 裸 project + 无尾随内容，断言 basedir=""）+ `wrappedFixtureFlagsProjectsWrapper`。
- `TestSourceRootPartition.testRepoRootModuleTestSourceWithoutLeadingSlash`（影子类回归钉）。
- 黑盒端到端复验：p1 从 `rm -rf .nop-ib target` 起步 refresh(1 module)→test fork（`demo.CalcTest` run 2 / failed 0）；p2 多模块项目全链路不受影响；nop-ib 全模块 203/0。

## Follow-ups / 已登记

- 嵌套独立项目（自身 pom 嵌在含 mvnw 的父仓内）会被 `discoverRepoRoot` 归一到父仓——判定为文档化契约（README「项目根识别契约」）而非代码缺陷；调用方须用 `ib-run --dir`。
- 并发会话同时重打包 `~/nop-ib/nop-ib.jar` 时，运行中的服务线程会 `NoClassDefFoundError`（jar 被并发替换）——环境缺陷族已有先例登记，重打包后必须重启服务。
