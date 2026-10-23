# 深度审核汇总报告 — nop-peripheral 外围支撑抽查并发审计（CRA WI 6.5）

## 基本信息

- **审核单元**: `nop-peripheral`（333f/26,887 lines——nop-demo 94f/3,760 + nop-frontend-support 84f/6,751[8 子] + nop-dev-tools 155f/16,376 + nop-entropy-e2e 0f main[零 Java 审计面显式登记]）
- **审核日期**: 2026-10-23（初审/深挖/复核/C10/合并裁定/汇总收口同日完成）
- **执行维度**: C1 共享可变状态、C6 资源生命周期（全单元各单批·抽查模式，roadmap 6.5 裁定）+ C10；C2~C9 overlay 随批 CL 登记
- **目标范围**: `src/main/java` 全部手写代码（排除 `_gen/` 与 `_` 前缀生成物）；基线 commit a20ca25130；nop-benchmark 不审（误报校准）
- **方法论**: 盲审初审（C1/C6 串行 2 批）→ 深挖（R2 合并批 1）→ 独立复核（C1+C6 合并批 1）→ C10 → C10 独立复核 → 合并裁定
- **Plan**: `ai-dev/plans/2492-cra-wi6.5-nop-peripheral.md`（R1 agent_ddc31a98 3 Major[frontend 8 子+JavaScriptWorker 真实面/demo 14 子+spring 系/批次前置预拆判定]+5 Minor 全修）
- **Protected Area**: 零 api 叶（nop-api-debugger 名义近失显式裁定：调试工具模块非契约叶）
- **执行说明**: 全部子代理严格串行派发（初审 2 + 深挖 1 + 复核 1 + C10 1 + C10 复核 1 + 裁定 1 = 7 派）
- **本单元地位**: **初审审计阶段（WI 1.1–6.5）收官单元**

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 1（R2） | 7 | +1 | 8 | 0 | 0 |
| C6 | 1（R2） | 3 | +1 | 4 | 0 | 0 |
| C10 | — | — | — | 缺保护 6 + watch-only 5 + CL-301 | 0 | 0 |
| **合计** | — | **10** | **+2 = 12 实体** | **12 全保留 → 11 工单** | 0 | 0 |

## 合并裁定

- 12 实体 → 复核全保留（0 驳回 0 降级）→ 同站点归并 1（C6-003→C1-004 别名，ProjectFileHelper 静态缓存）→ **11 工单 = P1×2 / P2×4 / P3×5**（维持 11/跨维度调整 1/移交 0）。
- **P1×2 全走 7.1 台账**：W-01 C1-002 JavaScriptWorker 生命周期族（关闭窗口任务搁浅 future 永不完成 + 死亡无监督路由黑洞 + 队列无界积压，吸 CL-001/002、伴生 CL-201；future 族第 6 例）；W-02 C6-001 IDEA 插件调试会话 socket 通道从不关闭（proxy_destroy 零调用——每会话泄漏 1 fd + Disconnect 钉死 1 recv 线程；**验证通道=Gradle/harness 非 ./mvnw**[CL-301]）。
- 别名归并：C6-003→C1-004（W-03）；同站点吸并：CL-003→C6-002（W-06）；族工单：W-01 吸收 2 CL + 1 伴生。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 2 | 见上 |
| P2 | 4 | ProjectFileHelper 静态缓存 Project 泄漏（吸 C6-003）、XLangDebugger 挂起交接非原子（幽灵挂起/吞挂起）、NopProjectService 项目级生命周期驱动全局注册表（伴生 CL-204）、suspendedThreads 一次性清理（吸 CL-003） |
| P3 | 5 | muted 非 volatile、tempBreakpoint 非原子、demo RESOURCE_STORE（降口径）、sendStack 裸读 server、demo 临时文件生命周期（降口径） |

C10 缺保护登记 6 条单列（覆盖 P1×2+P2×4）：矩阵 **会红 0/假保护 0/无测试 6**——会红=0 第 13 例；C10-003「假保护」候选经复核逐交错变异重放证伪修正为「无测试（零可观测）」；watch-only 5 条对应 P3/demo 工单（修复同 diff 顺带补测）。

## P0 记录

本单元 **P0 = 0**（显式空执行；两轮复核无 P0 候选 + 裁定终审确认无升 P0）。全链无 P0 判级。

## 现状标注摘要

- 12 实体全部**新发现**（2026-09 G 系列 13 文件对四模块零提及 grep 证实；cra-index 对四模块 0 提及）。
- 唯一非首报条目：CL-203 cross-ref **WI 2.1 C4-003**（已知未修复，F3 成员明列 JavaScriptService.java:40，本体归 2.1，本单元 cross-ref 防双计）。
- 缺陷密度热点：**双进程调试协作面（nop-xlang-debugger 4 + nop-idea-plugin 4）与 JS 工作线程面（nop-js）**；nop-api-debugger（纯 DTO）与 nop-maven-shaded-plugin（单线程 Mojo）两子模块面闭合零缺陷。
- owner doc drift：四模块无 owner doc（roadmap owner 列「—」）——治理轨单元级整体登记。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C1-002 | JavaScriptWorker 生命周期族：关闭窗口 execute 检查-入队非原子任务搁浅 future 永不完成（DynamicJsLoader.syncGet 无超时永久阻塞）+ worker 死亡无监督路由黑洞 + taskQueue 无界积压（吸 CL-001/002，future 族第 6 例） | 语义侧/路由侧/容量侧三层 |
| 2 | W-02/C6-001 | IDEA 插件调试会话 socket 通道从不关闭（proxy_destroy 零调用，工厂不登记已建 client）——每会话泄漏 1 fd，Disconnect 钉死 1 recv 线程（修复参照 TestRemoteDebugger.tearDown 配对；验证通道 Gradle/harness） | 资源侧（通道级配对缺失） |

## 盲区自评

- 覆盖核验：五类载体全单元盘点 + 基线命中 12 文件全部精读 + dev-tools 主战场逐文件核对；C6 配对 24 项；C10 测试面 40 文件盘点（勘误初审草稿 32）。
- 会红 0/12 第 13 例；全单元唯一真并发测试（TestPageProviderPermissionsTransform）保护历史修复回归面——「只向后看」形态与 5.5 同型。
- **结构性发现**：idea-plugin Gradle 独立构建不参与 `./mvnw test` 门禁（CL-301，campaign 首报）——4 个缺陷站点游离于 Maven 验证约束外，修复批次验证通道必须显式声明；demo 降口径甄别自纠（R2 grep 漏检自我修正，CL-205）登记方法论教训。

## 与其他 roadmap 的重叠

- 零同站点重复立项（裁定人独立 grep cra-index 12 站点标识零命中，与两轮复核三方一致）。
- 家族关联 6 组单列：future-永不完成族第 6 例（4.3=第4/4.4=第5）、socket/fd 泄漏族新成员（4.3 P0 已修/4.4/3.2）、IDE 插件项目泄漏族首例、F4 只进不出清理族、WI 2.1 C4-003 使用方↔设施两面切分、停机拒绝语义族（2.2 C1-009/1.7 C6-001）。
- security-audit / full-code-review roadmap：无重叠面。
