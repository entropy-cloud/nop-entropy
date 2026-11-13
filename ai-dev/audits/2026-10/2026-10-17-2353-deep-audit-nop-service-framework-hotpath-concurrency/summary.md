# 深度审核汇总报告 — 请求热路径（graphql/biz/gateway/biz-auth-core/biz-file-core）并发·异步·资源专项审计（CRA WI 2.3）

## 基本信息

- **审核单元**: `nop-service-framework-hotpath`（`nop-service-framework/` 聚合器下五模块 8 源码根 406 files/42,748 lines：nop-graphql 196f/21,292（core 145f/17,097 + orm 11f/859 + message 3f/162 + grpc 37f/3,174）、nop-biz 89f/9,985、nop-gateway 44f/4,743、nop-biz-auth-core 60f/5,760、nop-biz-file-core 17f/968；手写口径不含 `_` 前缀与 target/；nop-biz-auth-api 28f 不在范围）
- **审核日期**: 2026-10-17 ~ 2026-10-18
- **执行维度**: C1/C2/C3/C4/C8/C9 全维度（6 初审批次）+ C10 测试保护力（最后批次）；C5/C6/C7 维度集外 overlay 三形态（CL 前缀 `nop-service-framework-hotpath-CL-0NN`）
- **目标范围**: `src/main/java` 手写代码；基线 commit 5994c18211（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2470-cra-wi2.3-hotpath.md`（两轮对抗性审查通过后执行：R1 1 Blocker（nop-graphql 4 子模块聚合器无 src/main——拆 8 源码根）+ 4 Major + 3 Minor 全修；R2 0 Blocker/Major + 4 Minor 全修）
- **执行说明**: 全部批次串行派发（运营者要求）；判级口径「请求热路径被高并发调用，每请求必经缺陷常见条件门槛最低，特定 resolver/biz 方法内条件化降档」；Phase 1 锚点回填门执行（R2 代实测 6 真 3 幻——幻影名 IResourceResourceManager/file 去重表/@BizMethod 已订正）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回/撤案 |
|------|---------|---------|---------|---------|------|----------|
| C1 | 2 | 8（P1×1/P2×5/P3×2） | +2（P2×2）+CL-016/017 | 10 | 1（**C1-001 P1→P2** 部署一致性） | 0 |
| C2 | 2 | 6（P1×1/P2×5） | +0 立案（C2-006 撤案建议） | 5 | 2（**C2-004 P1→P2 并入 C1-001**、C2-002→P3） | 1（C2-006 撤案成立） |
| C3 | 3 | 12（P1×5/P2×7） | +5（**C3-013 P1**（r3 降 P3）、C3-014/015/016/017）+CL-019/020 | 17 | 5（**C3-001/013 P1→P3 二次反证、C3-005 P1→P2 实害证伪、C3-014/016→P3 dormant**） | 0 |
| C4 | 3 | 6（P1×1/P2×4/P3×1） | +2（**C4-007 P1**、C4-008 P2）+CL-022~025 | 8 | 1（**C4-003 P1→P2**——JDK 25 delayedExecutor 走 ForkJoinPool 多线程中继） | 0 |
| C8 | 2 | 8（P1×2/P2×6） | +2（C8-009 P2、C8-010 P3） | 10 | 0 | 0 |
| C9 | 2 | 11（P2×6/P3×5） | +2（**C9-012 P1**（CL-015 终裁升格移交功能线）、C9-013 P3） | 13 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 17（缺保护登记，全 P2） | 0 | 0 |
| **合计** | — | **51（P0×0/P1×10/P2×33/P3×8）** | **+13 = 64（P1 存量 12→含升格 C9-012）** | **63 全保留（含降级 9：P1×6 + P2→P3×3）+ 17 缺保护；另 1 撤案（C2-006 转正面样例）+ 1 量化记录（C9-008）** | 9 | 1 撤案 |

C1/C3/C4 各派 3 轮（新增 P1 触发收口轮）；C2/C8/C9 各 2 轮终止。C10 复核 13 行抽验 0 翻转。

## 合并裁定（详见 merge-adjudication.md）

- 台账 63 条不动（逐一归属）；**维度内并入 4 → 有效发现 59 = P1×7 / P2×39 / P3×12 + 量化记录×1（C9-008）**；跨单元并入 1（C9-002 → WI 2.1 C9-010，修复单点在 WI 2.1）→ 本单元承担修复口径 **58 条**。
- 合并组：**C1-001←C2-004**（UserContext 设施双镜头，双复核确认）、**C1-003←C3-014**（registerSubscription 同位点同交错，终态机 payload 五项强制随迁）、**C1-003←C8-004**（同站点同交错同后果，自主扫描新裁）、**C2-001←C9-007**（dispatchAll 监视器内同步批 I/O，按高判级方消解）。同族相邻不归并互列关联 7 组 + 自主补登相邻族 4 组。
- **P1 零变动**：7 条全部维持（立案 6：C3-002/003/007、C4-007、C8-001/002 + C9-012 移交案）。
- CL 台账终态：编号段 25、**实占 24**（CL-021 空号——dispatch-log 曾误记「CL-021→安全线」系与 WI 2.2 同号混写，已勘误）；升格 3（CL-003→C8-005、CL-010→C9-005、CL-015→C9-012）、并入 5、驳回 1（CL-011 维持不成立终判）、纯移交（CL-016→C4、CL-017→C4/C5、CL-022→nop-ai/nop-message、CL-023→认证/安全线、CL-025→平台原语前置）。
- **跨单元映射表**：①C9-012→功能正确性维度（P1 移交案，收口落名接收轨）；②C9-002→WI 2.1 C9-010（同链第三面 WI 2.1 C7-007 P1，三面互引不重复计数）；③C1-010→nop-auth-service 单元（Dao flush 修复归属）；④C4-008/C2-002 跨模块验收（nop-auth-sso/nop-http-api）；⑤CL-018 是本单元唯一→nop-kernel 移交（ContextTaskQueue 承重原语）；⑥CL-025 平台原语前置（C3-010/C4-003 修复先决：IHttpResponse 断连观测原语缺失）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 7 | **C3-002** invokeOperation 出口泵缺 thenOnContext（OrmBatchLoader 在完成线程 requireSession→NOT_IN_SESSION/错会话）、**C3-003** BufferedStreamingPublisher 背压断裂（自驱动 request(1) 越窗后无界 ArrayDeque，AI 路由默认形态）、**C3-007** BizActionService 裸 execute 不传播 IContext（租户/用户跨任务残留+proceedAsync NPE 面）、**C4-007** SSO 半衰期刷新在载体线程无超时阻塞 IdP HTTP（IdP brownout 确定性服务级停摆；修复勘误：ILoginService 异步化方向）、**C8-001** WS 订阅推送同步阻塞发送钉死 globalTimer（实为 4 线程，ρ=N·L/40，1~4 慢客户端即钉死）、**C8-002** Gateway 429 重试放大 ×7 无准入上界（在途×90s 挂起，OOM 轨迹确定）、**C9-012**（移交功能线）LocalDateTimeFieldMarshaller computeSize=20 vs 纳秒实写 23-29 字节 protobuf 越界（实测双模式硬失效） |
| P2 | 39 | 用户上下文设施族（C1-001 族案、C1-009/010 Dao flush 再登录风暴链）、订阅注册表族（C1-003 族案含 C3-014/C8-004 并入、C2-005、C8-007）、锁内 I/O 族（C2-001←C9-007、C2-002/003）、异步上下文族（C3-008/011/012/015/017、C4-004）、载体治理族（C4-001/002/005/008、C4-003 降级后）、过载族（C8-003/004/005/006/008/009）、热路径族（C9-001~006）等 |
| P3 | 12 | C1-007/008、C2-002（降级）、C3-001/013/016（降级）、C4-006、C8-010、C9-009/010/011/013——与 merge-adjudication.md §台账权威十二条清单一致（A4 对齐；C3-014 为被并案不计入、C9-007/008 为量化记录单列） |

C10 另有缺保护登记 17 条（全 P2）：保护力矩阵 **有钉 0 / 部分 25 / 无保护 36**（62 行 + 量化记录 2 不计）；**P1×7 = 部分 5 + 无保护 2（C3-007、C9-012 完全裸奔）**。测试资产 114 测试文件/428 @Test（审计分支口径），真并发断言仅 4 个且无一落在 P1/P2 交错面；nop-graphql-message 整模块无测试目录。测试套件自身缺陷注记 N1~N7（复核 7/7 属实——N1 流控用例未实例化被测类虚假覆盖、N5 Mock session 永不失败掩蔽 C8-001、N6 batch loader 对 context 无感掩蔽 C3-002）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 63 条保留发现全部「新发现·未修复」（产品代码最后提交 2026-08-26 先于审计；cra-index WI 1.1–2.2 台账不含本单元）。
- 维度集外线索 24 条实占终态：升格 3 + 并入 5 + 驳回 1 + 纯移交若干 + 关联保留（全表见 merge-adjudication.md §CL 台账）。

## 关键发现摘要（P1 全清单 7 条，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C3-002 | invokeOperation 出口泵缺 thenOnContext：完成线程 requireSession 失败/错会话 | nop-graphql-orm 出口链 |
| 2 | C3-003 | BufferedStreamingPublisher 背压断裂：越窗后无界缓冲，慢客户端内存失控 | nop-gateway 流式 |
| 3 | C3-007 | BizActionService 裸 execute：调用方 IContext 不传播，租户/用户跨任务残留 | nop-biz 异步执行 |
| 4 | C4-007 | SSO 半衰期刷新无超时阻塞 IdP HTTP：brownout 确定性服务级停摆 | nop-biz-auth-core × nop-auth-sso |
| 5 | C8-001 | WS 推送同步阻塞发送：1~4 慢客户端钉死 globalTimer 全池（平台 27 处消费方） | WS 适配 × 平台调度 |
| 6 | C8-002 | Gateway 429 重试放大 ×7 无准入上界：在途×90s 确定性 OOM 轨迹 | nop-gateway 重试链 |
| 7 | C9-012（移交功能线） | LocalDateTimeFieldMarshaller protobuf 线格式越界（双模式硬失效实测） | nop-graphql-grpc 序列化 |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除；C10 盘点曾误用 master 工作副本（复核修正为审计分支口径 114/428）。
- C5/C6/C7 为维度集外 overlay 三形态（CL 线索登记）——若 7.1 需要完整口径需补审计。
- 6 条 P1 降级全部有实测/语义证据支撑（部署一致性×2、字节码二次反证×2、shipping 客户端非阻塞、JDK 25 语义）；C8-001/002 双 P1 在 C4-003 降级事由外独立成立。
- C9-012 移交功能正确性维度的接收轨在收口时落名（cra-index CL/移交节）；nop-auth-service（Dao flush 修复归属）为 WI 5.x 未审单元——其审计时须回看 C1-010。
- CL-025 平台原语前置（IHttpResponse 断连观测缺失）是 C3-010/C4-003 修复的先决条件——Phase 8 批次排序输入。
- 修复耦合：用户上下文设施工单（C1-001 族案+C1-002/009+R-CL-1）、订阅注册表工单（C1-003 族案+C2-005）、Dao flush 工单（C1-010，归 nop-auth-service）、网关重试链工单（C4-003+C3-010+CL-025 原语先行）、测试 N1/N5/N6 须随对应修复改写。

## 与其他 roadmap 的重叠

- 与 WI 1.1–2.2 台账对账：**1 处跨单元并入**（C9-002→WI 2.1 C9-010，同链第三面 WI 2.1 C7-007 P1，修复单点在设施层）；C9-002 为热路径量化实例互引不重复计数。其余映射为移交/前置关系。
- 2026-09 nop-biz 写限制质量域审计：无同位点（本轮零重叠实测确认）。
- 安全线：无移交案（CL-021 空号勘误后本单元无安全线移交；OsUserHelper 案属 WI 2.2）。
