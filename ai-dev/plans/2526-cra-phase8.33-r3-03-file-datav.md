# 2526 CRA Phase 8.33 — R3-03 5.3 file-datav 批（14 项：W-01 文件去重竞态原子化[P1·CL-014] + W-04 查询上界 R02 三范式[P1·CL-009 主，携 CL-010/CL-021 对齐成员] + W-02 终态 fencing 族三成员[CL-003 携反向钉死同 diff 改写 / CL-001 cancel 竞态 / CL-016 孤儿文件] + W-03 check-then-act 三成员[CL-002 发布版本号 / CL-004 告警状态行 / CL-005 限额 TOCTOU 携假保护同 diff 修正] + W-05 导出临时文件泄漏[CL-015] + W-07 调度生命周期三成员[CL-019 注销回滚窗口 / CL-020 注册失败停摆·平台修复归 R1-01 接入点登记 / CL-023 SMTP 半程窗口]）

> Plan Status: active
> Draft Review R1: agent_71f576e2 @ 2026-11-02 —— **无 Blocker；2 Major + 3 Minor**——机械修订闭环，修订转 active（无需第二轮全量复审）：Major-1 CL-003 条件更新 0 行分支新孤儿面（裁定③/⑤增补 fileId 处置条款——回收或带 fileId 落 FAILED 二选一入裁定域，红②/红④断言面同步覆盖）；Major-2 裁定⑤备选载体面（nop-biz-file-core 公共接口形态[IFileStore 加方法]须四件证据链追加申报+全 impl 回归面，裁定⑤预设 Option A=daoFor(NopFileRecord)+getResourceStore() 镜像 detach 序列为非扩权缺省）；Minor-3 Scope In-Scope recovery 路径修正（entity→export）；Minor-4 载体文件计数统一逐名清单口径（datav 16+file 1）；Minor-5 裁定⑧谓词枚举补 NULL（首通知）分支覆盖。后续轮次如有则顺延登记
> Last Reviewed: 2026-11-02
> 起草说明：本 plan 由独立起草子 agent 于 2026-11-02 在工作仓 `nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation` @ `9ab4a0f4de`）起草；全部成员站点行号为该基线 live 亲读回读值（勘误节逐条登记漂移核对结果，含计划外 ChatBI 工具执行器红面发现——勘误 3），执行前如再漂移以 live 为准并登记。结构沿 plan 2525 先例（Source 全文引用 / Draft Review 行预留 / PA 判定表 / 对齐纪律显式条款 / 成员逐条表 / 勘误节 / 红绿判据 / E1–E6 / 组划分 / Closure Gates / Deferred / Risks）。待独立 draft review 通过后修订转 active。
> Source: roadmap 工作项 8.33（R3-03，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :175，Status=todo[本 plan 立项时回写为「todo（plan 2526 drafted，待 draft review @ 2026-11-02）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-03 行（L103 全文照录：finding ID 清单 14 项[5.3-W-01；5.3-W-04；5.3-CL-001；5.3-CL-002；5.3-CL-003；5.3-CL-004；5.3-CL-005；5.3-CL-010；5.3-CL-015；5.3-CL-016；5.3-CL-019；5.3-CL-020；5.3-CL-021；5.3-CL-023] / 模块与验证命令「nop-datav；nop-file / `./mvnw test -pl nop-datav -am`；`./mvnw test -pl nop-file -am`」 / Owner Doc=「nop-datav 无 owner doc→缺口声明（5.3 drift 登记）；`docs-for-ai/03-modules/nop-file.md`」 / PA=「无（nop-file-api plan-first 零触碰声明）」 / 依赖「无前置（可并行）；W-04 R02 与 1.5-MA-05/1.7-C5-001 批次对齐；CL-020 平台修复归 R1-01（本单接入点登记）；CL-010/021=R02 对齐」 / exit criteria 增补：「**CL-003 反向钉死同 diff 改写**（testSlowExecutionRaceWorkerSuccessOverwritesScanFailed 反转，跨单元 fencing 族对齐 R12）；CL-005 假保护（顺序测试伪装）同 diff 修正；W-01 去重竞态原子化；W-04 rowLimit 上界（R02 三范式）；缺保护 3/4/5 验收前置」）+ §一 通用门禁 E1–E6（E1 全量构建绿 / E2 focused 测试先红后绿+C10 同 diff / E3 双通道验证命令留证 / E4 PA 授权声明[本批 PA=无——零 api 叶零 orm 预设] / E5 owner doc 更新[nop-datav.md 为**新建** owner doc——缺口声明兑现] / E6 批级 closure 独立 audit）；成员发现详情 = WI 5.3 审计目录 `ai-dev/audits/2026-10/2026-10-21-1227-deep-audit-nop-file-datav-concurrency/`（merge-adjudication.md[§一 裁定输入与范围 :6-16 / §三 缺陷族合并 :25-34 / §四 冲突裁定 3 项 :36-40（#2 rowLimit 路径注记非包络声明——W-04 P1 维持）/ §五 跨单元关联 :42-52 / §六 工单台账权威表 :54-66 / §七 结构性发现 :68-73（#2 afterCommit 先例未普及——CL-019 修复方向 / #4 nop-file-api PA 记录）/ §八 CL 线索落位权威表 :75-103 / §九 修复批次与后续 :105-112]、C1-shared-mutable-state.md[CL-001 :46-69 / CL-002 :71-90 / CL-003 :92-114 / CL-004 :116-136 / CL-005 :138-157 / CL-006 :159-179]、C1-round2.md[CL-019 :7-26 / CL-020 :28-50]、C5-memory-bounds.md[CL-009 :28-48 / CL-010 :50-72 / CL-011 :74-95 / CL-012 :97-115 / CL-013 :117-135]、C5-round2.md[CL-021 :7-29 / CL-022 :31-52]、C6-resource-lifecycle.md[CL-014 :25-48 / CL-015 :50-76 / CL-016 :78-99 / CL-017 :101-124 / CL-018 :126-148]、C6-round2.md[CL-023 :7-28 / CL-024 :30-52 / CL-025 :54-77]、C10-test-protection.md[既有测试盘点 :7-20 / 保护力矩阵 :23-49 / 反向钉死登记 :51-53 / 时序敏感审查 :55-59 / 泄漏钩子 :61-63 / 补测清单 5 项 :65-71]、summary.md）；C10 承载口径 = 补测清单 5 条全部批内承载（1=CL-014 / 2=CL-009 / **3=CL-003 配套 / 4=CL-016 / 5=CL-001——批次 exit criteria「缺保护 3/4/5 验收前置」即此三条**）+ 假保护批内 3 条同 diff 修正（CL-003 反向钉死 :308 / CL-005 顺序伪装 TestNopDatavExportE2E.testConcurrencyLimitRejects :221 / CL-016 状态测试掩盖孤儿——ExportE2E cancel 场景补文件守恒断言）；P1 台账 = cra-index `ai-dev/audits/cra-index.md` live 2026-11-02 回读 5.3 P1 open 行 = **:300**[nop-file-datav-W-01（CL-014）]/:**301**[nop-file-datav-W-04（CL-009）]——**任务书所称「P1 三行」live 不成立，实为两行**（勘误 1）；P2 束 = cra-index :462 **P2-G76 行**（5.3 W-02/W-03/W-05/W-07 族束，成员 12 条 = 本批全部 P2 成员逐名列出）+ :463 P2-G77 行（5.3-CL-007 watch——**不在本批**）；5.3 计数口径 = cra-index 报告行 :102（8 工单 / P1×2 / P2×13 / P3×10 / C10 缺保护 5 / 反向钉死 1）；执行基线 @ `9ab4a0f4de`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.32 已 done 或 8.31 blocked 跳过，plan 2495–2525 均已完成或收口）。
> Related: plan 2386（completed——P1-3.8-001/002 文件记录权限与 attach/detach 归属校验批：**W-01 修复面的既有防护底座**——detach 权限校验 `ERR_FILE_NO_ATTACH_PERMISSION` 与 `nop-file.md` 记录保护节在案；本批 W-01 覆盖的是 copy 并发窗口（2386 未覆盖面），禁回退既有权限钉）；plan 2501（completed——R1 平台批：**3.2-C1-001 fail-loud 变更 commit `bafd41970c` @ 2026-10-25**（ToolManagerImpl :124 "no executor wired"）落于审计基线 2026-10-21 之后——**nop-datav ChatBI 测试线束 20 tests 恒红跨批遗留红面的因果归属**（勘误 3，Non-Goals 登记 successor 义务，本批零触碰）；plan 2502（completed——R1-07 F4 账本保留批：**CL-012/CL-013（W-06 P3 成员）的族级先例**——保留期/清理范式沿 `ai-dev/design/concurrency/ledger-retention-policy.md`，本批零承载）；**R1-01**（周期任务异常绝缘层批，batch-proposal §二.1——**CL-020 平台级统一修复的归属批**[2.1-C3-001]，本单仅接入点登记禁代做平台面）；**R2-11 1.5-MA-05 / R2-13 1.7-C5-001**（无界 findAll/扫描上界族——**W-04/CL-010/CL-021 的 R02 三范式对齐批**，引用不重开）；plan 2525（completed——R3-02 sys-dyn 批：**反向钉死同 diff 改写与条件更新纪律的直接先例**[MA-02 反向钉死/MA-01 fencing]，CL-003 跨单元 fencing 族对齐 R12 的形态参照，异单元异站点禁照抄细节）；**同号防混（§五 跨单元关联 6 组逐一列名）**：5.3-CL-003/CL-001 与 1.5/1.7-C7-001、1.6-C8-001 同族异站（终态 fencing 平台族—— cra-index 已注「平台级对齐」，修复方案引用同一条件更新模式、站点零触碰）；5.3-CL-020 与 2.1-C3-001/1.6-C8-001 同族（平台绝缘层——本体归 R1-01）；5.3-CL-010/CL-021 与 1.5-C5-001 同族（无界 findAll——R02 对齐）；5.3-CL-023 与 1.6-C3-005 弱同族（半程/幽灵窗口——独立登记）；5.3-CL-007 与 1.2-C4-004/1.1-C8-011 同族（watch 不立项）；**5.3-C5-003（CL-011，不在批）与 5.4-C5-001 同号异站零关联**（inventory-p2-c10 同号清单口径）。
> Protected Area 授权：批次 PA=「**无（nop-file-api plan-first 零触碰声明）**」（batch-proposal R3-03 行 PA 列全文已录 Source；merge-adjudication §七#4 PA 记录「nop-file-api 3f/492 本轮审计只读，CL-014 修复面在 nop-file-dao impl 叶——plan-first 内可实施」）。**头部预设（live 亲读核实成立）：14/14 成员零 api 叶触碰（nop-file-api 3 文件 / nop-datav-api 零触碰）、零 orm 模型触碰（`nop-file/model/nop-file.orm.xml` / `nop-datav/model/nop-datav.orm.xml` 零触碰）、零 `_` 生成物触碰**——载体文件共 17 个（逐名清单口径：nop-file-dao 1 + nop-datav-service 16，逐名清单见 Current Baseline 成员站点文件分布），分属 nop-file-dao 与 nop-datav-service 两 impl 叶聚合器，E3 双通道命令直达。**条件申报义务成文（禁静默扩权）**：①裁定⑦（CL-002）/裁定⑧（CL-004）如选「唯一约束」方案、或裁定⑭（CL-023）如选「notifyState 新列」方案，均触 `nop-datav/model/nop-datav.orm.xml`（+存量 DDL）→ 须按 AGENTS.md ORM plan-first 以**本 plan 追加申报 + 设计文档裁定专节 + draft review 记录 + 回归测试**四件证据链重新申报后方可实施；**头部预设三裁定均选非 orm 形态**（行锁/条件更新/进程内互斥 + 最简对账案）——设计文档须给出非 orm 形态的理由（唯一约束/新列需模型变更+存量数据预检+DDL 升级面，行锁/条件更新零模型变更，见 PA 表第 7/8/14 行）；②如任何修复需触 api 叶，同样须四件证据链追加申报；③**[R1 Major-2]裁定⑤（CL-016）备选载体面**：孤儿回收如选「nop-biz-file-core 公共接口形态」（`IFileStore` 加方法——跨聚合器公共契约叶）→ 同须四件证据链追加申报 + **nop-biz-file-core 全 impl 回归面**（该接口 impl 集合逐一名列回归）；**裁定⑤预设 Option A = 模块内 `daoFor(NopFileRecord)` + `getResourceStore()` 镜像 detachFileInSession 序列**（零公共接口变更、零扩权）为缺省形态，Option B（公共接口形态）仅在 Option A 论证不成立时经申报采纳。

## Purpose

关闭 R3-03 批的 14 项已确认 live 缺陷（P1×2 + P2×12；cra-index P1 open 两行 :300/:301 live 2026-11-02 回读全 open 且批次列=R3-03；P2-G76 行 :462 十二成员随批收敛；5.3 单元 P1 台账全部在本批收敛）。核心结果面有十四：

①**W-01 文件去重竞态原子化（P1，CL-014；C10 缺保护-1 随同 diff）**——DaoResourceFileStore 去重存储的判-删窗口：detachFileInSession「删本记录→isUniqueRef 查同 originFileId 其他记录→无引用则删物理文件」序列与 copyFile「读记录→插新记录（共享 filePath/originFileId）」并发交错——T1 判定"唯一引用"时 T2 新记录未提交不可见 → 物理文件被删 → T2 提交的新记录永久悬挂（下载返回不存在 resource）；附件数据丢失不可恢复；P1-3.8-002（plan 2386）补了 attach/detach 权限与会话归属，未覆盖 copy 并发窗口。

②**W-04 查询上界 R02 三范式（P1，CL-009 主；CL-010/CL-021 对齐成员；C10 缺保护-2 随同 diff）**——getDashboardData/getPanelData 主查询路径 rowLimit=null 全量物化：任一面板数据集返回大结果集即确定性 OOM 通道（50 面板逐个执行、并行度 4 放大）；模块已有四个显式行上界先例（export 100k / report 100k / alert 1k / chatbi 1k）唯主查询路径缺位——merge-adjudication §四#2 裁定 javadoc「null 表示不限制」为**路径注记而非包络声明**，P1 维持。

③**CL-003 终态行多写者无 fencing（P2，W-02 主；反向钉死同 diff 改写；跨单元 fencing 族对齐 R12）**——导出/交付状态行 worker/cancel/stuck-scanner/recovery 四方 last-writer-wins：executeTask 写 SUCCEEDED 前仅查 cancelFlags 不查 DB 当前状态（scanner 误标 FAILED 被"复活"）；runDeliveryInSession 加载后不检查终态即置 RUNNING（积压交付复活执行——重复发送邮件面）；**face a（scanner-vs-worker 覆盖）已被 TestNopDatavStuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed（:308）反向钉死为已声明接受语义**，保留面收敛为 face b（FAILED→RUNNING 复活）+ 终态写收口；修复须同 diff 改写该测试期望。

④**CL-001 cancelFlags 取消-完成竞态（P2；C10 缺保护-5 随同 diff）**——cancel 侧「terminal 读→put→写 CANCELLED」与 worker 侧「SUCCEEDED 写→finally remove」交错无原子性：终态覆盖（已成功交付的任务显示已取消）+ cancelFlags 条目永久残留（map 单调增长——F4 无界账本的内存变体）。

⑤**CL-016 cancel/fail-after-save 孤儿导出文件（P2；C10 假保护[状态测试掩盖孤儿]同 diff 修正）**——saveExportFile 落盘成功后 cancel-pre-SUCCEEDED 分支 fileId 丢弃不回滚、catch-FAILED 分支不引用 fileId、交付侧 updateEntityDirectly 抛错同构——NopFileRecord 行+物理文件双孤儿，无任何清理路径触达；ExportE2E cancel 测试仅断言状态不守恒文件。

⑥**CL-015 导出器临时文件失败路径泄漏（P2，W-05）**——writeCsv/writeXlsx 异常（checkCancelled 抛 NopException 穿过 IOException catch / 磁盘满）时仅关流不删临时资源；调用方 finally 只覆盖自身成功产物——失败导出在 temp 目录线性积累（百 MB 级/次）。

⑦**CL-002 发布版本号 check-then-act（P2，W-03）**——calculateNextVersion「读 max(version)+1」无唯一约束兜底无锁：并发 publishDashboard/publishScreen 同族产生重复 snapshotVersion——版本轴是回滚/最新读取的主键语义载体，回滚目标歧义。

⑧**CL-004 告警状态行并发评估（P2，W-03）**——loadOrCreateState check-then-insert 无 UK 兜底（对照 ChatBiSessionManager.nextSeq 有 UK 先例）：cron 与手动触发并发 → 重复通知（单次冷静期 2 封）+ 状态行分叉（findFirst 取行不定）。

⑨**CL-005 导出并发限额 TOCTOU（P2，W-03；C10 假保护[顺序测试伪装]同 diff 修正）**——checkConcurrencyLimit「COUNT(pending+running)→判→插」无原子机制：同用户并发提交可越过 max-concurrent-per-user（限额是防 globalWorker 池耗尽的背压手段）；testConcurrencyLimitRejects（:221）单线程顺序通过≠竞态受控。

⑩**CL-010 ChatBI 会话历史全量加载（P2，W-04；R02 对齐）**——loadMessages 无 LIMIT 全量载入 2N 条消息（含历史 resultJson 大列），注入侧双上界（max-turns/max-chars）裁剪发生在全量加载**之后**——每请求内存与耗时随会话长度线性增长。

⑪**CL-021 快照历史全列全量（P2，W-04；R02 对齐）**——getScreenSnapshotHistory 经 findAllSnapshots 无 LIMIT 整行加载 N 条快照（含 SNAPSHOT_CONTENT 大列）仅消费三元数据字段；对照 Dashboard 侧同类查询全部 setLimit(1)——族内防护不对称。

⑫**CL-019 调度 job 注销非事务副作用（P2，W-07）**——handleDeleteCascade「disableReportTasks（含 unregisterTask 进程内状态）→revokeShares/deleteByQuery」序列：注销成功后其余步骤失败 → 事务回滚 DB 行 ENABLED 而 cron job 已消失 → 定时报告**静默停摆**；对称窗口在 disableAlertRulesForPanels 两个调用面 + ReportTask doDeleteEntity——模块内 AR-2 afterCommit 先例（submitExecutionAfterCommit）未复用（merge-adjudication §七#2 范式一致性修复面）。

⑬**CL-020 周期任务注册失败永久停摆（P2，W-07；平台修复归 R1-01 接入点登记）**——stuck-scanner/report/alert 调度器与 recovery 双 bean 的 init 注册失败仅记日志：无重试、无健康位、无补注册——恢复基础设施单点静默缺失，进程生命周期不运行；**平台级统一绝缘层归 R1-01（2.1-C3-001），本单仅最小面修复+接入点登记**。

⑭**CL-023 交付两段式 SMTP 半程窗口（P2，W-07）**——runDelivery Part A（SUCCEEDED 事务提交）与 Part B（sendReport SMTP）之间进程死亡：交付记录显示成功、邮件永未发出、无重发/对账机制（recovery 只处理非终态行，SUCCEEDED 明确跳过）；对照告警侧 rearm 自愈先例（P1-06）报告交付侧无等价机制。

批末 roadmap 8.33 → done，cra-index P1 两行收敛 + P2-G76 行收敛注随本体同 diff 落地；nop-datav owner doc 缺口声明以**新建 `docs-for-ai/03-modules/nop-datav.md`** 兑现（merge-adjudication §九 SSOT 义务）。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.3 R3-03 = 14 项成员（P1×2：W-01[CL-014]/W-04[CL-009 主]；P2×12：CL-001/CL-002/CL-003/CL-004/CL-005/CL-010/CL-015/CL-016/CL-019/CL-020/CL-021/CL-023——P2-G76 行 :462 十二成员逐名列出与本批完全一致）；cra-index live 2026-11-02 回读 5.3 P1 open 两行 = :300/:301，P2-G76 行 :462，P2-G77 行 :463（CL-007 watch 零触碰）；roadmap :175 8.33 行 Status=todo。依赖核对（live 2026-11-02）：**无前置（可并行）**；W-04/CL-010/CL-021 的 R02 三范式与 1.5-MA-05/1.7-C5-001 批次为「对齐注记」非硬依赖；CL-020 平台修复归 R1-01（本单接入点登记）。
- **工单-成员映射（batch finding 清单混合工单级与 CL 级 ID——勘误 6 粒度声明；P2-G76 十二成员清单为权威交叉验证）**：W-01=CL-014；W-04=CL-009（P1 锚）+CL-010+CL-021（显式单列成员）；W-02=CL-003+CL-001+CL-016；W-03=CL-002+CL-004+CL-005；W-05=CL-015；W-07=CL-019+CL-020+CL-023。W-02/W-03/W-04/W-05/W-07 各工单的 P3 成员（CL-006/CL-008/CL-011/CL-012/CL-013/CL-017/CL-018/CL-022/CL-024/CL-025）与 watch（CL-007）均**不在 finding 清单**→ Non-Goals 登记去向（勘误 6）。
- **成员站点文件分布（live 亲读；逐名清单口径——载体共 17：datav 16 + file 1）**：nop-file-dao 1 文件（DaoResourceFileStore，469 行）+ nop-datav-service 16 文件（NopDatavExportTaskBizModel 611 行[承载 CL-003/CL-001/CL-016/CL-005 四成员——diff 分组登记防混]/NopDatavDashboardBizModel 976 行[承载 CL-002/CL-009/CL-019 三成员——同前]/ReportDeliveryExecutor 556 行[承载 CL-003 交付侧/CL-016 同族/CL-023]/NopDatavScreenBizModel 446 行[CL-002 同族/CL-021]/AlertEvaluator 460 行[CL-004]/ChatBiSessionManager 286 行[CL-010]/NopDatavChatBiBizModel 525 行[CL-010 调用方]/PanelDataBinder 215 行[CL-009]/PanelDataExporter 359 行[CL-015]/NopDatavPanelBizModel 180 行[CL-009 调用方]/NopDatavReportTaskBizModel 215 行[CL-019]/NopDatavReportScheduler[CL-019/CL-020]/NopDatavStuckTaskScanner[CL-020]/NopDatavAlertScheduler[CL-020]/NopDatavExportTaskRecovery/NopDatavReportDeliveryRecovery[CL-003 scanner 面]——**载体文件计数统一逐名清单口径：nop-datav-service 16 文件（上列逐名清单）+ nop-file-dao 1 文件 = 共 17 载体文件，PA 表按本清单计）；`NopDatavErrors.java` 为错误码源注册面（W-04 新错误码落点）；`NopDatavConfigs.java` 为配置键源（W-04 新上界键落点，四个限额先例 :17/:35/:51-53/:67 在案）。
- **C10 验收前置入批集（映射 SSOT = `C10-test-protection.md` 矩阵 :23-49 + 反向钉死 :51-53 + 补测清单 :65-71；随本体同 diff 落地[E2]）**：本批 14 成员对应补测清单 5 条全承载（缺保护 3/4/5 = CL-003 配套/CL-016/CL-001 为批次 exit criteria 验收前置；1/2 = CL-014/CL-009 随本体）+ 假保护批内 3 条同 diff 修正（CL-003 反向钉死 / CL-005 顺序伪装 / CL-016 状态掩盖）；时序敏感 0（模块 fake clock/latch/seam 范式健康——C10 :55-59，**无时序敏感搭车项**，对照 2525 有 2 处）；泄漏检测钩子 0（C10 :61-63——组 B 补 @AfterEach temp 清空+NopFileRecord 行数守恒形态，为模块首例）；部分保护 CL-007（Semaphore 占池）watch 零承载。
- **批前已收口面（live 形态登记，零触碰）**：P1-3.8-001/002（plan 2386）attach/detach 权限校验+CRUD 保护列（`nop-file.md` :230-236 节在案）——W-01 修复不得回退该防护；ChatBiSessionManager.nextSeq UK 先例（CL-008 对照）；AlertEvaluator P1-06 afterCommit+rearm 契约（CL-023 自愈对照先例）；Dim14-02 SMTP 移出 session（CL-023 正向窗口已修，反向窗口即本缺陷）；P1-04 clampMaxRows（chatbi.max-rows 有界对照）；nop-file.md owner doc 在案（281 行）——本批 E5 增补去重/并发契约节；nop-datav 无 owner doc（94% 行数无 owner——merge-adjudication §九欠账，module-groups :17/:74 仅列 nop-file）——本批 E5 **新建**。
- **平台设施与测试资产盘点（live 实读 @ `9ab4a0f4de` + 基线亲跑）**：
  - **基线测试结果（2026-11-02 亲跑，勘误 3/4 全记录）**：r1 分解通道 `./mvnw test -pl nop-datav,nop-file`（无 `-am`）：**nop-file 17 tests 0F0E**（nop-file-dao 2 类 9 tests + nop-file-service 2 类 7 tests + nop-file-web 1 类 1 test 全绿；`_tmp/baseline-2526-decomposed.log`）+ **nop-datav 630 tests 中 20 red（4F+16E）**——8 个测试类全部 ChatBI 工具链（TestDatavGenerateDashboardExecutor/TestDatavGenerateScreenExecutor/TestNopDatavChatBiDashboardE2E/TestNopDatavChatBiE2E/TestNopDatavChatBiScreenAction/TestNopDatavChatBiScreenE2E/TestNopDatavChatBiSessionE2E/TestNopDatavChatBiTransactionBoundary），根因 = plan 2501 fail-loud 变更（`bafd41970c`，ToolManagerImpl :124）跨批遗留——**恒红基线红面，零成员站点关联**（solo 复跑恒红确认：TestNopDatavChatBiTransactionBoundary solo 1F+2E 同签名，`_tmp/baseline-2526-chatbi-solo.log`；勘误 3）→ **零回退钉清单 = nop-file 17 tests + nop-datav 非 ChatBI 红面 610 tests 0F0E**；双 `-am` 通道至首个上游阻断面 r2/r3 实录登记（勘误 5——r2 阻断于 nop-auth-service TestOperationMfaE2E TOTP 恒红面③，solo 复跑恒红确认）。
  - **测试资产（live 核实）**：nop-datav **74 个测试类全部在 nop-datav-service**（api/dao/meta/web 无测试）；nop-file **5 个测试类**（nop-file-dao 2：TestDaoResourceFileStoreFileHash/TestDaoResourceFileStoreAttachPermission[plan 2386 权限钉——W-01 修复零回退]；nop-file-service 2：TestNopFileRecordBizModelAssembly/TestNopFileRecordUpdateProtectedProps；nop-file-web 1：NopFileWebPagesTest）。既有并发测试 10 类保护力见 C10 盘点表（ParallelQuery/QueryCache/AsyncSubmit/ShareRateLimit/StuckTaskScan/ExportE2E/AlertNotify/ChatBiTransactionBoundary/PanelDataExporter×2/ReportE2E/ChatBiSessionManagement/DeleteLifecycle——全部 live 在案）；缺保护=坏路径而非好路径（C10 :72 结构性发现——本批补钉全部落坏路径）。
  - **owner doc 现状（2026-11-02 亲读）**：`docs-for-ai/03-modules/nop-file.md`（281 行）在案，含记录保护节（:230-236）与源码锚点表——本批 E5 落点：增补「去重存储并发契约」节（引用计数/物理删除原子性语义 + 两段式清理契约）；`docs-for-ai/03-modules/nop-datav.md` **不存在**（缺口声明）——本批 E5 **新建** owner doc（上界配置族/导出交付状态机并发契约/调度生命周期契约最小集，落 W-04 新配置键与错误码声明）；`docs-for-ai/04-reference/source-anchors.md` 新增锚点行（FILE-CC-001 / DATAV-CC-001，命名沿 SYS-CC-001/DYN-CC-001 既有惯例，插入点按表序实际行号 live 亲读裁定点）；`docs-for-ai/01-repo-map/module-groups.md` :17 可复用业务模块行与 :74 nop-file 行核对（nop-datav 是否补路由——live 核对后最小同步）。

### 对齐纪律显式条款

- **CL-003 反向钉死同 diff 改写 + face a/b 边界（批次 exit criteria 首条）**：`TestNopDatavStuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed`（:308）把 scanner 假阳性覆盖钉为"final state correct"（face a 豁免——已声明接受）——终态条件更新修复**必须同 diff 改写期望终态**（scanner FAILED 保持 + worker 条件写 0 行可观测[结构化冲突日志]，C10 补测-3 建议形态），否则修复无法落地；face b（FAILED→RUNNING 复活，积压场景可达）为保留修复面；**跨单元 fencing 族对齐 R12**：条件更新纪律沿 cra-index WI1.5/WI1.7 C7-001 / WI1.6 C8-001 平台族与 plan 2511/2525 先例**引用不重开**，站点细节（状态行实体形态/取消语义）禁照抄先例实现。
- **CL-005 假保护同 diff 修正**：`TestNopDatavExportE2E.testConcurrencyLimitRejects`（:221）单线程顺序测试伪装竞态保护——修复同 diff 补并发交错断言（或并发提交形态重写），禁只修代码不修测试（C10 矩阵行口径）。
- **W-01 去重竞态原子化（批次 exit criteria 第三条）+ CL-017 边界**：两段式延迟清理（isUniqueRef 命中后登记待清理→延迟任务复验引用后删）或 originFileId 维度应用层互斥（平台 IResourceLockManager 使用面）两案一次定夺禁第三形态（裁定①域）；`removeResource`（:262-270，CL-017 P3 站点）best-effort 语义**零触碰**——W-01 两段式形态不得顺手改 removeResource 的失败处理（CL-017 去向登记）；plan 2386 attach/detach 权限钉零回退。
- **W-04 R02 三范式（批次 exit criteria 第四条）**：行上界（CL-009 新配置 `nop.datav.dashboard-query.max-rows-per-panel`，对齐既有四限额先例与导出错误码语义）+ 列投影或分页（CL-021 一次定夺）+ 加载侧 LIMIT（CL-010 按 max-turns 推导）三范式批内一次成文；与 1.5-MA-05/1.7-C5-001 批次对齐（同族无界 findAll——范式引用不重开）；CL-009 修复为**补齐惯例而非新造机制**（merge-adjudication §七#1——不对称性即缺陷画像）；`NopDatavChatBiBizModel.getChatSessionHistory` 全量返回为用户可见用途（audit 建议保留+分页参数——是否分页由裁定⑩定夺禁第三形态）。
- **CL-020 接入点登记（R1-01 边界）**：本单最小面 = 注册失败启动延迟重试（平台定时器一次重试）**或** scanRegistered 健康位指标——两案一次定夺禁第三形态（裁定⑬域）；**平台级统一绝缘层（29 站点 catch Throwable+completeExceptionally+重臂）归 R1-01（2.1-C3-001）**，本单禁实现平台级方案、禁触 nop-kernel；接入点清单（scanner/report/alert/recovery×2 五站点）随批登记供 R1-01 消费。
- **CL-019 AR-2 先例复用（范式一致性）**：unregister/registerTask 副作用后置 `transactionTemplate.afterCommit`（模块内先例 `submitExecutionAfterCommit` :261-267 在案）或失败补偿两案一次定夺（裁定⑫域）——merge-adjudication §七#2「同一模块内范式一致性是低成本高收益修复面」；失效面枚举闭合（disableReportTasksForDashboard/disableAlertRulesForPanels×2 调用面/doDeleteEntity×2 同族同修）。
- **CL-002/CL-004/CL-023 非 orm 预设（PA=无 延伸条款）**：三裁定的"唯一约束/新列"备选形态触 `nop-datav/model/nop-datav.orm.xml`——**头部预设裁定⑦（行锁 SELECT FOR UPDATE 或条件更新+重试）/裁定⑧（条件更新乐观 fencing 或进程内互斥）/裁定⑭（最简对账 WARN 清单）均选非 orm 形态**；设计文档须给出非 orm 形态理由（模型变更+DDL+存量数据预检代价 vs 行锁/条件更新零模型变更）；如实施期改选 orm 形态须四件证据链追加申报（头部 Protected Area 段②条款）。
- **CL-001 与 CL-003 同收口通道 + CL-016 同 diff 交付**：cancel 终态写（CL-001）与 worker 终态写（CL-003）统一为同一条件更新形态（`where status in non-terminal`）——禁同文件两套 fencing 形态；CL-016 回收动作（catch/cancel 分支 fileStore 侧回收，镜像 detachFileInSession 序列）与 CL-001 的 cancel 分支改造同 diff 交付（同文件同方法族）；CL-015（PanelDataExporter）与 CL-016（导出产物守恒）同导出链分裁定交付。
- **同变更集绑定（C10 随本体，E2）**：缺保护 5 条 + 假保护 3 条与各自本体同 diff 落地，禁止先修本体后补测的拆分交付；本批无时序敏感搭车项（C10 时序敏感 0——勘误 4 口径声明）。
- **硬序条款**：本批无跨批硬依赖（无前置，可并行）；批内软序 = CL-003 收口先行于 CL-001（同文件同收口通道基线清楚）→ CL-016（依赖 cancel 分支形态）；组 D 三成员（CL-009→CL-010→CL-021）按 R02 范式同批成文；每成员先红后绿或变异/回退论证（E2）。

### 14 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `9ab4a0f4de` 2026-11-02 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；工单；C10 面） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 5.3-W-01（P1；W-01；C10 缺保护-1[补测清单#1]） | `DaoResourceFileStore.java`（nop-file-dao store） | `detachFileInSession` :373（:381 deleteEntity / :382 isUniqueRef / :383 removeResource 判-删序列）；`isUniqueRef` :392-401；`copyFile` :442-460（:444 requireEntityById 读记录 / :449 setOriginFileId / :453 setFilePath 共享路径）；`removeTempFileByOwner` :156（:165 deleteEntity / :167 isUniqueRef / :168 removeResource 同模式——审计 :164-170 微漂移[勘误 2]）；`removeResource` :262-270（CL-017 P3 站点**零触碰**） | 物理文件去重 check-then-act：detach 判"唯一引用"与 copy 插新记录并发交错——引用计数过期判删 → 新记录永久悬挂（下载 404）或提前物理删除，附件数据丢失不可恢复；经 OrmFileComponent copyFrom 实体复制流可达 | 裁定①（缺保护-1 随同 diff）：原子化——两段式延迟清理（登记待清理→延迟复验后删）或 originFileId 互斥（IResourceLockManager 使用面）两案一次定夺；removeTempFileByOwner 同族同修；plan 2386 权限钉零回退；CL-017 removeResource 语义零触碰登记 |
| 2 | 5.3-W-04（P1；W-04 主；C10 缺保护-2[补测清单#2]；R02 三范式与 1.5-MA-05/1.7-C5-001 对齐） | `PanelDataBinder.java`（nop-datav-service query）+ `NopDatavDashboardBizModel.java`、`NopDatavPanelBizModel.java`（entity，调用方）+ `NopDatavConfigs.java`/`NopDatavErrors.java`（新键/新错误码落点） | javadoc「null 表示不限制」:81（§四#2 裁定=路径注记非包络声明）；`queryPanelData` 重载 :83-85/:100-101；rowLimit==null 分支 :175（range=null 流式游标）；`extractRows` :207-214 全量物化；调用方 :583（Dashboard 批量传 null）/:136/:150（Panel 同族传 null） | 主查询路径 rowLimit=null 无行数上界：大结果集确定性 OOM 通道（50 面板×并行度 4 放大）；四限额先例（export/report/alert/chatbi）在案唯主路径缺位 | 裁定②（缺保护-2 随同 diff）：新配置 `nop.datav.dashboard-query.max-rows-per-panel`（默认值由裁定②定夺，audit 建议 10k）；三站点传 rowLimit；超限显式抛 `ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED`（对齐导出 ERR 语义——`NopDatavErrors.java` 注册）；R02 行上界范式成文 |
| 3 | 5.3-CL-003（P2；W-02 主；C10 假保护[反向钉死]——TestNopDatavStuckTaskScan :308 同 diff 改写；fencing 族对齐 R12） | `NopDatavExportTaskBizModel.java`（entity）+ `ReportDeliveryExecutor.java`（report）+ `NopDatavExportTaskRecovery.java`、`NopDatavReportDeliveryRecovery.java`（export/report recovery）+ 反向钉死 `TestNopDatavStuckTaskScan.java`（test） | `executeTask` :284-370（:335 saveExportFile / :339 仅查 cancelFlags / :344-345 SUCCEEDED 写无 DB 状态重验）；`runDeliveryInSession` :215-293（:249-251 RUNNING 写加载后无终态复验 / :271/:275 SUCCEEDED）；export recovery doScanStuck :112-133（:125 FAILED/:129 update）；delivery recovery doScanStuck :108-131（:122/:127）；反向钉死 :308 | 终态行四方多写者 last-writer-wins：scanner 误标 FAILED 被复活；积压交付复活执行（重复发送邮件面）；终态转换无条件守卫（markFailedSafe 的 isTerminal 守卫为同族已修对照） | 裁定③（反向钉死改写随同 diff）：终态写收口条件更新（`where status in non-terminal`，按影响行数判定）+ 执行体加载后终态短路（镜像 markFailedSafe）；**0 行放弃分支的 fileId 处置条款[R1 Major-1]**：条件更新 0 行（已被 scanner/cancel 抢先定终态）时已落盘 fileId 不得静默丢弃——回收或带 fileId 落 FAILED 二选一入裁定域（与裁定⑤合流，Phase 1 一次定夺）；**反向钉死 :308 同 diff 改写**（scanner FAILED 保持+worker 0 行可观测）；R12 族纪律引用不重开 |
| 4 | 5.3-CL-001（P2；W-02；C10 缺保护-5[补测清单#5]） | `NopDatavExportTaskBizModel.java`（entity——与 CL-003 同文件，同收口通道） | `cancelExportTask` :225-233（:225 isTerminal 读 / :228 cancelFlags.put / :230 setStatus CANCELLED——读-写非原子）；`executeTask` finally `cancelFlags.remove` :367（worker 退出后无兜底清理） | cancel-完成竞态：T1 写终态后 T2 put+写 CANCELLED 覆盖 SUCCEEDED（交付物已产生状态却显示取消）+ cancelFlags 条目永久残留（map 单调增长） | 裁定④（缺保护-5 随同 diff）：cancel 侧 putIfAbsent + 终态写并入裁定③条件更新收口（同文件同形态禁两套 fencing）；残留兜底（executeTask 收尾 remove 语义收口或 flag 挂 DB 行——裁定④定夺）；executionStartHook seam（:138）为测试交错同步点（seam 本体 CL-008 P3 零触碰） |
| 5 | 5.3-CL-016（P2；W-02；C10 假保护[状态测试掩盖孤儿]同 diff 修正） | `NopDatavExportTaskBizModel.java`（entity）+ `ReportDeliveryExecutor.java`（report） | `executeTask` :335-349（:339-342 cancel-pre-SUCCEEDED fileId 丢弃不回滚）+ catch :352-358（FAILED 写不引用 fileId）；交付侧 :266-292（:275 updateEntityDirectly 抛错 fileId 丢弃）；`removeTempFileByOwner` 仅清 TEMP_BIZ_OBJ_ID（孤儿记录不可枚举） | cancel/fail-after-save 孤儿导出文件：NopFileRecord 行+物理文件双孤儿永久不可达（百 MB 级/次累积）；ExportE2E cancel 测试仅断言状态——状态测试掩盖孤儿 | 裁定⑤（假保护修正随同 diff）：catch/cancel 分支补 fileStore 侧回收——**预设 Option A = 模块内 `daoFor(NopFileRecord)` + `getResourceStore()` 镜像 detachFileInSession 序列**（fileId 尚无共享引用删除安全、零公共接口变更非扩权缺省[R1 Major-2]；公共接口形态[IFileStore 加方法]须四件证据链追加申报+nop-biz-file-core 全 impl 回归面）；**与裁定③ 0 行放弃分支合流[R1 Major-1]**：0 行时已落盘 fileId 按裁定③处置条款回收或带 fileId 落 FAILED（一次定夺禁第三形态）；交付侧 :275 同族同修；ExportE2E cancel 场景补 NopFileRecord 行数守恒+物理文件不存在断言（与 CL-001 缺保护-5 钉合流） |
| 6 | 5.3-CL-015（P2；W-05；C10 缺保护[矩阵行 CL-015 无测试]） | `PanelDataExporter.java`（export） | `writeCsv` :215-241（:218 getTempResource / :235 NopException.adapt 后无 delete / :237-238 finally 只关流）；`writeXlsx(singleSheet)` :243-246 委托；`writeXlsx(workbook)` :249-258（:250 getTempResource / :254 throw 后无 delete）；`checkCancelled` :90（:229 抛点——RuntimeException 穿过 IOException catch） | 导出临时文件失败路径泄漏：成功路径由调用方 finally 删除、失败路径无任何删除——temp 目录线性积累（磁盘耗尽候选）；`ResourceHelper.getTempResource` 无自动回收契约 | 裁定⑥：writeCsv/writeXlsx 收口两案一次定夺（catch→resource.delete() 后 rethrow 或职责内 finally delete——裁定⑥域）；writeXlsx 两重载同修；泄漏钉=@AfterEach temp 清空断言形态（C10 泄漏钩子缺口模块首例） |
| 7 | 5.3-CL-002（P2；W-03；C10 缺保护[无并发 publish 测试]） | `NopDatavDashboardBizModel.java` + `NopDatavScreenBizModel.java`（entity——同族两 BizModel 同 diff） | `calculateNextVersion`（Dashboard）:920-928（:926 findFirstByQuery 读 max——审计 :925 微漂移[勘误 2]）；`calculateNextVersion`（Screen）:356-364（:360 setLimit(1)）；publishDashboard :229 / publishScreen :108 | 发布版本号读 max+1 无唯一约束兜底无锁：并发发布同 dashboard/screen 重复 snapshotVersion → 回滚/最新读取按 limit(1) 取行歧义（主键语义载体违约） | 裁定⑦：**非 orm 预设**——行锁（SELECT FOR UPDATE 语义的 dao 条件面）或条件更新+冲突重读重试两案一次定夺；Dashboard+Screen 同族同 diff；如选唯一约束形态触 nop-datav.orm.xml 须四件证据链追加申报（PA 表第 7 行） |
| 8 | 5.3-CL-004（P2；W-03；C10 缺保护[无双触发并发测试]） | `AlertEvaluator.java`（alert） | `evaluate` :121（cron+manual 双入口）；`loadOrCreateState` :278-289（:283 findFirstByQuery / :288 createInitialState check-then-insert——对照 ChatBiSessionManager.nextSeq UK 先例无兜底）；rearm 判定读 lastNotifiedTime :235-248 区（:236-248 旧值并发判定） | 告警状态行并发评估：单次冷静期窗口重复通知（值班疲劳）+ 建行竞态状态机分叉（findFirst 取行不定） | 裁定⑧：**非 orm 预设**——rearm 回写条件更新（`where last_notified_time=旧值` 乐观 fencing）+ 建行竞态以条件更新冲突重读或进程内互斥（IResourceLockManager 按 ruleId）收口，组合一次定夺；**谓词枚举含 NULL（首通知）分支覆盖[R1 Minor-5——AlertEvaluator :195-197 注释自证失败保持 null，谓词须显式覆盖 NULL→已通知迁移]**；AlertRuleBizModel.initializeStateIfAbsent 建行同族核对；如选唯一约束形态须四件证据链追加申报（PA 表第 8 行） |
| 9 | 5.3-CL-005（P2；W-03；C10 假保护[顺序测试伪装]——TestNopDatavExportE2E :221 同 diff 修正） | `NopDatavExportTaskBizModel.java`（entity——与 CL-003/CL-001/CL-016 同文件）+ 假保护载体 `TestNopDatavExportE2E.java`（test） | `checkConcurrencyLimit` :497-513（:507 countByQuery COUNT(pending+running)）；`createExportTask` :166 校验调用 / :174 saveEntityDirectly 插入——校验与插入无原子机制（javadoc 仅声明执行线程未声明原子性） | 导出并发限额 TOCTOU：同用户并发提交越过 max-concurrent-per-user（背压手段失效，突发 N 并发至多超限 N-1）；单线程顺序测试 :221 伪装竞态保护 | 裁定⑨（假保护修正随同 diff）：per-user 进程内 ISemaphore（io.nop.commons.concurrent.semaphore 平台设施**使用面**）与 DB count 校验叠加（audit 建议前案）或显式声明非原子上界+javadoc——两案一次定夺；:221 并发交错断言同 diff 补齐 |
| 10 | 5.3-CL-010（P2；W-04；C10 缺保护[加载侧无界无断言]；R02 对齐） | `ChatBiSessionManager.java`（chatbi）+ `NopDatavChatBiBizModel.java`（entity，调用方） | `loadMessages` :124-129（:128 findAllByQuery 无 LIMIT）；`appendTurn` :138-160（resultJson 行数据每轮持久化）；调用方 chatToQuery :296-304（:296 loadMessages→:297 buildHistoryContext 裁剪后置于全量加载）/ getChatSessionHistory :352-358（全量直接返回） | ChatBI 会话历史全量加载无上界：每轮线性放大（2N 条含 resultJson 大列），注入侧双上界（max-turns/max-chars）裁剪发生在全量加载之后——长会话单请求物化数十万行等价 JSON | 裁定⑩（R02 加载侧 LIMIT 范式随同 diff）：loadMessages 按 max-turns 推导 LIMIT（seq 降序取最近 2×maxTurns+1，audit 建议形态）；getChatSessionHistory 分页参数与否一次定夺（用户可见用途保留全量为可选项——禁第三形态）；`listSessions` :96-101（CL-022 P3 面）零触碰登记 |
| 11 | 5.3-CL-021（P2；W-04；C10 缺保护[历史查询规模面无测试]；R02 对齐） | `NopDatavScreenBizModel.java`（entity） | `getScreenSnapshotHistory` :221-233（:225 findAllSnapshots 调用 / :228-232 仅取三元数据字段）；`findAllSnapshots` :385-390（:389 findAllByQuery 无 LIMIT 整行含 SNAPSHOT_CONTENT）；对照 Dashboard 侧 setLimit(1) :360/:370/:378（族内防护不对称） | 快照历史全列全量加载：每发布一条快照（无上限）整行载入含完整屏幕 JSON content 大列，仅消费三字段——单请求 O(ΣN×contentSize) 内存驻留 | 裁定⑪（R02 投影/分页范式随同 diff）：列投影（排除 snapshotContent）或分页参数两案一次定夺；快照保留数上界为可选项（裁定⑪域——与 CL-013 保留族去向注记，不扩张）；Dashboard 对照查询零触碰 |
| 12 | 5.3-CL-019（P2；W-07；C10 缺保护[无回滚后 job 恢复断言]） | `NopDatavDashboardBizModel.java` + `NopDatavReportTaskBizModel.java`（entity）+ `NopDatavReportScheduler.java`（report） | `handleDeleteCascade` :126-162（:134 disableReportTasksForDashboard[内 :159 unregisterTask] / :135 disableAlertRulesForPanels / :136 revokeSharesForDashboard 后续失败回滚）；`saveDashboardLayout` 面板移除面 :772；`NopDatavReportTaskBizModel.doDeleteEntity` :93-110（:74-78 save/unregister 配对）+ `unregisterTask` :185-190 | 调度 job 注销非事务副作用：注销成功后其余步骤失败→事务回滚 DB 行 ENABLED 而 cron job 已消失——定时报告/告警**静默停摆**（恢复仅靠用户再 save） | 裁定⑫：副作用后置 afterCommit（模块内先例 submitExecutionAfterCommit :261-267）或失败补偿两案一次定夺；失效面枚举闭合（disableReportTasks/disableAlertRules×2/doDeleteEntity×2 同族同修）；回滚恢复钉（回滚后 job 仍注册断言） |
| 13 | 5.3-CL-020（P2；W-07；C10 缺保护[无 init 失败→重试/健康位测试]；**平台修复归 R1-01——本单接入点登记**） | `NopDatavStuckTaskScanner.java`（recovery）+ `NopDatavReportScheduler.java`、`NopDatavAlertScheduler.java`（report/alert）+ recovery 双 bean | scanner init :91-110（:103 activate / :104 addJob / :108 register-failed catch 仅 LOG.error）；report init :116-147（:141 register-failed per-task）；alert init :110-141（:135）；recovery 双 bean init :58-65/:60-65（catch warn） | 周期任务注册失败仅记日志：无重试/无健康位/无补注册——stuck 扫描或 per-task cron 整个进程生命周期停摆（基础设施级静默缺失，F5 滞留不可见） | 裁定⑬（本单最小面）：注册失败启动延迟重试（平台定时器一次重试）**或** scanRegistered 健康位指标两案一次定夺；五站点接入点清单随批登记供 R1-01（2.1-C3-001）消费；**禁在本单实现平台级绝缘层、禁触 nop-kernel**（对齐纪律条款） |
| 14 | 5.3-CL-023（P2；W-07；C10 缺保护[无崩溃注入/对账测试]；与 1.6-C3-005 弱同族独立登记） | `ReportDeliveryExecutor.java`（report——与 CL-003/CL-016 同文件分裁定） | `runDelivery` :201-209（:203-204 Part A runInNewSession SUCCEEDED 提交 / :207 Part B sendNotificationOutOfSession——两段间进程死亡窗口）；`sendNotificationOutOfSession` :299-316（:302 sendReport / :309 失败→rollbackSucceededToFailed :340-363 进程内补偿在案）；recovery 只处理非终态行（SUCCEEDED 跳过） | 交付两段式半程窗口：Part A 提交后 JVM 死亡 → SMTP 永不执行且无重发/对账（交付记录成功、deliveredChannels 空、静默丢发）；对照告警侧 rearm 自愈先例无等价机制 | 裁定⑭：**非 orm 预设**——最简对账案（恢复扫描把 deliveredChannels 为空的 SUCCEEDED 行列对账清单输出 WARN，零模型变更）**或** notifyState 两态列方案（触 nop-datav.orm.xml 须四件证据链追加申报——PA 表第 14 行）两案一次定夺；对照 rearm 自愈先例论证自愈语义取舍 |

### PA 判定表（14 成员逐一，orm/_生成物/平台设施/api 叶触碰与否；live 2026-11-02 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触 nop-api-core/nop-kernel/nop-ai 平台设施？ | 触 nop-file-api/nop-datav-api？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|---|
| 1 | W-01 | nop-file-dao（store） | 判-删序列原子化 | 否 | 否 | 否（IResourceLockManager 为**使用面**注入——两案定夺后仍零平台修改） | 否（nop-file-api 3f 零触碰——§七#4 PA 记录） | 纯 Java 修复 |
| 2 | W-04 | nop-datav-service（query+entity+configs/errors） | rowLimit 传入+新配置键+新错误码 | 否 | 否 | 否 | 否 | 纯 Java 修复；配置键/错误码落 `NopDatavConfigs.java`/`NopDatavErrors.java` 源注册面（非生成物） |
| 3 | CL-003 | nop-datav-service（entity+report+recovery×2+test 反向钉死改写） | 终态条件更新+短路 | 否 | 否 | 否 | 否 | 纯 Java 修复；test 树内断言改写非生成物 |
| 4 | CL-001 | nop-datav-service（entity） | putIfAbsent+条件更新收口 | 否 | 否 | 否 | 否 | 纯 Java 修复 |
| 5 | CL-016 | nop-datav-service（entity+report） | 失败/cancel 分支文件回收 | 否 | 否 | **预设否**——Option A 模块内 daoFor+getResourceStore() 零公共接口变更[R1 Major-2 缺省]；**条件申报**：Option B = `IFileStore`（nop-biz-file-core 公共接口）加方法须四件证据链追加申报+nop-biz-file-core 全 impl 回归面 | 否 | 纯 Java 修复（预设 Option A 形态） |
| 6 | CL-015 | nop-datav-service（export） | 失败路径 delete 收口 | 否 | 否 | 否（ResourceHelper 使用面） | 否 | 纯 Java 修复 |
| 7 | CL-002 | nop-datav-service（entity×2） | 行锁/条件更新+重试 | **预设否**——头部预设行锁/条件更新形态；**条件申报**：唯一约束形态触 `nop-datav/model/nop-datav.orm.xml` 须 ORM plan-first 四件证据链追加申报 | 否（预设形态零生成物） | 否 | 否 | 纯 Java 修复（预设形态） |
| 8 | CL-004 | nop-datav-service（alert） | 条件更新乐观 fencing/进程内互斥 | **预设否**——同第 7 行条件申报条款 | 否 | 否（IResourceLockManager 使用面——若采纳） | 否 | 纯 Java 修复（预设形态） |
| 9 | CL-005 | nop-datav-service（entity+test 假保护修正） | ISemaphore 叠加+并发断言 | 否 | 否 | 否（`io.nop.commons.concurrent.semaphore.ISemaphore` 为**使用面**引用——零平台修改，audit 明文口径） | 否 | 纯 Java 修复 |
| 10 | CL-010 | nop-datav-service（chatbi+entity） | 加载侧 LIMIT | 否 | 否 | 否 | 否 | 纯 Java 修复 |
| 11 | CL-021 | nop-datav-service（entity） | 列投影/分页 | 否（Nop ORM 投影走 QueryBean 声明面，零模型变更） | 否 | 否 | 否 | 纯 Java 修复 |
| 12 | CL-019 | nop-datav-service（entity×2+report scheduler） | afterCommit 后置/补偿 | 否 | 否 | 否（transactionTemplate 为平台**使用面**） | 否 | 纯 Java 修复 |
| 13 | CL-020 | nop-datav-service（recovery+scheduler×2） | 重试/健康位 | 否 | 否 | 否（平台定时器/CoreMetrics 为使用面；**平台级绝缘层本体归 R1-01 零触碰**） | 否 | 纯 Java 修复 |
| 14 | CL-023 | nop-datav-service（report） | 对账 WARN/notifyState 两案 | **预设否**——最简对账案零模型；**条件申报**：notifyState 新列形态触 `nop-datav/model/nop-datav.orm.xml`（+存量 DDL）须四件证据链追加申报 | 否 | 否 | 否 | 纯 Java 修复（预设形态） |

**结论：头部 14/14 成员零 `_` 生成物触碰、零 api 叶触碰、零平台设施修改（ISemaphore/IResourceLockManager/IJobScheduler/transactionTemplate/CoreMetrics 均为使用面引用）；零 orm 模型触碰为头部预设（裁定⑦⑧⑭三处条件申报条款在案，预设全部选非 orm 形态）。站点分布于 nop-file（dao 1 impl 叶）与 nop-datav（service 1 impl 叶承载 16 文件——逐名清单口径，见 Current Baseline）——两聚合器，E3 双通道命令直达零盲区。**

### Baseline 勘误（输入不一致登记）

1. **P1 台账行数勘误（任务书层）**：起草任务书所称「5.3 P1 三行 open」live 不成立——cra-index live 2026-11-02 回读 5.3 P1 台账 = **两行 open**（:300 W-01/:301 W-04），无第三行。另发现 :298/:299 为 **plan 2525 收口遗留的 5.2 I-01/I-02 重复行**（fixed 版本已在 :296/:297，旧 open 行未删——`9ab4a0f4de` 插入 fixed 行时残留，git blame 亲证）——cra-index ledger 缺陷登记，非 5.3 面非本批成员；**清理义务登记**：归本批 Phase 4 台账回填时顺带处置（删除两重复行，登记于 Phase 4 checklist）或独立 doc-fix，禁静默留存。
2. **成员行号漂移勘误（审计基线 2026-10-21 → live `9ab4a0f4de`；全部为行号微漂移或零漂移，缺陷机制零漂移）**：`DaoResourceFileStore` detachFileInSession :373（deleteEntity :381/isUniqueRef :382/removeResource :383 与审计 :381-384 一致）、isUniqueRef :392-401 零漂移、copyFile :442-460（:444/:449/:453）零漂移、removeResource :262-270 零漂移、removeTempFileByOwner 审计 :164-170 → live 方法体 :156 起（deleteEntity :165/isUniqueRef :167/removeResource :168——审计引用行段整体前移 8 行）；`PanelDataBinder` javadoc :81/queryPanelData :83-85/:100-101/rowLimit 分支 :175/extractRows :207-214 零漂移；`NopDatavDashboardBizModel` newPanelQueryTask :583/handleDeleteCascade :126-162（:134/:135/:136/:159）/saveDashboardLayout 面 :772/calculateNextVersion :920-928（findFirstByQuery 审计 :925 → live :926 微漂移）/parseFilterFromUrl requireEntity :314（getEntityById 审计 :319 → live :320 微漂移[CL-006 关联面，不在批]）零机制漂移；`NopDatavPanelBizModel` :136/:150 零漂移；`NopDatavExportTaskBizModel` cancelFlags :119/executionStartHook :138/cancelExportTask :225-233（:225/:228/:230）/createExportTask :166/:174/checkConcurrencyLimit :497-513（:507）/executeTask :284-370（:335/:339/:344-345/:352-358/:367）全零漂移；`ReportDeliveryExecutor` runDelivery :201-209（:203-204/:207）/runDeliveryInSession :215-293（:225 waitForDeliveryRow 兜底在案/:249-251/:266/:271/:275）/sendNotificationOutOfSession :299-316（:302/:309）/rollbackSucceededToFailed :340-363 全零漂移；`NopDatavScreenBizModel` getScreenSnapshotHistory :221-233（:225）零漂移、calculateNextVersion :356/setLimit(1) :360/:370/:378、findAllSnapshots :385-390 零漂移；`AlertEvaluator` evaluate :121/loadOrCreateState :278-289（:283/:288）零漂移；`ChatBiSessionManager` listSessions :96-101/loadMessages :124-129（:128）/appendTurn :138 零漂移；`NopDatavChatBiBizModel` chatToQuery :296-304/getChatSessionHistory :352-358 零漂移；`PanelDataExporter` checkCancelled :90/writeCsv :215-241（:218/:229/:235/:237-238）/writeXlsx×2 :243-258（:250/:254）零漂移；`NopDatavReportTaskBizModel` doDeleteEntity :93-110（:74-78）零漂移；`NopDatavReportScheduler` init :116-147（:141）/unregisterTask :185-190 零漂移；`NopDatavStuckTaskScanner` init :91-110（:103/:104/:108）零漂移；`NopDatavAlertScheduler` init :110-141（:135）零漂移；recovery 双 bean（export :58-65/:112-133/:125/:129；report :58-65/:108-131/:122/:127）零漂移；反向钉死 `TestNopDatavStuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed` live **:308**（审计未给行号——本行号为起草期亲读补齐）；假保护 `TestNopDatavExportE2E.testConcurrencyLimitRejects` live **:221**（同前补齐）；`NopDatavConfigs` 四限额先例 :17/:35/:51-53/:67 与 `NopDatavErrors` 注册面 live 亲读在案。
3. **计划外基线红面发现——ChatBI 工具执行器接线（20 tests 恒红，跨批遗留）**：分解通道 r1（`_tmp/baseline-2526-decomposed.log`）nop-datav-service 630 tests 中 **20 red（4F+16E）**，8 个测试类全部 ChatBI 工具链——失败签名统一为 `nop.err.ai.toolkit.invalid-state: Tool execution context has no executor wired (fail-loud, previously an NPE inside dispatch)`；根因 = **plan 2501 的 3.2-C1-001 fail-loud 变更（commit `bafd41970c` @ 2026-10-25，`ToolManagerImpl.java` :124）**落于审计基线（2026-10-21）之后，nop-datav ChatBI 测试线束未按新契约接线 executor——**恒红基线红面非负载 flake**（solo 复跑同签名恒红确认：TestNopDatavChatBiTransactionBoundary solo 1F+2E，`_tmp/baseline-2526-chatbi-solo.log`）；**与 14 成员零站点关联**（成员载体零触碰 ChatBiToolCallingLoop/ToolManagerImpl；CL-010 站点 ChatBiSessionManager.loadMessages 为消息加载面非工具分发面）。处置：①本批零触碰该红面（修复线束属 nop-ai/3.2 域 successor 义务——Non-Goals 登记，禁顺手改测试线束）；②E3 验收判据以「排除该 8 类的分解通道」形态成文（Phase 3 显式项：`./mvnw test -pl nop-datav,nop-file` 全量留痕 + 20-red 面签名与勘误 3 逐字比对 + 剔除面 610 tests 0F0E 断言），沿 2525 勘误 5「等价分解通道+已知面登记」先例形态。
4. **基线测试数登记（起草期亲跑 r1 全记录）**：分解通道 `./mvnw test -pl nop-datav,nop-file`（无 `-am`）：nop-file = dao 2 类 9 tests + service 2 类 7 tests + web 1 类 1 test = **17 tests 0F0E 全绿**；nop-datav-service = **74 类 630 tests（20 red 如勘误 3）**；合计 647 tests。**零回退钉清单 = nop-file 17 tests + nop-datav 非 ChatBI 面 610 tests 0F0E**，Phase 2/3 零回退以此为钉（ChatBI 20-red 面修复期前后均应恒红——如执行期该面变绿/变红须登记归因，禁静默）。双 `-am` 通道至首个上游阻断面登记见勘误 5（r2/r3 实录）。**时序敏感搭车项口径**：本批无（C10 时序敏感 0——对照 2525 勘误 5 之 2 处搭车改写，本批不适用）。
5. **E3 双通道上游链核对与 `-am` 阻断面登记**：batch-proposal R3-03 行验证命令 = 双通道原文「`./mvnw test -pl nop-datav -am`；`./mvnw test -pl nop-file -am`」——live pom 亲读核实两聚合器均含全部载体模块（nop-file 7 子模块/nop-datav 7 子模块），双通道零盲区；上游闭包含 nop-kernel 全链/nop-core/nop-auth-service/nop-stream 面（与 2525 勘误 5 同一上游已知 flake 面集——nop-core TestResourceCacheInvalidationChain 负载 flake/nop-cluster-core 时序敏感/nop-auth-service TOTP 恒红/nop-stream-core 恒红/debezium known issue 五面）。起草期双 `-am` 通道亲跑实录（r2/r3）：**r2 `./mvnw test -pl nop-datav -am`（`_tmp/baseline-2526-datav-am.log`）BUILD FAILURE EXIT=1——阻断于 nop-auth-service `TestOperationMfaE2E`（18 tests 中 5 errors，TOTP 窗口型——即 2525 勘误 5 已登记面③）**，nop-core 面本轮未触发（负载 flake 非确定性）；solo 复跑确认恒红基线行为（`./mvnw test -pl nop-auth/nop-auth-service -Dtest=TestOperationMfaE2E` 连续 6/18 errors 恒红——`_tmp/baseline-2526-mfa-solo.log`，与 2525 勘误 5 solo 6/18 同签名、错误基数逐轮波动 5–6 先例一致[plan 2502 Phase 5/13 先例]）；r3 `./mvnw test -pl nop-file -am` **BUILD SUCCESS EXIT=0 全绿**（`_tmp/baseline-2526-file-am.log`，822 个测试类 0F0E——live reactor 亲读核实：nop-file 的 `-am` 上游闭包**不含 nop-auth-service/nop-datav**[MFA 面不在闭包]，含 nop-kernel 全链/nop-core[flake 本轮未触发——负载型]/nop-orm/nop-biz-file-core 等；nop-file 聚合 7 子模块全 SUCCESS）。**E3 判据（沿 2525 R1 Major-1 修订判据形态）**：Phase 3 执行时双通道 `-am` 命令各执行并留痕至首个上游阻断面，阻断面按三联裁定/恒红基线行为处置留痕后，以等价分解通道 `./mvnw test -pl nop-datav,nop-file`（无 `-am`，剔除 ChatBI 20-red 面后 610+新钉 0F0E）BUILD SUCCESS 作为 E3 可接受等价验收（勘误 3 排除形态 + 2525 勘误 5 r7 先例叠加），不得静默重跑或跳过。
6. **工单粒度声明（finding 清单层）**：batch-proposal R3-03 finding 清单混合工单级 ID（5.3-W-01/5.3-W-04）与 CL 级 ID（12 条）——**W-01=CL-014 单成员工单；W-04=五成员工单中批内承载 3 成员（CL-009 P1 锚+显式单列的 CL-010/CL-021）**；各工单 P3 成员不在清单（CL-006[双读 NPE]/CL-008[hook 非 volatile 一行面]/CL-011[workbook 乘积]/CL-012[队列积压]/CL-013[账本零保留]/CL-017[removeResource log-only]/CL-018[崩溃窗口 watch]/CL-022[会话列表]/CL-024[删除×在途]/CL-025[回补双失败]）→ Non-Goals 登记去向；P2-G76 行十二成员清单与本批 P2 成员逐一相符（权威交叉验证 PASS）；CL-007（P2 watch）已由 cra-index :463 P2-G77 行裁定 deferred，本批零承载。
7. **C10 统计口径差（审计层）**：C10-test-protection.md 头部统计「矩阵 18 行——会红 0/假保护 4/部分 1/无测试 13」与矩阵行可见结论（假保护 3[CL-003/CL-005/CL-016]+部分 1[CL-007]+其余无测试）存在 ±1 计数差（audit 内部口径差，summary.md :38 同引「假保护 4」）——**本批承载以矩阵行逐条结论为准**（批内假保护 3 条全部同 diff 修正 + 部分 1 条[CL-007]零承载 watch），头部计数差不回写历史审计文件（沿 2525 勘误 1「不回写历史裁定文件」先例），本登记即为差注。
8. **W-01 触发面可达性补验（起草期 live 亲读）**：C6-001 复核注「copyFile 经 OrmFileComponent:159 实体复制流可达」——live 核对 `nop-file.md` owner doc :150-173（copyFrom→DaoResourceFileStore.copyFile 契约在案）+ `DaoResourceFileStore.copyFile` :442-460 在案，触发面成立；`nop-orm` OrmFileComponent/OrmFileListComponent 为调用方透传面（audit 失效面枚举「无涉」）——**零触碰声明**（nop-orm 归框架核心域，本批禁触碰）。
9. **[Phase 1 起草增补条款（预留）]** 裁定①须给出两案（两段式清理 vs originFileId 互斥）的否决理由与延迟清理任务的失败面（重试上限/对账 WARN/进程重启残留清理）；裁定②须给出默认值选择论证（10k vs 对齐先例取值）与既有大面板用户的兼容声明（owner doc 落点）；裁定③须给出条件更新谓词（status 字段域枚举）与反向钉死改写后的行为等价断言目标（**结构断言[scanner FAILED 保持+0 行 WARN 日志断言]为默认预设**——沿 2525 裁定④结构断言默认先例）；裁定⑤须给出回收动作自身的失败处理（回收失败显式 WARN+path 结构化日志——不引入 CL-017 面）；裁定⑭须给出对账扫描的触发点（复用 recovery 调度 or 独立周期）与存量行兼容论证；裁定⑦⑧须给出非 orm 形态理由（PA 条款）与行锁在 Nop ORM dao 层的可行实现面核实（live dao API 亲读——如 `IEntityDao` 无 FOR UPDATE 语义则改条件更新+重试形态并登记核实记录）。

## Goals

- Phase 1 设计裁定文档一次裁决 14 项裁定（裁定①–⑭，14 成员各一条 + 测试缝/零回退钉清单，含 CL-003 反向钉死改写口径、CL-001/CL-003 同收口通道、W-04 R02 三范式族、CL-002/CL-004/CL-023 非 orm 形态理由与条件申报边界、CL-020 R1-01 接入点清单）+ 独立 draft review 通过，无临场发明空间。设计文档拟名 `ai-dev/design/concurrency/file-dedup-datav-terminal-fencing-query-bounds-and-scheduler-concurrency.md`（Phase 1 产出物，前向引用；命名裁定：file 去重原子性（W-01 域）+ datav 终态 fencing/取消竞态/孤儿回收（W-02/W-05 域）+ check-then-act 限额与版本（W-03 域）+ 查询上界（W-04 域）+ 调度生命周期（W-07 域）多域合一，与现有 concurrency 设计集命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 14 项成员全部修复并各带回归测试（rule 25）；C10 收口门（缺保护 5 条[缺保护 3/4/5 为批次验收前置] + 假保护 3 条同 diff 修正）落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红⑭逐成员登记（形态要点，细节归裁定测试缝表）：红① 去重红（W-01：copy×detach 两线程 latch 构造性交错——T2 copyFile 读记录后 latch、T1 detach 完成、T2 提交、断言物理文件仍存在[C10 补测-1 建议形态]，**当前即红**；交错窗无挂钩点时降级变异论证——沿 2525 红③ 先例形态登记）；红② fencing 红（CL-003：反向钉死 :308 改写后 scanner FAILED 保持+worker 条件写 0 行可观测+**0 行分支 fileId 处置断言[R1 Major-1——回收或带 fileId 落 FAILED 可观测]** + face b 复活钉[积压 delivery 加载后终态短路]，**当前即红**）；红③ cancel 竞态红（CL-001：executionStartHook seam 交错——终态不被覆盖+cancelFlags 空[缺保护-5 建议形态]，**当前即红**）；红④ 孤儿红（CL-016：cancel-after-save 后 NopFileRecord 无残留行+物理文件不存在[缺保护-4 建议形态]，**0 行放弃分支交错面同步覆盖[R1 Major-1——条件更新 0 行时 fileId 不留孤儿]**，当前即红）；红⑤ 版本红（CL-002：并发 publish 无重复 snapshotVersion——行锁/条件更新驱动，**当前即红**）；红⑥ 告警红（CL-004：双入口交错单冷静期仅一通知+loadOrCreateState 单行，**当前即红**）；红⑦ 限额红（CL-005：并发提交不越 max + :221 假保护并发化同 diff，**当前即红**）；红⑧ 上界红（CL-009：大结果集>配置上界显式抛 ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED+错误码断言[补测清单#2 建议形态]——实现落地后红，配变异论证形态）；红⑨ 加载界红（CL-010：loadMessages LIMIT 生效断言——消息数上界+裁剪次序修正可观测）；红⑩ 投影红（CL-021：历史查询不加载 SNAPSHOT_CONTENT[投影断言]或分页参数断言——按裁定⑪形态）；红⑪ 泄漏红（CL-015：writeCsv/writeXlsx 异常注入后 temp 目录无 datav-export* 残留[@AfterEach 钩子形态——C10 泄漏钩子缺口首例]，**当前即红**）；红⑫ 回滚恢复红（CL-019：注销后其余步骤失败回滚→job 仍注册断言，**当前即红**）；红⑬ 自愈红（CL-020：init 注入失败→重试后注册成功/健康位翻转断言，**当前即红**）；红⑭ 半程红（CL-023：deliveredChannels 空的 SUCCEEDED 行对账清单输出断言[最简案形态]或 PENDING_NOTIFY 恢复重发断言——按裁定⑭形态，**当前即红**）。
- CL-003×CL-001×CL-016 同文件交叉的协同兑现留证：终态条件更新单形态收口可证（红②/红③双钉 + 设计文档同收口通道条款）；CL-003 反向钉死改写与 R12 族对齐留证（改写后断言目标=行为等价口径禁纯删）。
- Owner doc 更新落账：`docs-for-ai/03-modules/nop-datav.md`（**新建**——缺口声明兑现：W-04 上界配置族+错误码声明、导出/交付状态机并发契约、调度生命周期契约；merge-adjudication §九 SSOT 义务）、`docs-for-ai/03-modules/nop-file.md`（增补去重存储并发契约节）、`docs-for-ai/04-reference/source-anchors.md`（FILE-CC-001 / DATAV-CC-001 锚点行，修复后 live 亲读核实）、`docs-for-ai/01-repo-map/module-groups.md` 路由核对（nop-datav owner doc 新建后的最小同步）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做批外 CL 全集**（各工单 P3 成员+watch：CL-006 双读 NPE[审计 :159-179]/CL-008 hook 非 volatile 一行面[测试 seam 本体，缺保护-5 钉仅消费该 seam 零改其字段]/CL-011 workbook 乘积[§九 P3「声明包络出口」候选——NopDatavConfigs Description 补乘积上界声明即闭单，归后续]/CL-012 队列积压/CL-013 账本零保留[W-06 P3——族级先例 ledger-retention-policy.md]/CL-017 removeResource log-only[W-05 P3——本批两段式清理形态不得顺手改其语义，对齐纪律条款]/CL-018 ChatBI 崩溃窗口[watch 豁免候选]/CL-022 会话列表无界[W-04 P3 尾部——listSessions :96-101 零触碰]/CL-024 删除×在途/CL-025 回补双失败/CL-007 Semaphore 占池[ cra-index :463 P2-G77 deferred，负载画像后立项]）——均不在 R3-03 finding 清单（勘误 6）。
- **不做 ChatBI 工具执行器接线修复**（nop-datav ChatBI 测试线束 20 tests 恒红——plan 2501 fail-loud 变更 `bafd41970c` 跨批遗留，勘误 3；修复面在 nop-ai/3.2 域测试线束接线，successor 义务登记，本批零触碰且禁顺手改测试线束——E3 以排除形态验收）。
- **不做 CL-020 平台级统一绝缘层**（29 站点 catch Throwable+completeExceptionally+重臂——归 R1-01[2.1-C3-001]，本单最小面+接入点登记；禁触 nop-kernel）。
- **不做 W-06 账本保留族**（CL-012/CL-013——P3 第三批；快照保留数上界仅在裁定⑪域内作为可选项，不扩张为保留策略实现）。
- **不做跨单元同族站点**：终态 fencing 族在 1.5/1.7/1.6 单元的异站点零触碰（R12 纪律引用不重开）；无界 findAll 族在 1.5/1.7 异站点零触碰（R02 对齐引用）；nop-orm OrmFileComponent/OrmFileListComponent 透传面零触碰（勘误 8）。
- 不做平台层改码：nop-api-core/nop-core/nop-kernel/nop-ai-toolkit/nop-commons 零触碰（`ISemaphore`/`IResourceLockManager`/`IJobScheduler`/`transactionTemplate`/`CoreMetrics` 均为只读使用面引用）；nop-file-api/nop-datav-api 零触碰；`nop-file.orm.xml`/`nop-datav.orm.xml` 零触碰（头部预设——条件申报条款见 PA 表第 7/8/14 行）；`_` 生成物零手改。
- 不做结构性优化扩张：面板查询/导出/交付分池（CL-007 watch 面）；SXSSF 流式导出（CL-011 面）；ChatBI 会话/消息保留期策略（CL-013 面）——未选案登记 Deferred/Non-Blocking Follow-up。
- 不触碰 R3 其余批（R3-01 blocked/R3-04+）成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-file/nop-file-dao/src/main/java/io/nop/file/dao/store/DaoResourceFileStore.java`（裁定① W-01）。
- `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java`（裁定③ CL-003 导出侧 + 裁定④ CL-001 + 裁定⑤ CL-016 导出侧 + 裁定⑨ CL-005——同文件四成员分裁定交付，diff 分组登记防混）、`.../report/ReportDeliveryExecutor.java`（裁定③ 交付侧 + 裁定⑤ 同族 + 裁定⑭ CL-023）、`.../export/NopDatavExportTaskRecovery.java`、`.../report/NopDatavReportDeliveryRecovery.java`（裁定③ scanner 面）。
- `.../entity/NopDatavDashboardBizModel.java`（裁定⑦ CL-002 + 裁定② 调用方 + 裁定⑫ CL-019——同文件三成员，diff 分组防混）、`.../entity/NopDatavScreenBizModel.java`（裁定⑦ 同族 + 裁定⑪ CL-021）、`.../entity/NopDatavPanelBizModel.java`（裁定② 调用方）、`.../query/PanelDataBinder.java`（裁定②）、`.../alert/AlertEvaluator.java`（裁定⑧）、`.../chatbi/ChatBiSessionManager.java` + `.../entity/NopDatavChatBiBizModel.java`（裁定⑩）、`.../export/PanelDataExporter.java`（裁定⑥）、`.../entity/NopDatavReportTaskBizModel.java` + `.../report/NopDatavReportScheduler.java` + `.../recovery/NopDatavStuckTaskScanner.java` + `.../alert/NopDatavAlertScheduler.java`（裁定⑫⑬）。
- 配置/错误码源注册面：`.../service/NopDatavConfigs.java`（裁定② 新键）、`.../service/NopDatavErrors.java`（裁定② 新错误码）。
- 测试树（既有树内新测试类/用例+既有测试改写，零新建模块测试树）：nop-file-dao（红①）、nop-datav-service（红②–红⑭ + 反向钉死改写 TestNopDatavStuckTaskScan :308 + 假保护修正 TestNopDatavExportE2E :221 + ExportE2E cancel 场景文件守恒断言）；既有基线零回退钉（勘误 4：nop-file 17 + nop-datav 非 ChatBI 面 610）。
- 设计文档：`ai-dev/design/concurrency/file-dedup-datav-terminal-fencing-query-bounds-and-scheduler-concurrency.md`（新建；裁定①–⑭+测试缝+零回退钉+CL-003 反向钉死/CL-001 同收口/W-04 R02 族/CL-020 接入点清单，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/03-modules/nop-datav.md`（**新建**）、`docs-for-ai/03-modules/nop-file.md`（增补节）、`docs-for-ai/04-reference/source-anchors.md`（FILE-CC-001/DATAV-CC-001）、`docs-for-ai/01-repo-map/module-groups.md`（路由核对）。

### Out Of Scope

- Non-Goals 所列全部项；批外 CL 全集（勘误 6）；ChatBI 工具执行器红面（plan 2501 域 successor）；W-06 保留族；2.1-C3-001/R1-01 平台面本体；1.5/1.7/1.6 同族异站点；平台设施与 api 叶；`nop-file.orm.xml`/`nop-datav.orm.xml`；nop-orm 透传面；nop-datav-web/nop-datav-dao/nop-file-web 等非载体模块手工触碰；`_` 生成物。

## Execution Plan

### Phase 1 - file 去重原子性/datav 终态 fencing/查询上界/调度生命周期并发设计裁定文档 + 独立评审

Status: planned
Targets: `ai-dev/design/concurrency/file-dedup-datav-terminal-fencing-query-bounds-and-scheduler-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [ ] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [ ] **裁定① W-01 去重原子化形态（缺保护-1 随同 diff）**：两段式延迟清理 vs originFileId 互斥两案定夺禁第三形态；延迟清理任务的失败面（重试上限/对账 WARN/重启残留处理）；removeTempFileByOwner 同族处置；removeResource :262-270 零触碰边界声明；plan 2386 权限钉兼容声明
  - [ ] **裁定② W-04 行上界形态（缺保护-2 随同 diff）**：新配置键名/默认值/超限错误码（NopDatavErrors 注册）；三站点（Dashboard :583/Panel :136/:150）传入形态；R02 行上界范式成文（与 1.5-MA-05/1.7-C5-001 对齐点逐项）；大面板用户兼容声明（owner doc 落点）
  - [ ] **裁定③ CL-003 终态 fencing 形态（反向钉死改写随同 diff）**：条件更新谓词（status 字段域枚举）与影响行数判定；执行体终态短路形态（export executeTask/delivery runDeliveryInSession 双面）；scanner 面（recovery 双 bean doScanStuck）守卫形态；**0 行放弃分支的 fileId 处置[R1 Major-1]**（0 行=已被抢先定终态时已落盘 fileId 回收 or 带 fileId 落 FAILED 二选一一次定夺禁第三形态——与裁定⑤合流，防 0 行分支新孤儿面）；**反向钉死改写目标**（结构断言[scanner FAILED 保持+0 行 WARN 日志断言]默认预设——禁纯删形态）；R12 族对齐矩阵（与 1.5/1.7-C7-001/1.6-C8-001 平台族条件更新纪律的引用点）
  - [ ] **裁定④ CL-001 cancel 收口形态（缺保护-5 随同 diff）**：putIfAbsent+终态写并入裁定③收口；残留兜底方案定夺（executeTask 收尾 remove 或 flag 挂 DB 行）；与裁定③同文件同形态禁两套 fencing 声明
  - [ ] **裁定⑤ CL-016 孤儿回收形态（假保护修正随同 diff）**：回收动作形态——**预设 Option A = 模块内 daoFor(NopFileRecord)+getResourceStore() 镜像 detach 序列（非扩权缺省[R1 Major-2]）**，Option B = nop-biz-file-core 公共接口形态（IFileStore 加方法——**须四件证据链追加申报+nop-biz-file-core 全 impl 回归面逐一名列**，仅在 Option A 论证不成立时经申报采纳）；与裁定③ 0 行分支 fileId 处置合流定夺[R1 Major-1]；回收自身失败的显式处理（WARN+path 结构化日志——不引入 CL-017 面）；交付侧 :275 同族形态；ExportE2E 文件守恒断言形态（与缺保护-4/-5 钉合流）
  - [ ] **裁定⑥ CL-015 泄漏收口形态**：catch→delete 后 rethrow vs 职责内 finally delete 两案定夺；writeXlsx 两重载同修；泄漏钉 @AfterEach 形态
  - [ ] **裁定⑦ CL-002 版本号形态**：非 orm 形态两案（行锁语义 dao 面 vs 条件更新+冲突重读重试）定夺——**含 Nop ORM dao 层 FOR UPDATE 可行性 live 核实记录**（如无 FOR UPDATE 语义则登记核实并选条件更新形态）；Dashboard+Screen 同族形态；唯一约束备选否决理由（PA 条款）
  - [ ] **裁定⑧ CL-004 告警形态**：rearm 条件更新谓词+建行收口组合定夺——**谓词枚举显式含 NULL（首通知）分支[R1 Minor-5——AlertEvaluator :195-197 注释自证「通知失败 lastNotifiedTime 保持 null、下轮 shouldRearm(null)=true 立即重试」，`where last_notified_time=旧值` 形态须覆盖 NULL→值迁移否则首通知后条件更新恒 0 行]**；与裁定⑦形态族一致；AlertRuleBizModel.initializeStateIfAbsent 同族核对
  - [ ] **裁定⑨ CL-005 限额形态（假保护修正随同 diff）**：ISemaphore 叠加 vs 显式非原子上界声明两案定夺；:221 并发交错断言形态（假保护修正目标禁纯删）
  - [ ] **裁定⑩ CL-010 加载界形态**：loadMessages LIMIT 推导式（2×maxTurns+1）；getChatSessionHistory 分页与否定夺禁第三形态；listSessions 零触碰边界
  - [ ] **裁定⑪ CL-021 投影/分页形态**：列投影 vs 分页两案定夺；快照保留数上界可选子项定夺（不扩张为保留策略）
  - [ ] **裁定⑫ CL-019 副作用后置形态**：afterCommit vs 失败补偿两案定夺；失效面五站点（disableReportTasks/disableAlertRules×2/doDeleteEntity×2）统一形态；回滚恢复钉形态
  - [ ] **裁定⑬ CL-020 最小面形态**：延迟重试 vs 健康位两案定夺（重试上限一次）；五站点接入点清单成文（R1-01 交付物）；平台面零触碰边界声明
  - [ ] **裁定⑭ CL-023 半程窗口形态**：最简对账案（零模型——头部预设）vs notifyState 列方案（orm 条件申报）两案定夺；对账扫描触发点与存量行兼容论证；rearm 自愈对照语义取舍
  - [ ] **测试缝与红①–红⑭驱动器表+零回退钉清单**：红①–红⑭ 测试形态逐条（含缺陷态红判据形态标注：构造性当前即红[红①③⑤⑥⑦⑨⑩⑫⑬⑭候选]/反转钉死[红②]/变异论证[红④⑧⑪——异常注入与大结果集注入面]；执行期不可构造降级结构钉沿 2523/2524/2525 先例登记）；零回退钉 = 勘误 4 基线（nop-file 17 + nop-datav 非 ChatBI 面 610）逐模块+ChatBI 20-red 面恒红维持声明；点名既有钉族（TestDaoResourceFileStoreAttachPermission[plan 2386]/TestNopDatavDashboardParallelQuery/TestNopDatavDashboardQueryCache/TestNopDatavAsyncSubmitTransactionPath/TestNopDatavShareRateLimit/TestNopDatavExportE2E/TestNopDatavAlertNotifyTransactionBoundary/TestNopDatavChatBiTransactionBoundary[ChatBI 红面类——其非工具链用例维持]/TestPanelDataExporter×2/TestNopDatavReportE2E/TestNopDatavChatBiSessionManagement/TestNopDatavDeleteLifecycle/TestNopDatavStuckTaskScan 既有用例族）零回退义务
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动；记录于本 plan 头部 Draft Review 行或 daily log（本 plan 自身的立项 draft review 先行于设计文档 review——两级 review 均登记）
- [ ] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑭全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [ ] 裁定①含两案否决理由与延迟清理失败面；裁定③含反向钉死改写目标（结构断言默认预设）+R12 对齐矩阵；裁定②含 R02 范式成文与兼容声明；裁定⑦含 dao 层 FOR UPDATE 可行性核实记录；裁定⑦⑧⑭含非 orm 形态理由与条件申报边界；裁定⑬含 R1-01 接入点清单；测试缝节（红①–红⑭驱动器表）含零回退钉全列
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 14 成员实现 + 回归测试（按模块分组 A–E）

Status: planned
Targets: `nop-file/nop-file-dao/`、`nop-datav/nop-datav-service/`（main+test 树；`NopDatavConfigs.java`/`NopDatavErrors.java`）

- Item Types: `Fix`（14 项均为已确认 live defect——P1×2/P2×12，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 A nop-file（W-01）：裁定①（红① 去重红[copy×detach latch 或变异论证]；removeTempFileByOwner 同族；CL-017 零触碰；plan 2386 权限钉零回退核对）
- [ ] 组 B nop-datav 导出/交付执行体（CL-003+CL-001+CL-016+CL-015+CL-023）：裁定③（**反向钉死 :308 同 diff 改写** + 红② fencing 红 + face b 复活钉）→ 裁定④（红③ cancel 竞态红[缺保护-5]——与裁定③同收口通道）→ 裁定⑤（红④ 孤儿红[缺保护-4]+ExportE2E 文件守恒断言）→ 裁定⑥（红⑪ 泄漏红[@AfterEach 钩子首例]）→ 裁定⑭（红⑭ 半程红）——同文件多成员 diff 分组登记（ExportTaskBizModel×3/ReportDeliveryExecutor×3）
- [ ] 组 C nop-datav check-then-act（CL-002+CL-004+CL-005）：裁定⑦（红⑤ 版本红——Dashboard+Screen 同族同 diff）+ 裁定⑧（红⑥ 告警红）+ 裁定⑨（红⑦ 限额红 + **:221 假保护同 diff 修正**）
- [ ] 组 D nop-datav 查询上界（CL-009+CL-010+CL-021）：裁定②（红⑧ 上界红[缺保护-2]——NopDatavConfigs 新键+NopDatavErrors 新错误码）→ 裁定⑩（红⑨ 加载界红）→ 裁定⑪（红⑩ 投影红）——R02 三范式批内一次成文
- [ ] 组 E nop-datav 调度生命周期（CL-019+CL-020）：裁定⑫（红⑫ 回滚恢复红——afterCommit 后置/补偿）+ 裁定⑬（红⑬ 自愈红——最小面+R1-01 接入点清单登记）
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红⑭快照落 `_tmp/` 并登记（`_tmp/red-green-2526-summary.md` 总册 + 逐 trial 日志——命名沿 2525 形态）；变异探针纪律兑现（恢复路径以 grep/断言确认目标行真实存在——plan guide When Executing #8 条款）；ChatBI 20-red 面修复期前后恒红维持核对（勘误 3/4 口径）
- [ ] **端到端验证**（rule 22）：去重链 = 实体 copyFrom→flush→detach→物理文件存在性全链钉（W-01）；导出链 = createExportTask→execute→cancel/失败→终态正确+文件守恒全链钉（组 B：ExportE2E 扩展——从 create 入口到 fileStore 出口）；交付链 = runDelivery→SMTP 失败→补偿/对账全链钉（CL-023——从 runDelivery 入口到 rollbackSucceededToFailed/对账出口）；发布链 = 并发 publish→版本唯一全链钉（CL-002）；查询链 = getDashboardData 大结果集→上界拒绝全链钉（CL-009——从 getDashboardData 入口到 ERR 出口）；调度链 = dashboard 删除→afterCommit 注销→回滚恢复全链钉（CL-019——从 handleDeleteCascade 入口到 scheduler 状态出口）
- [ ] **接线验证**（rule 23）：CL-003 条件写真拦截（0 行路径 WARN 可观测断言）；CL-019 afterCommit 真挂点（回滚不触发注销断言——TestNopDatavAsyncSubmitTransactionPath 既有 seam 形态参照）；CL-020 重试真注册/健康位真翻转；CL-010 LIMIT 真下推（返回消息数断言）；CL-009 上界真抛 ERR（错误码断言）；CL-016 回收真执行（残留行数守恒断言）
- [ ] **无静默跳过**（rule 24）：CL-003/CL-001 条件写 0 行路径显式 WARN（结构化日志）；CL-016/裁定① 回收/清理失败显式 WARN+path 日志（禁吞）；CL-009 上界拒绝显式 ERR（非静默截断）；CL-020 重试终败显式 WARN/健康位=false（非静默停摆）；CL-014 对账清单显式输出；新增公共分支无空方法体/TODO（全分支显式）

Exit Criteria:

- [ ] 14 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单（`Test*_<工单>` 惯例：W01/CL003/CL001/CL016/CL015/CL002/CL004/CL005/CL009/CL010/CL021/CL019/CL020/CL023）；C10 收口门（缺保护 5+假保护 3）同 diff 落地（缺保护 3/4/5 验收前置逐条勾验）
- [ ] 必红证据（E2）：红①–红⑭均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记（red-green-2526-summary.md：形态标注逐钉在册）
- [ ] 零回退实证：nop-file 聚合 BUILD SUCCESS（基线 17 tests 全绿维持）+ nop-datav 非 ChatBI 面 BUILD SUCCESS（基线 610 tests 0F0E 维持 + ChatBI 20-red 面恒红归因核对）；既有钉族零回退（TestDaoResourceFileStoreAttachPermission/ParallelQuery/AsyncSubmit/ShareRateLimit/StuckTaskScan 既有用例/PanelDataExporter×2/ReportE2E 等点名族）；反向钉死 :308 改写仅 CL-003 同 diff 且行为等价断言在位（禁纯删）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] E3 组内留痕：组内 focused 全绿 + 模块聚合绿（nop-file/nop-datav 双聚合）；双通道 `-am` 与等价分解通道全量验收归 Phase 3 统一执行（显式登记去向）；实际触达模块（git diff 核对）：nop-file-dao main+test、nop-datav-service main+test——零触碰确认：`_` 生成物零触碰、orm 模型零触碰（或条件申报四件证据链闭合后按申报面）、平台设施/api 叶零触碰、批外 CL 与 ChatBI 红面零触碰；Owner doc 定稿归 Phase 3
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: planned
Targets: nop-file+nop-datav 聚合全量测试；`docs-for-ai/03-modules/nop-datav.md`（新建）；`docs-for-ai/03-modules/nop-file.md`；`docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/01-repo-map/module-groups.md`（核对）

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C -DskipTests` EXIT=0 留痕（上游已知 flake 面按 2497–2525 先例快速通道支路声明；聚焦覆盖 = E3 分解通道）
- [ ] 批行验证命令（E3 判据——勘误 5）：`./mvnw test -pl nop-datav -am` 与 `./mvnw test -pl nop-file -am` 各执行并留痕至首个上游阻断面（阻断面按三联裁定/恒红基线行为处置留痕——上游已知面集见勘误 5）；等价分解通道 `./mvnw test -pl nop-datav,nop-file`（无 `-am`）BUILD SUCCESS：nop-file 全量 0F0E + nop-datav 剔除 ChatBI 20-red 面（8 类，勘误 3 签名逐字比对）后 0F0E + ChatBI 20-red 面恒红维持归因登记（**排除形态验收沿 2525 勘误 5 r7 + 2520 面③ 先例叠加**）
- [ ] Owner doc 落账：`nop-datav.md` **新建**（缺口声明兑现——上界配置族含新键默认值、错误码表增补、导出/交付状态机并发契约节、调度生命周期契约节）、`nop-file.md` 增「去重存储并发契约」节（引用计数/物理删除原子性语义）、`source-anchors.md` 新增 `FILE-CC-001`/`DATAV-CC-001` 锚点行（修复后 live 行号亲读核实）、`module-groups.md` 路由核对（nop-datav owner doc 新建后 `:17` 行 `详见 03-modules` 引用核对最小同步）
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow nop-datav EXIT=0 + nop-file EXIT=0（`_tmp/scan-hollow-{datav,file}.log`）
- [ ] `ai-dev/logs/` 对应日期条目已更新（Phase 2+3 条目）

Exit Criteria:

- [ ] E1 + 批行验证命令验收且逐条登记——双通道至首个阻断面留痕 + 分解通道（含 ChatBI 排除形态）BUILD SUCCESS 等价验收；E1 支路理由已声明
- [ ] nop-datav.md 新建落账 + nop-file.md 增补节 + source-anchors.md 双锚点 + module-groups 核对登记；check-doc-links EXIT=0；scan-hollow 双 EXIT=0
- [ ] 零触碰确认登记（git diff：`_` 生成物零触碰、orm 零触碰或条件申报链闭合、平台设施/api 叶零触碰、批外成员与 ChatBI 红面零触碰）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.33 行、cra-index P1 台账 :300/:301 + P2-G76 行 :462 + **勘误 1 之 :298/:299 重复行清理**、batch-proposal 勘误注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 成立性，含 Anti-Hollow 检查（条件写/原子化/上界/回收/重试运行时真实生效 + 无空方法体/静默吞）；W-01 非 orm 条件申报链（如触发）齐备性复核
- [ ] cra-index 台账回填：P1 两行 :300/:301 状态收敛（open→fixed，注记 plan 2526 + closure audit 结论）；P2-G76 行 :462 收敛注（十二成员 fixed 注记 + C10 映射面兑现）；**:298/:299 5.2 重复行清理**（勘误 1 义务——删除或标注，登记处置方式）；roadmap 8.33 行 Status → done + 勘误注记（ChatBI 红面移交 + 条件申报触发记录[如触发]，沿 2523/2524/2525 先例形态，不改 L103 原文）；batch-proposal 为历史裁定文件不回写（先例），勘误登记于 roadmap 行与本 Closure 段
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 复核登记（nop-datav/nop-file 双 EXIT=0）；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index P1 两行收敛 + P2-G76 收敛注 + :298/:299 重复行处置登记、roadmap 8.33 = done（含勘误注记）、batch-proposal 勘误注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 14 项成员全部修复：W-01（去重竞态原子化——两段式清理或 originFileId 互斥）、W-04（rowLimit 行上界三站点+新配置键+新错误码；CL-010 加载侧 LIMIT；CL-021 投影/分页）、CL-003（终态条件更新+终态短路+反向钉死同 diff 改写）、CL-001（cancel putIfAbsent+同收口+残留兜底）、CL-016（失败/cancel 分支文件回收+文件守恒断言）、CL-015（writeCsv/writeXlsx 失败路径 delete 收口）、CL-002（发布版本号非 orm 原子化——Dashboard+Screen 同族）、CL-004（告警条件更新 fencing+建行收口）、CL-005（限额原子化+:221 假保护同 diff 修正）、CL-019（注销副作用 afterCommit 后置/补偿——五站点）、CL-020（注册失败最小面自愈+R1-01 接入点登记）、CL-023（半程窗口对账/notifyState 定夺形态）
- [ ] batch-proposal R3-03 行 exit criteria 增补逐条达成并留证：CL-003 反向钉死同 diff 改写（testSlowExecutionRaceWorkerSuccessOverwritesScanFailed :308 反转+R12 对齐）；CL-005 假保护同 diff 修正（:221 并发化）；W-01 去重竞态原子化；W-04 rowLimit 上界（R02 三范式）；缺保护 3/4/5 验收前置（CL-003 配套/CL-016/CL-001 三钉在位）
- [ ] 对齐纪律守约：CL-003 反向钉死同 diff+face a/b 边界兑现；CL-005 假保护修正兑现；W-01 CL-017 零触碰边界兑现；W-04 R02 三范式一次成文+批内三成员同批兑现；CL-020 R1-01 边界兑现（禁平台面）；CL-019 AR-2 先例复用兑现；CL-002/CL-004/CL-023 非 orm 预设兑现（或条件申报四件证据链闭合）；CL-001/CL-003 同收口单形态兑现；C10 同 diff 兑现（缺保护 5+假保护 3）；PA 零触碰声明闭环（closure audit git diff 全量核对 PASS）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25，全工单命名）；红①–红⑭均有先红后绿或变异/回退论证（E2；形态标注逐钉在 red-green 总册）；C10 收口门（5 缺保护+3 假保护修正）同 diff 落地
- [ ] 设计文档裁定①–⑭成文且经独立 draft review（两级 review——plan 立项 review + 设计文档 review 均在案）；既有钉（勘误 4 基线 + 既有钉族）零回退实证（closure audit git diff 零改动核对 PASS）；反向钉死与假保护修正后行为等价断言在位（禁纯删）
- [ ] 全量构建绿：E1 留痕（支路声明在案）+ 批行验证命令按 E3 判据验收（双通道至阻断面留痕 + 分解通道 ChatBI 排除形态 BUILD SUCCESS 等价验收 + 20-red 面恒红归因登记）
- [ ] Owner doc 落账完成：nop-datav.md 新建 + nop-file.md 增补节 + source-anchors.md FILE-CC-001/DATAV-CC-001 + module-groups 核对（E5——缺口声明兑现）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（nop-datav/nop-file 双 EXIT=0 真实退出码）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（closure audit Deferred 诚实性 PASS——批外 CL 均为批次定义层排除非本批降级）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27，fresh session）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）条件更新/原子化/上界拒绝/文件回收/重试自愈/对账输出运行时真实生效（调用链追踪 + 红绿日志证行为改变），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-file+nop-datav 聚合——E3 分解通道含 ChatBI 排除形态验收）；checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 5.3 P1 两行收敛 + P2-G76 收敛注 + :298/:299 重复行处置 + roadmap 8.33 → done（E6）

## Deferred But Adjudicated

### 批外 CL 全集（P3 成员×10 + watch×1：CL-006/CL-008/CL-011/CL-012/CL-013/CL-017/CL-018/CL-022/CL-024/CL-025/CL-007）

- Classification: `out-of-scope improvement`（merge-adjudication §八 权威表 P3 判级 + P2-G77 已裁定 deferred——batch-proposal R3-03 finding 清单批前裁定不含，非本批降级）；Successor Required: `yes`（分去向：CL-011 声明包络出口[§九]/CL-012+CL-013 归 W-06 P3 第三批[族级先例 ledger-retention-policy.md]/CL-017+CL-018 归 W-05 尾部/CL-022 归 W-04 尾部/CL-024+CL-025 归 W-02/W-07 尾部/CL-006+CL-008 独立 P3 尾部/CL-007 负载画像触发条款[P2-G77]）；Successor Path: R4+ 后续轮次批次 plan（merge-adjudication §九 第三批/watch 入口）
- Why Not Blocking Closure: 判级对齐零重复——P3/watch 工单批次列显式不在 R3-03 finding 清单（勘误 6 交叉验证）；批内承载的 14 成员已覆盖 P1 全部与 P2 主体（12/13——P2 唯一批外成员为 CL-007 watch）。

### ChatBI 工具执行器接线红面（nop-datav 8 测试类 20 tests 恒红）

- Classification: `out-of-scope improvement`（plan 2501 fail-loud 变更 `bafd41970c` 的跨批遗留——红面成因在本批范围外）；Successor Required: `yes`；Successor Path: nop-ai/3.2 域（plan 2501 后续批次或独立 doc/测试线束修复批——ChatBI 测试线束 executor 接线）
- Why Not Blocking Closure: 红面与 14 成员零站点关联（勘误 3 逐字比对）；E3 以排除形态验收（红面前后恒红维持核对+归因登记），非 ChatBI 面 610 tests 全绿保障本批修复面成立；修复线束属 nop-ai 域，本批触碰将违反零触碰纪律。

## Non-Blocking Follow-ups

- **导出产物对账任务**（CL-016 回收失败的兜底巡检/CL-017 孤儿对账——结构化 path 日志 grep 形态已在钉内，定期对账任务为优化候选）。
- **面板查询/导出/交付分池**（CL-007 watch 面——负载画像量化后立项，P2-G77 successor）。
- **SXSSF 流式导出**（CL-011 面——若声明包络出口未采）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Risks And Rollback

- **W-01 两段式清理的对账面风险**：延迟清理任务自身成为新停摆面（任务失败→物理文件永不清理→慢泄漏）——重试上限+对账 WARN 为裁定①前置；两案（互斥 vs 两段式）的并发正确性差异（互斥锁泄漏/两段式窗口残留）须在裁定①枚举；回滚 = 收口点单点可逆，红①钉防「索引语义回退为判-删」退化；plan 2386 权限钉回退风险（零回退钉在位即防）。
- **CL-003 条件更新行为面风险**：谓词选取过窄可能把合法终态推进误拒（如 SKIPPED 态交付）——谓词以 status 字段域枚举为界（裁定③枚举核对）；反向钉死改写若口径过纯删将失去保护力——行为等价断言目标由裁定③锁定禁纯删形态；face a 豁免语义在改写后须保持（scanner 误杀→FAILED 保持+冲突可观测，不得反向把 face a 改为可覆盖）。
- **CL-001×CL-003 同文件改造交叉风险**：cancel 分支与 worker 收口同方法族——形态不一致将引入新竞态窗（同收口通道条款即防）；CL-016 回收与 CL-001 兜底 remove 的执行次序（回收后再 remove 防泄漏）由裁定⑤成文；回滚 = 各分支单点可逆，红②③④三钉独立可跑防归因混淆。
- **W-04 上界配置的兼容风险**：既有用户大面板查询被新上界拒绝（行为变更）——默认值论证+错误码语义 owner doc 声明为 Phase 1 前置；上界是防 OOM 硬防护禁 advisory 化（缺保护-2 验收前置条款）；`getChatSessionHistory` 分页与否改变用户可见 API 面（裁定⑩定夺+owner doc 登记）；回滚 = 配置键默认值单点可逆，红⑧钉防「上界存在但未接线」退化。
- **CL-002/CL-004 非 orm 形态的可行性风险**：Nop ORM dao 层若无条件更新/行锁语义面，行锁方案不可实施——裁定⑦ dao API 亲读核实为 Phase 1 前置（勘误 9 条款）；条件更新+重试的活锁面（重试上限一次定夺）；如改选 orm 唯一约束须走四件证据链追加申报（禁静默扩权——PA 表第 7/8 行）。
- **CL-019 afterCommit 形态的事务边界风险**：afterCommit 内异常处理（注册失败是否回滚 DB 行——不对称新窗口）为裁定⑫前置论证；TestNopDatavAsyncSubmitTransactionPath 既有 seam 零回退（先例钉在案即防）；五站点统一形态一次成文禁同族两形态。
- **CL-020 重试风暴风险**：启动期重试与宿主启动时序（多租户宿主不抛崩启动的既有选择）——重试一次上限+终败显式 WARN/健康位为裁定⑬前置；禁无限重试/禁抛崩启动（audit 误报排除口径维持）。
- **CL-023 对账案的行为面风险**：对账 WARN 清单若无人消费则停摆不可见性未消除——消费面（运维文档/owner doc 登记）随批落账；notifyState 列方案须 ORM plan-first 追加申报+存量行迁移（禁静默扩权——PA 表第 14 行）；回滚 = 对账扫描单点可逆，红⑭钉防「扫描存在但谓词漏行」退化。
- **同文件多成员交叉风险**：NopDatavExportTaskBizModel 承载 CL-003/CL-001/CL-016/CL-005 四成员、NopDatavDashboardBizModel 承载 CL-002/CL-009/CL-019 三成员、ReportDeliveryExecutor 承载 CL-003/CL-016/CL-023 三成员——diff 分组登记防混（Phase 2 组 B/组 C 显式项），红钉逐成员独立可跑，防「A 成员修复回退时连带打红 B 成员钉」的归因混淆。
- **上游链环境噪声风险**：双通道命令触达上游闭包（nop-core/nop-kernel/nop-auth-service/nop-stream 面——勘误 5 已知面集）+ ChatBI 20-red 面（勘误 3 登记恒红）——三联裁定/恒红归因执行并留痕，不得静默重跑或跳过。

## Closure

Status Note: （立项时留空——closure 时填写：为什么这个 plan 可以关闭）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （待 closure audit 填写——独立审阅者或独立子 agent）
- Audit Session: （待填写）
- Evidence:
  - （待填写——每条 Exit Criterion 与 Closure Gate 的验证结果 + check-plan-checklist --strict 退出码 + Anti-Hollow 检查结果 + Deferred 项分类检查）

Follow-up:

- （待 closure 时填写——只记录 non-blocking follow-up；confirmed live defect 不得出现在这里）
