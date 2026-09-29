# 06 资源泄漏配对 v1 — acquire/release 路径敏感义务分析（roadmap item 7, Wave 3, M2b）

> Plan Status: active
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 3 item 7 + [gap-ledger](../../../nop-bytecode/docs/gap-ledger.md) G2 + plan 23 Option A（源码 lane Deferred 面的字节码承接）+ plan 05 对照形态先例
> Related: [05-null-flow-v1.md](05-null-flow-v1.md)（M2a 已达成；内核/通道复用）

## Purpose

实现方法内 acquire/release 路径敏感配对分析 v1（资源义务分析）：Closeable/连接/锁三类资源注册表，try-with-resources 与 finally-close 豁免，wrapper 所有权转移白名单；以已知命中集对照（SpotBugs 同语料 + 自建 fixture 命中集）完成证据闭环。完成后 M2b 达成（两大缺口类各有 v1 且对照在档）。

## Current Baseline

- 内核 v1（plan 03）与通道 v1（plan 04）在库：`DataflowSemantics` 回调契约天然支持第二类分析（ResourceSemantics 同构实现）；CLI 的 Finding/渲染/退出码契约可直接挂新 ruleId（`resources/unclosed-resource`）。
- plan 05 形态先例：正式口径 + 豁免面测试锁定 + 同语料对照记录（SpotBugs 基线空集场景的处理方式——对照记录 §一/§三 结构可复用）。
- 对照基线事实（审查实验订正）：`FindUnsatisfiedObligation` 在 SpotBugs 4.9.8 **active 排程**（plan 01 旧记「6 findings（SF×4/UPM×2/IC/EQ）」括号加总 8 系笔误，实为 6：SF×3类4条/UPM/EQ/IC 各一——对照复跑时订正）（无 disabled 属性；`spotbugs-exclude.xml:134` 注释即指 OBL）——nop-jq 空集的真因是**语料资源面为零**（grep 证实 0 处 .close、0 个自定义 Closeable），非开关问题。故对照主腿 = **fixture 语料 SpotBugs 只读对照**（五形态编译产物经本机 4.9.8 standalone 实跑，产出仅落 `_tmp/`，仓库接线零改动——HC1 相容），记录 OBL 对各形态是否命中；全语料腿保留但证据力如实声明。
- 仓库无直接 ASM 之外的依赖变化需求（义务分析纯内核实现）。
- roadmap item 7 = `todo`。

## Goals

- `analysis/resources`：`ResourceSemantics`（复用内核 Frame/solver——义务 token 作为帧槽位值传播）+ `ResourceRegistry`（**v1 交付两类**：Closeable/AutoCloseable 系（NEW 精确 owner 匹配）+ JDBC 工厂方法 acquire 小名单（`Connection.createStatement/prepareStatement`、`Statement.executeQuery/executeUpdate`、`DriverManager.getConnection` 返回类型——SpotBugs 政策数据库同型）；**Lock 显式移出 v1**（无 close()，lock/unlock 配对需独立规格，归 Follow-up））+ `OwnershipWhitelist`（包装器所有权转移白名单）+ `UnclosedResourceFinding`。
- 豁免面：try-with-resources（编译形态 = close 于全部退出路径，义务分析自然覆盖）、finally-close（同）、**所有权转移**（资源被 return 返回 / 存入字段 / 传入白名单包装器构造器 → 义务解除）。
- CLI 挂载：`resources/unclosed-resource` ruleId 并入发现流（同一渲染/退出码契约）。
- 对照：SpotBugs 同语料（预期空集如实记录）+ 自建 fixture 命中集（泄漏/TWR/finally/所有权转移/条件泄漏五形态）+ 分层抽检（同 plan 05 方法学）。
- M2b 达成。

## Non-Goals

- 跨方法义务传播（资源逃逸入参数/全局状态）——v1 仅方法内；逃逸面经所有权转移豁免保守处理，已知限制注明。
- nullness 分析口径变更（plan 05 交付物不动，除 CLI 挂载新 ruleId 的最小接线）。
- SpotBugs 接线改动；CI 接线（item 9）。
- FP 收敛迭代（首版保守语义的 FP 声明 + 对照基线即闭环，收敛归后续优化）。

## Scope

### In Scope

- `nop-bytecode/src/main/java/io/nop/bytecode/analysis/resources/**`
- `nop-bytecode/src/test/java/**`（义务分析测试）
- `nop-bytecode/src/main/java/io/nop/bytecode/cli/NopBytecodeMain.java`（最小接线：第二分析器挂载 + ruleId）
- `nop-bytecode/docs/resource-comparison.md`（对照记录）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`、gap-ledger G2、`ai-dev/logs/{执行当日}.md`

### Out Of Scope

- nullness 口径与既有测试语义变更；nop-lint / SpotBugs 接线；maven goal；CI

## Execution Plan

### Phase 1 — 义务分析 v1

Status: completed
Targets: `analysis/resources/**`、`cli/NopBytecodeMain.java`

- Item Types: `Fix`

- [x] draft review 通过后：roadmap item 7 `todo`→`planned` + `Last updated` 刷新
- [x] `ResourceRegistry`：资源类型判定（类层次解析 = 从语料可及的已知资源类型名单 + `Closeable`/`AutoCloseable` 名单，v1 名单制不做全量层次推导——保守面注明）；`acquire` 形态 = `NEW <资源类型>`（INVOKESPECIAL `<init>` 后 DUP 消费完成时的引用）
- [x] `ResourceSemantics implements DataflowSemantics`——**义务状态算法规格**（guide 规则 10 语义规格；审查实验定音）：
  - **帧值表示**：被跟踪引用的槽位值 = 不可变 token `(nullState ∈ {DEF, MAYBE}, openSites: 不可变集合<siteId>)`——siteId = acquire 指令索引；**必须放帧槽位值内**（solver 多次 copy 后调 transfer，side-table 会被覆盖破坏路径敏感）；参数初始 `(MAYBE, ∅)`，ACONST_NULL = `(NULL, ∅)`，cat2 占位 `topValue()`
  - **迁移**：DUP/ASTORE/ALOAD 按值拷贝语义天然迁移义务（Frame 现状即可）；**别名 close 与所有权转移 = 全帧扫描改写**（遍历 [0, sp) 读 slot(i)，把含目标 site 的值替换为新 token 经 setLocal 写回——close 解除 site；ARETURN/PUTFIELD/PUTSTATIC/白名单构造器实参 = 该 site 全帧解除 discharge）
  - **merge**：分量合并（nullState 按 MAYNULL 同型 join；openSites 求并——单调有界必终止）；**DEF 维度**：新鲜 DEF token（未与 null 混合）在 IFNULL/IFNONNULL 判空时按 edgeFrame 返回 false 丢弃 null 侧边（`$assertionsDisabled` drop 先例）——否则守卫式 finally `if (r != null) r.close()` 与 Java 9 `try (r)` 的 null-skip 边并集必然系统性 FP（审查 B1 实证）
  - **发现记录点**：RETURN 族 + 显式 ATHROW 的 transfer 扫描残留 openSites → Consumer；handler 进入帧 = 清栈 + 压异常引用（资源 token 栈上部分消失——构造器抛异常义务正确解除；locals 中保留=异常路径泄漏可见）；**隐式异常传播退出（无 handler 的调用抛出）CFG 无退出指令——已知 FN 面声明**
  - **close 判定**：接收者含被跟踪 site 即 discharge（site 只在注册表类型 acquire 点创建，无需接收者类型层次解析）；close 形态含 INVOKEVIRTUAL 与 INVOKEINTERFACE
- [x] `OwnershipWhitelist`：`java.io.BufferedReader/BufferedWriter/PrintWriter/ObjectInputStream/ObjectOutputStream/Scanner` 等包装器构造器名单（持有底层流所有权），javadoc 注明名单制保守面与扩充方式；普通方法实参逃逸**不在豁免清单**——如实记 FP 方向
- [x] **finding ref 编码 acquire 点**（`Type@insnN`）——CLI 去重键按 (class,method,insn,ref) 不丢同 return 多泄漏
- [x] `OwnershipWhitelist`：`java.io.BufferedReader/BufferedWriter/PrintWriter/ObjectInputStream/ObjectOutputStream/Scanner` 等包装器构造器名单（持有底层流所有权），javadoc 注明名单制保守面与扩充方式
- [x] CLI 挂载：`NopBytecodeMain` 管线追加第二分析器（同字节读回路径），finding ruleId `resources/unclosed-resource`，severity warning；既有 nullflow 行为与测试零回归
- [x] **Phase 1 同 Phase 单测**（guide 规则 25）：Registry 判定/Whitelist/义务 token merge 代数/别名 close 全帧扫描解除/所有权转移 discharge 各一组单测；**CLI 接线端到端**：泄漏 fixture 经 `run()` 输出 `resources/unclosed-resource` finding（规则 23——防挂载空转）

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode -am` 全绿（含 Phase 2 测试；Phase 1 不先于 Phase 2 标 completed）
- [x] 既有 nullflow/采集/oracle 测试零回归（测试计数只增）
- [x] CLI 接线验证：resources finding 经真实管线输出（Phase 1 端到端测试断言）
- [x] owner-doc 裁定：00-overview 分析器层/通道层行按交付面更新（两类注册表 + ruleId 扩展）；RuleIds javadoc 命名空间句扩展
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 2 — fixture 命中集与对照

Status: completed
Targets: `src/test/java/**`、`docs/resource-comparison.md`、`_tmp/nop-bytecode-resource-compare/`

- Item Types: `Proof`

- [x] **fixture 命中集测试**（八形态，字节码形态依据 = 审查 `_tmp/plan06-review/` 反汇编）：未关闭流（return 路径泄漏）= 命中；**声明式 TWR**（close 无守卫）= 豁免；**Java 9 existing-var TWR `try (r)`**（带 ifnull 守卫）= 豁免（DEF 丢边验证）；**守卫式 finally-close `if (r != null) r.close()`** = 豁免（同上）；**别名 close** `b = a; b.close()` = 双引用均豁免；**字段转移** `this.field = res; return` = 豁免；所有权转移（return 资源 / 白名单包装器包裹）= 豁免；条件泄漏（if 分支 return 前未 close）= 命中（路径敏感）
- [x] **SpotBugs fixture 语料对照腿（主腿）**：五/八形态 fixture 编译产物用本机 SpotBugs 4.9.8 standalone 只读实跑（产出落 `_tmp/nop-bytecode-resource-compare/`），记录 OBL 对 leak/TWR/finally/别名/条件泄漏各形态是否命中——这是有信息量的对照
- [x] **SpotBugs 全语料腿**：nop-jq 重跑（复用 plan 05 命令形态）——预期空集，归因如实记录（语料资源面为零，非 OBL 关闭——审查订正）
- [x] **plan 23 案例集对照腿**：closeable-not-closed 保守面（源码 lane Option B）的典型形态过本通道——路径敏感面是否互补（源码 lane 报声明型，本通道报路径敏感型），逐形态记归属
- [x] 对照记录 `docs/resource-comparison.md`（口径/命令/数字/裁定/FP 声明/已知限制清单：**隐式异常传播退出不报**、逃逸保守面=普通方法实参 FP 方向、名单制类型面、链式 acquire/store-over/栈上-only-handler-入口/非白名单包装器实参消耗 token 等 FN 面——审查 m3 清单全收录）

Exit Criteria:

- [x] 五形态 fixture 测试全绿且纳入默认 suite
- [x] 对照记录在档：命令可复现 + 空集/命中如实 + 已知限制声明
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 3 — 收口（M2b）

Status: completed
Targets: roadmap、gap-ledger、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap item 7 `planned`→`done`（指针 + audit id；done 标注 reconciliation：三类注册表 v1 交付两类，锁归 Follow-up）；**M2b 行标注达成**
- [x] gap-ledger G2 立项翻 `claimed`（本 plan 指针）→ 收口 `closed`（对照指针 + 控制面细化回填：豁免面 = TWR/finally/DEF 丢边/所有权转移/白名单；FP 方向 = 普通方法实参逃逸；FN 面 = 隐式异常传播等审查清单；交付面 = 两类注册表）
- [x] 文本一致性五处核对
- [x] 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / scan-hollow --module nop-bytecode 0
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add）

Exit Criteria:

- [x] roadmap item 7 done + M2b 标注；G2 closed
- [x] 三门禁 0；`./mvnw test -pl nop-bytecode -am` 收口复跑全绿
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 义务分析 v1 落地：三类注册表 + 五形态 fixture 锁定 + CLI 挂载（`resources/` ruleId）且既有测试零回归
- [x] 对照记录在档：SpotBugs 空集如实 + plan 23 案例集归属 + 已知限制声明
- [x] gap-ledger G2 closed（控制面细化回填）；roadmap item 7 done 带指针与 audit id；M2b 标注
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0 + `./mvnw test -pl nop-bytecode -am` 全绿

## Deferred But Adjudicated

### 跨方法义务传播

- Classification: `watch-only residual`
- Why Not Blocking Closure: v1 范围 = 方法内 + 所有权转移豁免（plan 交付声明）；跨方法需调用图/字段摘要——item 10 跨过程 spike 的直接下游
- Successor Required: `yes`
- Successor Path: Wave 5 item 10 结论后视需要另立

## Non-Blocking Follow-ups

- 资源类型全量层次推导（替代名单制保守面）——真实误报数据出现后按需立项
- Lock 系平台类型扩展（`ReentrantLock` 显式 lock/unlock 配对）——首版聚焦 Closeable/连接/锁名单中的 JDK 类型，平台扩展随消费需求

## Closure

Status Note: item 7 收口：资源义务分析 v1（两类注册表 + 全帧扫描解除 + DEF 丢边守卫豁免 + 所有权转移豁免）+ CLI 挂载（resources/ ruleId）+ 八形态 fixture 锁定 + 对照记录（SpotBugs fixture 腿零 diff + 全语料空集 + plan 23 归属）。G2 closed。M2b 达成（两大缺口类各有 v1 且对照在档）。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_d32d9340-57e2-4677-9b59-1a23772f3fc3（独立 fresh-session 子代理，实跑测试/CLI/SpotBugs/探针）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_d32d9340
- Evidence:
  - Draft review: 独立审查 agent_6291d37b（含 javac 17 TWR/finally/别名反汇编、SpotBugs findbugs.xml OBL 注册状态实验——2 Blocker + 6 Major 修复后按其建议流程进入执行）
  - **首轮 closure audit REJECT**（auditor 三重实证：探针+trace+SpotBugs）：Blocker 1 = ARETURN 分支缺残留义务扫描（同泄漏经 return-对象退出不报——未声明的系统性 FN 面）；Major 1 = 白名单所有权转移未接线（isOwnershipWrapper 零调用死代码，两步白名单形态 FP）；Major 2 = JDBC 工厂 acquire 零测试覆盖；文本一致性 5 处（00-overview 未更新/roadmap 缺 reconciliation/既有 25→26 笔误等）
  - 修复：ARETURN 分支补残留扫描；创建 OwnershipWhitelist 类并接线（INVOKESPECIAL <init> 实参 sites pop 前捕获 + 全帧 discharge）；回归 fixture 四形态（leak-then-ARETURN / 两步白名单[含测试前提修正：br 自身是 NEW 注册资源须 close] / JDBC 泄漏 / JDBC 转移）；00-overview 两类措辞 + roadmap reconciliation + 计数订正；ObligationToken.merge 死代码清理
  - 修复后 28/28 全绿；对照记录零 diff 语料限定注记 + 扩充形态说明在档
  - 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / scan-hollow --module nop-bytecode 0（回填后复跑）

Follow-up:

- 跨方法义务传播——Wave 5 item 10 下游
- Lock 配对规格、资源类型全量层次推导、FP 收敛（普通方法实参逃逸）——按需立项

