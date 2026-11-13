# 深度审核汇总报告 — nop-spring + nop-quarkus 宿主并发·异步·资源专项审计（CRA WI 4.4）

## 基本信息

- **审核单元**: `nop-host`（11 有源模块 53 files/4,344 lines src/main 口径：nop-spring core-starter 5f/464+delta 4f/205+file 2f/174+proxy 5f/298+web-starter 13f/1,213 + nop-ai/nop-spring-mcp-server 3f/85 + nop-quarkus core-starter 6f/484+file 3f/244+web 10f/1,120+web-orm-starter 1f/11+web-starter 1f/46；5 pom-only 模块登记零审计面；`_gen/` 残留 0）
- **审核日期**: 2026-10-21
- **执行维度**: C1 共享可变状态、C4 线程池治理、C6 资源生命周期（roadmap 4.4 裁剪集）+ C10 测试保护力（C2/C3/C5/C7/C8/C9 不在维度集，overlay 线索 CL×8）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 2a2d546fc0（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2480-cra-wi4.4-nop-host.md`（两轮对抗性审查通过后执行；R1 agent_8ff962ce 2 Major[计数 13→11/形态预注失真]+4 Minor 全修——锚点底稿 StartupEvent 零命中/WebClassLoader 零定义/BeanPostProcessor 零命中订正；R2 agent_9a7695de 1 Major[pom-only 11→5/源码根 13→11 残留]+m1/m2 全修）
- **执行说明**: 全部批次串行派发（运营者要求）——C1+C4+C6 合并批单次派发 + 深挖/复核合并任务单次派发 + C10/裁定合并任务单次派发；**原始并发面极薄**（6 文件 26 行原语，零自建线程/池——预校准成立未凑数）；**owner doc 覆盖薄**（module-groups.md 组级覆盖，drift 登记兜底）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 6（P1×2/P2×4） | +0 | 6 | 1（C1-002 P1→P2——R1 执行载体归因有误：BaseContext 用 ContextTaskQueue 无池） | 0 |
| C4 | 2 | 3（P2×3） | +1（P2） | 4 | 0 | 0 |
| C6 | 2 | 6（P1×1/P2×4/P3×1） | +3（P1×1/P2×1/P3×1） | 9 | 0 | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 19 行矩阵全维持 + 缺保护 19（P1 本体强制 4 条） | 0 | 0 |
| **合计** | — | **15** | **+4 = 19** | **19** | 1（升格 1：C1-006 P2→P1——复核补全变体 B 空链固化机制双宿主坐实） | 0 |

R2 深挖三维度全部显式终止（+4 全 P2/P3 且收敛证据齐备——四点名目标行级负结果、@EventListener 阻塞面穷举、destroy 幂等核验无缺陷）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 19 条保留缺陷 → 合并后 **16 张工单 + watch 1 = 5 缺陷族（MA-01…05，覆盖 7 条 = 主 5 + 吸收 2）+ 11 独立立案**。逐条落位无遗漏。
- 工单判级：**P0×0 / P1×4 / P2×10 / P3×2**。
- 冲突裁定 2 处留 7.1（C6-008 族判级对齐、C4-001 家族三载体判级口径）+ 1 项联动设计议程（MA-01 门禁 × MA-02 悬挂面同批）。
- 跨单元对账：与 cra-index WI 1.1–4.3 台账（152 条 P1）**零同站点重复**（nop-host 首次并发审计）；跨单元关联 8 处单列（future 永不完成族第 5 例 C6-007——平台根因 ContextTaskQueue→WI 2.3 CL-018 + WI 2.1 C3-001 绝缘层、错误交付族传输层成员 C6-008、C4-001↔WI 4.3 MA-03 同族异站、WI 2.3 上下文传播族×2 等）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 4（2 族 + 2 独立） | MA-01 启动窗口门禁族（C1-006 主：双宿主空链固化/认证旁路——IocCoreInitializer registerInstance 覆盖窗口内 filter 全落 Spring/CDI 桥空链；成员 C4-003 启动窗口无就绪门禁）；MA-02 future 族宿主面（C6-007：ContextTaskQueue 吞异常 future 永不完成——Quarkus 认证请求永久悬挂，/px/ 畸形 JSON 用户可触发；future 族第 5 例）；C6-001 上传流校验失败路径永不关闭（每被拒上传泄漏 1 fd，外部可稳定触发）；C1-001 SpringWebProxy 凭据对撕裂（Reactor 线程写/请求线程读无 volatile） |
| P2 | 10（3 族 + 7 独立） | MA-03 错误交付族传输层成员（C6-008，判级留 7.1）、MA-04 生命周期绑定镜像对（C6-003 Spring onClose 无归属守卫+C6-002 Quarkus 只 start 不 stop）、MA-05 上下文传播族宿主面（C1-002 降档）+ C4-001/002、C1-004 等 |
| P3 | 2 | C6-006（write(int) 吞字节防御性）、C6-009（释放责任未声明零调用面） |

C10 另有缺保护登记 19 条（强制 P1 本体 4 条 C10-001/006/011/017；覆盖 19/19 闭合，全部映射随本体工单）：保护力矩阵 19 行**会红 0/假保护 1/部分 2/无测试 16**——P1×4 全部无保护；反向钉死 4 处（1 钉死型 assertSame+3 掩蔽型）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 19 条保留发现全部为**新发现**（cra-index WI 1.1–4.3 台账零同站点命中，nop-host 首次并发审计）；已知未修复 0、已修复 0。
- 现状分层：live 未修复新发现为主；C1-002 降档附 ContextTaskQueue 无池证据。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-006（MA-01 主，成员 C4-003） | 双宿主启动窗口空链固化：IocCoreInitializer registerInstance 覆盖 BeanContainer facade 窗口内 filter 全落 Spring/CDI 桥空链——认证旁路不可自愈 | nop-spring/nop-quarkus 宿主 |
| 2 | C6-007（MA-02） | ContextTaskQueue 吞异常 future 永不完成——Quarkus 认证请求永久悬挂（/px/ 畸形 JSON 用户可触发；future 族第 5 例，平台根因绝缘层移交） | nop-quarkus-web |
| 3 | C6-001（独立） | 上传流「先校验后消费」失败路径永不关闭——每被拒上传泄漏 1 个 fd，外部可稳定触发 | nop-spring-file/nop-quarkus-file |
| 4 | C1-001（独立） | SpringWebProxy 凭据对（authorization/cookie）Reactor 线程写/请求线程读无 volatile——陈旧 token 重发 + 撕裂凭据对 | nop-spring-proxy |

## 盲区自评

- C2/C3/C5/C7/C8/C9 为 roadmap 4.4 裁剪维度：仅 overlay 线索登记（CL×8：并入工单 3/独立线索 5/watch 1——CL-005 拆分/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- Spring/Quarkus 框架本体黑盒按使用面审计；宿主原始并发面极薄（6 文件 26 行）为实测事实——C1 产出密度已按预校准管理。
- MA-01 门禁 × MA-02 悬挂面联动设计议程移交 7.1；C6-009 触碰 nop-http-api 申报 plan-first Protected Area。
- R2 深挖三维度全终止（+4 全 P2/P3），无口径偏差。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面。
- 与 WI 1.1–4.3 台账（152 条 P1）零同站点重复；家族关联 8 处单列。
- security-audit roadmap：C1-006 认证旁路面建议知会安全线（登记不重复）。
