# Dispatch Log — CRA WI 4.3 nop-network-integration 并发审计

- **Plan**: `ai-dev/plans/2479-cra-wi4.3-nop-network-integration.md`（active；R1 agent_770fbb03 4 Major[IHttpClient 实现清单错/nop-rpc-simple 口径/3×nop-*-api Protected Area/目录路径]+3 Minor 全修；R2 agent_6f6513e2 1 Major[rpc-simple 排除去向虚构→并入批 1]+5 Minor 全修）
- **基线**: commit b63e4cbe85，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-network-integration.txt`（15 命令组 × 26 叶 = 1,440 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-http-api 46f/4,478 + nop-rpc-api 18f/750 + nop-integration-api 24f/1,399 三叶命中 plan-first——审计只读不受限；P0 即修仅限 impl 叶，触 api 叶须 plan-first 登记
- **跨单元落点**: WI 2.3 CL-022 orTimeout 本单元零命中（同族对照为超时站点）；WI 2.3 CL-025 IHttpResponse 断连观测→本单元实现面落点；WI 3.1 C7-001/C8-001 消费面回写（JdkHttpClient SSE readLine）；WI 2.1 CL-021 feishu 点名核实（FeishuClient 心跳面 heartbeatFuture:81/定义:258）

## 源码根实测（Phase 1 回填，src/main 口径；原语计数含测试树）

**26 叶全清单**：network 16 叶（codec 6f/294；http 46+10+8+6+3+1=74f/8,249[api/apache/jdk/oauth/okhttp/server-filters]；netty 31f/2,313；rpc 18+30+3+16+3=70f/5,545[api/core/http/model/simple，simple 3f/561 **在批 1 实审**]；socket 11f/1,203；vertx commons 3f/89+mqtt-server 13f/1,054）+ integration 10 叶（api 24f/1,399；feishu 22f/2,045；email-java 2f/394；email-tencent 1f/198；file-local 3f/320；oss 3f/442；sftp 4f/554；sms-tencent 1f/201；sms-yunpian 1f/167；zxing 1f/61）= **270f/24,528**。

## 预注锚点核实（Phase 1，R1/R2 双实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| IHttpClient 实现 | JdkHttpClient/OkHttpClientImpl/ApacheHttpClient+Helper + oauth enhancer；无 vertx HTTP client | R1 订正 |
| rpc-simple | 3f/561，SimpleRpcClientFactory:31；roadmap 6.x 零覆盖 | R2-M1 并入批 1 |
| nop-*-api 叶 | 3 个命中 plan-first | 预注⑤+Phase 7 条款 |
| orTimeout | 两树 0 命中 | CL-022 同族对照为超时站点 |
| FeishuClient 心跳 | heartbeatFuture:81/heartbeatIntervalSeconds:67/scheduleHeartbeat 定义:258 | 3.3 缺席裁定回写 |

## owner doc 漂移登记

- `docs-for-ai/03-modules/nop-network.md`：Phase 1 粒度核验在册（契约以 javadoc 为准处按实测）；nop-integration 侧 owner doc 覆盖面按 drift 登记执行。

## 历史审计对照口径

- `ai-dev/audits/` 下两树 **并发主题**直接审计 0 命中（规模为 src/main 口径）。

## 维度级派发记录

（Phase 2 起逐条追加）

### Phase 2 初审（2026-10-21，6 批次串行）

| 批次 | 编号 | 结果 |
|------|------|------|
| C1 批 1 | C1-001…007；CL-001…004 | **P1×1**（SimpleRpcClientFactory.init 非幂等——CL-024 原始站点）+P2×4+P3×2 |
| C1 批 2 | C1-008…017；CL-005…010 | **P1×2**（MQTT 会话接管订阅抹除/FeishuClient 重连无单飞世代守卫）+P2×7+P3×1 |
| C3 批 1 | C3-001…014；CL-011…015 | **P1×5**（OkHttp onResponse 异常吞没/JDK 取消语义失效双子星/下载完成语义违约两处——sidecar get(30s) 跑 reactor 线程+无校验源跳过改名）+P2×7+P3×2 |
| C3 批 2 | C3-015…032；CL-016…020 | **P1×3**（FeishuClient 无界 get 单线程 scheduler 死锁/MQTT removeConnection 订阅抹除/SftpClient 全链路无超时）+P2×9+P3×6 |
| C6 批 1 | C6-001…016；CL-021…026 | **P0×1**（下载成功路径从不 close 文件句柄——resume=true 每次泄漏，Windows Files.move 直接失败）+**P1×5**（416 陈旧 Range 重传/SSE 反压无超时阻塞等）+P2×7+P3×3 |
| C6 批 2 | C6-017…027；CL-027…032 | **P1×5**（MQTT 订阅登记无属主校验/TencentEmailSender SesClient 不关/FeishuClient stop 与重连竞态）+P2×6 |
| **Phase 2 合计** | 76 条 + CL×42 | **P0×1/P1×21/P2×40/P3×14** |

- 继续条件判定：3 维度均含 P0/P1 → **全部必派第 2 轮深挖**（合并批）

### Phase 3 深挖（2026-10-21）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C3/C6 | +5（全 P2×4/P3×1）：C1-018/019/020、C3-033、C6-028 + 扩展 4 处[C3-008 失效面修正中招入口 1→3、C3-012 新增 2 站点]；CL-033/034 | **3 维度全部显式终止**（无 P0/P1 新增） |

- **深挖后累计 81 条**：C1×20（P1×3/P2×13/P3×4）、C3×33（P1×8/P2×17/P3×8）、C6×28（P0×1/P1×10/P2×13/P3×4）→ P0×1/**P1×21**/P2×44/P3×15；CL×34（C6 括注勘误：原记 P2×13/P3×4 系笔误——实为 P2×14/P3×3，closure audit B5 订正）

### Phase 4 独立复核（2026-10-21，3 维度 2 派发串行）

| 批次 | 判定 | 最终严重度 | 关键裁定 |
|------|------|-----------|---------|
| C1+C6 | 48 条：保留 43+并入 4+降 1+升 1 | C1 P1×3/P2×12/P3×4；C6 **P0×1**/P1×10/P2×12/P3×2 | **C6-001 P0 四子项核验维持**（证伪前轮 3.9「文件模式主路径确认关闭」——真实漏检）；C6-024 升格 P1；C6-003 对齐 09 前案降 P2；消费面回写核对成立（SSE readLine 本体面已立案） |
| C3 | 33/33 保留（同案并入 3） | P1×8/P2×15/P3×7 | C3-004 复核加重（HC5 ioThreadCount 默认即 1）；C3-021/022 字节码独立再证；同族跨单元第 4 例登记（future 永不完成/错误以成功交付两族） |

- **复核后最终保留 74 条：P0×1/P1×21/P2×39/P3×13**；同案并入 7 条计入主登记；判级分歧 3 处留 7.1
- 主登记建议网：C6-001（P0 下载句柄泄漏，与 C3-005 同批设计）、C1-008=C3-018=C6-020（MQTT 三登记一工单）、C3-001/C3-003/C3-005（下载链族）、C6-004（SSE 反压族）、C1-009 主登记合并（feishu 重连族）

### Phase 5 C10 测试保护力（2026-10-21）

- 保护力矩阵 74 行：**会红 0 / 假保护 20 / 无测试 54**（P0×1 + P1×21 全部无保护）；缺保护登记 C10-001…015（立项 14 + watch 1，覆盖闭合含 A1/A2 补登）
- 反向钉死 5 处（2 断言级 MQTT 同案 + 3 掩蔽/耦合级——Windows 门控/Feishu 假体/死代码耦合）
- 独立复核：0 对账差异、16+ 行回测零误判、15 登记全保留、5 勘误 + 2 覆盖补登落账

### Phase 7 P0 即时通道（2026-10-21，主 agent 执行）

- **P0 = C6-001**（campaign 第 3 个 P0）：修复 JdkHttpClient.processDownloadResponse + OkHttpClientImpl onResponse 成功路径 verifyAndRename 后补 sink.close()（416 重试支路外层 sink 未 begin 为 no-op 天然安全；**仅限 impl 叶未触 nop-*-api**）
- 回归测试 TestFileDownloadSinkClose：fd 计数口径（UnixOperatingSystemMXBean 可用性探测）、预热 3+连发 40+增量≤10 断言；**测试语义修正 2 轮**（自身踩 C3-005 语义/静态 checksum 时序——修正后成立）；**回退实测必红**（5.7s Failure）
- 两模块套件 33 测试全绿（jdk 21/okhttp 12）；scan-hollow --severity high 退出码 0
- owner doc：下载句柄行为对齐 Apache 既有正确实现（非新包络），No owner-doc update required

### Phase 6 合并裁定（2026-10-21）

- 独立裁定子代理串行派发一次；输入 = 74 条保留发现（三维 `## 最终保留项`/`## 维度复核结论`）+ C10 矩阵（含 A1/A2 补登）+ cra-index WI 1.1–4.2 台账 137 行
- 输出 = merge-adjudication.md：74 → 58 工单（17 族 MA-01~17 覆盖 32 保留成员 + 7 并入登记 + 41 独立 + watch 1；§六脚本程序化校验 81=74+7），P0×1/P1×15/P2×32/P3×10；冲突裁定 12 处（判级分歧 3 留 7.1——MA-05 主登记本级终裁 C1-009 主、MA-06 合并单工单留 7.1；本级新增 9 处）；CL 34 全落位（7/10/12/5/0）；C10 15 全映射
- summary.md 由主 agent 按附录 B 撰写；cra-index 登记（报告行/P0 行/P1 台账 15 行[closure audit B1 按 §二/§三 重写]/CL 4.3 小节；P1 累计 137→152）

### Phase 7 P0 即时通道（2026-10-21，主 agent 执行）

- **P0 = C6-001**（campaign 第 3 个 P0）：修复 JdkHttpClient.processDownloadResponse + OkHttpClientImpl onResponse 成功路径 verifyAndRename 后补 sink.close()（416 重试支路外层 sink 未 begin 为 no-op 天然安全；仅限 impl 叶未触 nop-*-api）
- 回归测试 TestFileDownloadSinkClose：fd 计数口径（UnixOperatingSystemMXBean）+ 预热 3+连发 40+增量 ≤10 断言；**回退实测必红**（stash 后 5.7s Failure）；两模块套件 33 测试全绿；scan-hollow 退出码 0
- owner doc：No owner-doc update required（行为对齐 Apache 既有正确实现，非新包络）
