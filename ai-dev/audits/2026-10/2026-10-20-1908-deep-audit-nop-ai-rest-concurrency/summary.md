# 深度审核汇总报告 — nop-ai 其余模块并发·异步·资源专项审计（CRA WI 3.4）

## 基本信息

- **审核单元**: `nop-ai-rest`（8 有源模块：nop-ai-api 29f/3,917 + nop-ai-coder 21f/2,050 + nop-ai-dsl-orm 4f/226 + nop-ai-rag 20f/1,810 + nop-ai-code-analyzer 33f/3,450 + nop-ai-dao 46f/614 + nop-ai-service 27f/863 + nop-ai-app 1f/32，合计 181 files/12,962 lines；codegen/meta/web 零 java 源登记零审计面；手写口径不含 `_` 前缀与 target/）
- **审核日期**: 2026-10-20
- **执行维度**: C1、C6 全模块 + C3 仅 rag 异步检索链（roadmap 3.4 裁剪集）+ C10 测试保护力（C2/C4/C5/C7/C8/C9 不在维度集，overlay 线索 CL×16）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 8dd63bedc1（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2476-cra-wi3.4-nop-ai-rest.md`（两轮对抗性审查通过后执行；R1 4 Major + 3 Minor 全修——**rag 异步前提失真**[零 CompletableFuture、async 委托零调用方→C3 重锚+空执行预登记]/coder 幻影锚点/nop-ai-api Protected Area/锚点底稿；R2 0 Major 置 active）
- **执行说明**: 全部批次串行派发（运营者要求）——3 初审批次（C1/C6 全模块 + C3 仅 rag）+ 深挖 1 轮 + 复核 1（三维度合并批）+ C10 + 裁定；**code-analyzer 入圈裁定成立**（R1：pom.xml:33 聚合器直含、roadmap 无其他工作项覆盖）；nop-ai-api 属 Protected Area（plan-first）——0 张工单触碰。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 8（P1×3/P2×1/P3×4） | +1（P3） | 9 | 0 | 0 |
| C3（仅 rag） | 2 | 4（P1×1/P2×2/P3×1） | +2（P2/P3） | 6 | 0 | 0 |
| C6 | 2 | 3（P1×1/P2×2） | +1（P2） | 4 | 0 | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 17 行矩阵全维持 + 缺保护 8（P2×4/P3×4） | 0 | 0 |
| **合计** | — | **15** | **+4 = 19** | **19** | 0（升格 1：C3-005 P2→P1） | 0 |

R2 深挖三维度全部显式终止（+4 全 P2/P3 且收敛证据齐备——code-analyzer 其余 29 文件清点、service 22 BizModel 逐字段、rag 异步站点总账闭环）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 19 条保留缺陷 → 合并后 **15 张工单 + watch 1 = 2 缺陷族（MA-01 schemaReady 三登记吸收 2 条、MA-02 static 卫生族吸收 1 条）+ 13 独立立案**；逐条落位无遗漏。
- 工单判级：**P0×0 / P1×4 / P2×6 / P3×5**；4 张条件化 P2 裁决留 7.1。
- 显式裁定：schemaReady 三登记归一 C6-001 主登记（一处修复闭合三登记）；C6-002/C6-004 分歧裁定双保留不合并（同族不同站点不同根因，C6-002「幂等可恢复」结论被推翻更正）；C3-002 watch（随 C3-001 修复关闭）；MA-02 组族新增待 7.1 追认。
- 跨单元对账：与 cra-index WI 1.1–3.3 台账（126 条 P1）**零同站点重复**；家族/设施关联 5 处单列（错误以成功交付族/内存放大族/取消契约族 rag 5 站点首立/F4 族/ShellRunner 相邻面）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 4（1 族 + 3 独立） | MA-01 PgVectorStore.schemaReady 实例级旗标 vs collection 级 DDL 作用域错配（多 index 部署从第二个起永久确定性 SQL 失败）；C1-001 JavaSourceFileFinder parallelStream 向普通 ArrayList 并发 add（静默丢文件/AIOOBE）；C1-003 InMemoryVectorStore removeIf+add 非原子 upsert（并发同 docId 重复条目，与 javadoc「线程安全」相悖——条件标注）；C3-005 synthesize 不检查 ChatResponse.isSuccess——LLM 错误响应静默折叠为空串 200 返回（升格：三步可达性闭合） |
| P2 | 6 | C1-004（code-analyzer static 面）、C3-001（embedAsync 契约分叉——异步变体缺同步版全部 null 校验）、C3-003（sync-over-async：syncGet 60s 有界长阻塞 + null 取消令牌——幽灵 LLM 任务）、C6-002（JavaMethodReplacer 截断式原地覆写无备份）、C6-003（JarTypeSolver 无 close 生命周期）、C6-004（JavaCodeFileInfoGenerator exists()-skip × 非原子截断写） |
| P3 | 5 | MA-02 static 非 final 卫生族、C1-007/008/009、C3-006（classifier 除零 NaN 静默丢标签） |

C10 另有缺保护登记 8 条（P2×4 对应 P1 面/P3×4，覆盖 17 行矩阵闭合，全部映射随本体工单）：保护力矩阵 17 行**会红 0/假保护 1/无测试 16**；反向钉死 1 项（TestCodeFileInfoStructure intern assertSame——修复 C1-008 必红）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 19 条保留发现全部为**新发现**（cra-index WI 1.1–3.3 台账零同站点命中；与 ARM/code-review 前案重叠命中均为风格/正确性域非并发域——JavaMethodReplacer MA4.2-11 仅删 demo main、PgVectorStore P1-7.1-002 已修复且不同面）；已知未修复 0、已修复 0。
- 潜伏面标注：code-analyzer 全模块零外部 main 消费者亲证（C1-001 并行面内生于方法体不适用单线程降档，按零调用方条件化保留 P1）。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C6-001（MA-01 主，吸收 C1-002/C3-004） | PgVectorStore.schemaReady 实例级旗标 vs per-collection DDL：多 index 部署从第二个起永久确定性 SQL 失败（进程生命周期不可自愈；并发首建另有 DDL 竞态窗） | nop-ai-rag PgVectorStore |
| 2 | C1-001（独立） | JavaSourceFileFinder.getAllJavaSourceFiles parallelStream 向普通 ArrayList 并发 add——多模块仓上静默丢文件或 AIOOBE（并行面内生于方法体，零调用方条件化保留 P1） | nop-ai-code-analyzer |
| 3 | C1-003（独立，条件标注） | InMemoryVectorStore removeIf+add 复合 upsert 非原子——并发同 docId 摄取产生重复条目（与 javadoc「线程安全」相悖；兄弟 PgVectorStore ON CONFLICT 正确范式反证） | nop-ai-rag InMemoryVectorStore |
| 4 | C3-005（独立，升格） | RagSynthesizeService 不检查 ChatResponse.isSuccess——LLM 错误响应静默折叠为空串以 200 返回（错误以成功交付族，与 WI 3.1 C7-002/WI 3.3 C3-001 家族关联） | nop-ai-rag synthesize |

## 盲区自评

- C2/C4/C5/C7/C8/C9 为 roadmap 3.4 裁剪维度：仅 overlay 线索登记（CL×16：并入工单 4/重复合并 3/独立移交 9/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- dao/api/app 面极稀疏（实测证实：dao 零 static 可变状态、api 零 static 可变集合）——C1 稀疏判定有逐条排除清单支撑。
- rag C3 为重锚后窄口径（零 CompletableFuture 前提下的死代码面+边界契约+sync-over-async）——完整异步面在 nop-ai-core（3.1 已审）。
- coder 转换器「逐调用 new 实例」假设证伪后（初审排除）未发现复用面——单例装配改造是回归面。
- MA-02 组族为本轮新增结构裁定，待 7.1 追认。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：JavaMethodReplacer 与 PgVectorStore 的前案重叠命中均为风格/正确性域（已排除同案）。
- 与 WI 1.1–3.3 台账（126 条 P1）零同站点重复；家族关联 5 处单列。
- security-audit roadmap：无重叠面。
