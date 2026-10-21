# 深度审核汇总报告 — nop-network + nop-integration 并发·异步·资源专项审计（CRA WI 4.3）

## 基本信息

- **审核单元**: `nop-network-integration`（16 模块根 26 叶源码根：nop-network codec/http[api/apache/jdk/oauth/okhttp/server-filters]/netty/rpc[api/core/http/model/simple]/socket/vertx[commons/mqtt-server] + nop-integration 10 叶，合计 270 files/24,528 lines src/main 口径；原语计数含测试树；`_gen/` 残留 rpc-model 8 个 `_` 前缀已排除；mqtt-client pom-only）
- **审核日期**: 2026-10-21
- **执行维度**: C1 共享可变状态、C3 异步链路、C6 资源生命周期（roadmap 4.3 裁剪集）+ C10 测试保护力（C2/C4/C5/C7/C8/C9 不在维度集，overlay 线索 CL×34）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit b63e4cbe85（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2479-cra-wi4.3-nop-network-integration.md`（两轮对抗性审查通过后执行；R1 4 Major + 3 Minor 全修——**IHttpClient 实现清单实测订正[jdk/okhttp/apache+oauth，无 vertx HTTP client]/nop-rpc-simple 口径[R2-M1 并入批 1 实审]/3 个 nop-*-api Protected Area 条款/目录路径/26 叶锚点底稿**；R2 1 Major + 5 Minor 全修——rpc-simple 排除去向虚构→并入批 1）
- **执行说明**: 全部批次串行派发（运营者要求）——C1/C3/C6 各×2 批次共 6 批次 + 深挖 1 轮 + 复核 2（C1+C6 合并批/C3 单批）+ C10 + 裁定；**Protected Area**：nop-http-api/nop-rpc-api/nop-integration-api 三叶命中 plan-first——审计只读不受限，P0 修复仅限 impl 叶；**本单元为多个前序单元消费面回写落点**（WI 3.1 C7-001 SSE readLine 本体面、WI 2.3 CL-022/CL-025、WI 2.1 CL-021 feishu 点名）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2（2 批次） | 17（P1×3/P2×11/P3×3） | +3（P2×2/P3） | 20 | 0 | 1（C1-004→C3-010 同案并入） |
| C3 | 2（2 批次） | 32（P1×8/P2×16/P3×8） | +1（P2） | 30 | 0 | 3（C3-019/020/031 同案并入，缺陷本体成立） |
| C6 | 2（2 批次） | 27（**P0×1**/P1×10/P2×13/P3×3） | +1（P2） | 25 | 1（C6-003 P1→P2 对齐 09 前案） | 3（C6-008/009/014 同案并入） |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 74 行矩阵全维持 + 缺保护 15（立项 14+watch 1） | 0 | 0 |
| **合计** | — | **76** | **+5 = 81** | **74** | 1（升格 1：C6-024 P2→P1） | 7（全部同案并入，无证伪驳回——初审证据质量高） |

R2 深挖三维度全部显式终止（+5 全 P2×4/P3×1 且收敛证据齐备——载体清单收敛、异步站点总账闭环、六深挖方向三面核对通过）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 74 条保留缺陷 → 合并后 **58 张工单 = 17 缺陷族（MA-01…17，覆盖 32 条保留成员 + 7 条并入登记）+ 41 独立立案**；watch-only 1（C1-020 理论 JMM）。§六权威表经脚本程序化校验（81 行 = 74 保留 + 7 并入，零缺漏零重复）。
- 工单判级：**P0×1 / P1×15 / P2×32 / P3×10**；P1 面 21 条全部保级（7 张 P1 族 + 8 张 P1 独立 + 判级对齐）。
- 冲突裁定 12 处（判级分歧 3 留 7.1——其中 MA-05 主登记结构本级终裁为 C1-009 主登记合并单工单；本级新增 9 处含 MA-08 主键=C3-008、「下载链族」建议网不采纳为单一缺陷族等）。
- 跨单元对账：与 cra-index WI 1.1–4.2 台账（137 条 P1）**零同站点重复**；跨单元关联 10 处单列（future 永不完成族第 4 例 C3-001、错误以成功交付族第 4 例 C3-005、WI 2.5 MA-01 兄弟面、WI 4.1 MA-05 配对族、WI 2.1 CL-021 载体、WI 3.1 C7-001 消费面回写两侧同批、WI 2.3 CL-025/CL-022、3.9 前案并轨、api-core bindCancel 协同）；globalTimer 族确认不涉。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| **P0** | **1** | **C6-001**：JDK/OkHttp 下载成功路径从不调用 FileDownloadSink.close()——resume 默认 true 下每次成功下载泄漏文件句柄（Windows Files.move 确定性失败/Unix 静默 fd 泄漏）；四子项复核核验维持（证伪前轮 3.9「文件模式主路径确认关闭」——真实漏检）；修复仅须覆盖 JDK/OkHttp（Apache HC5 兜底闭合） |
| P1 | 15（7 族 + 8 独立） | MA-01 MQTT 三登记族（C1-008/C3-018/C6-020——会话接管订阅抹除 + 失效面①证伪修正）、MA-02 JDK 响应体取消/遗弃三登记、MA-03 sidecar get(30s) reactor 线程（HC5 ioThreadCount 默认即 1 复核加重）、MA-04/05/06 feishu 三工单（无界 get 单线程死锁/重连世代守卫/stop 竞态——MA-05 主登记 C1-009 本级终裁）、MA-07 rpc-simple init 非幂等（CL-024 原始站点）；独立：C1-001 SimpleRpc 前置拦截器累积、C3-001 OkHttp onResponse 吞异常（future 永不完成族第 4 例）、C3-003 sidecar 语义/下载成功无产物、C3-005 无校验源跳过改名（错误以成功交付族第 4 例）、C6-004 SSE 反压无超时阻塞、C6-024 FeishuClient stop 竞态（升格）等 |
| P2 | 32 | MA-08/09/10 + C1-002 oauth 监视器内 token HTTP 往返、C3-024 SftpClient 全链路无超时、C3-033 SSE 终态后无终止隔离、C6-028 vertx 异步 close 不等待等 |
| P3 | 10 | C1-020 watch + 各尾部条目 |

C10 另有缺保护登记 15 条（立项 14+watch 1，覆盖 74/74 闭合[含 A1/A2 补登]，全部映射随本体工单）：保护力矩阵 74 行**会红 0/假保护 20/无测试 54**——P0×1 + P1×21 全部无保护；反向钉死 5 处（2 断言级 MQTT assertThrows 同案 + 3 掩蔽/耦合级——Windows 门控排除 P0 唯一确定性症状/Feishu 假体结构性不可构造/死代码测试耦合）；先行失败测试 16 处。

## P0 记录（即时通道）

本单元 **P0 = 1**：**C6-001**（JDK/OkHttp 下载成功路径从不 close 文件句柄）——四子项复核核验维持 P0（成功路径从不 close；resume 默认 true 即主路径；Windows Files.move 确定性失败 + Unix 静默 fd 泄漏；Apache HC5 三终态兜底闭合）。**证伪前轮 code-review-full 3.9 的「FileDownloadSink 文件模式主路径确认关闭」结论**——真实漏检。处置：按 plan Phase 7 即时通道修复（JDK/OkHttp 成功路径补 sink.close()，与 C3-005 无校验源跳过改名同批设计）。

## 现状标注摘要

- 74 条保留发现：新发现 ×73 + 已知未修复 ×1（C6-003 ← 2026-09-22 code-review-full 3.9 同站点前案 P2，本轮对齐降级）；已修复 0。
- 关键佐证：前轮 3.9 曾将 FileDownloadSink 文件模式主路径列为确认关闭子域——本轮回码证伪为真实漏检（C6-001 P0 的来源）。

## 关键发现摘要（P0 + P1 摘要，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 0 | C6-001（**P0**） | JDK/OkHttp 下载成功路径从不 close 文件句柄——resume 默认主路径每下载泄漏 fd；Windows Files.move 确定性失败 | nop-http-client-jdk/okhttp |
| 1 | C1-008+C3-018+C6-020（MA-01 MQTT 三登记） | MQTT 会话接管订阅抹除（removeConnection 无属主校验 + 失效面①证伪修正）——设备重连后下行路由静默失效 | nop-vertx-mqtt-server |
| 2 | C3-001（独立） | OkHttp onResponse 异常吞没——body().string() 断连 IOException 被吞 future 永不完成（future 永不完成族第 4 例） | nop-http-client-okhttp |
| 3 | C3-005（独立） | 无校验源跳过 verifyAndRename——默认配置下载报成功但目标文件永不产生（错误以成功交付族第 4 例；与 C6-001 同批设计） | nop-network FileDownloadSink |
| 4 | C3-002/003/004（MA-02/03） | JDK 取消语义失效双子星（幽灵下载/SSE 线程按次泄漏）+ sidecar get(30s) 跑 reactor 线程（HC5 ioThreadCount 默认 1） | nop-http-client-jdk/apache |
| 5 | C3-015 + C6-024（MA-04/06 feishu） | FeishuClient 无界 get 单线程 scheduler 死锁（心跳/重连整体死锁）+ stop 竞态孤儿 WS（升格 P1） | nop-integration-feishu |
| 6 | C1-009（MA-05 主登记） | FeishuClient 重连无单飞/世代守卫——双活/三活 WS、消息重复投递（前提(a) 复核义务随工单） | nop-integration-feishu |
| 7 | C1-001（MA-07 主） | SimpleRpcClientFactory.init 挂在 newInstance 非幂等——拦截器列表无界累积 + timer 泄漏（WI 2.1 CL-024 原始站点） | nop-rpc-simple |
| 8 | C6-004（独立） | SSE 反压无超时阻塞——Apache 形态挂起 I/O reactor 线程/JDK readLine 不可中断（与 WI 3.1 C7-001 本体面两侧同批） | nop-http 三实现 |

## 盲区自评

- C2/C4/C5/C7/C8/C9 为 roadmap 4.3 裁剪维度：仅 overlay 线索登记（CL×34：并入工单 7/重复合并 10/独立线索 12/移交 5/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- 外部 SDK 黑盒（HC5/okhttp/netty/vertx/jsch 库内部）按使用面审计——判定采信处均经字节码核验（HC5 5.6.4/5.4.4、vertx-mqtt 5.1.8、netty-buffer 4.1.128）。
- C3-002/C6-002/C8-004 生产主路径免疫标注沿用 4.1 口径；本单元 P1 的嵌入方/主路径权重分歧已留 7.1（3 处）。
- R2 深挖三维度全终止（+5 全 P2/P3），无口径偏差。
- rpc-simple 并入批 1 为 R2-M1 修正——SimpleRpcClientFactory 站点实审覆盖，WI 2.1 CL-024 原始落点闭环。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：C6-003 与 2026-09-22 3.9 前案并轨（对齐降级 P2）；**3.9 的「文件模式主路径确认关闭」误结论由本单元 C6-001 证伪**（并轨注记）。
- 与 WI 1.1–4.2 台账（137 条 P1）零同站点重复；跨单元关联 10 处单列。
- security-audit roadmap：无重叠面。
