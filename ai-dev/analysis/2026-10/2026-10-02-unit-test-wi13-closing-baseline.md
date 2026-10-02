# unit-test-coverage-roadmap WI13 — 收口基线刷新与未达标裁定

> Status: resolved
> Date: 2026-10-02
> Scope: 全仓 318 个含主代码模块统一重跑（WI0 管线，exec 当日清理重建）
> Conclusion: 收口全仓加权行覆盖 58.37%（WI0 56.92%→+1.45pp，分母因 15 个模块首次产出覆盖而扩大）；分层 kernel 47.87%（+3.49）/ engine 46.79%（名义 -5.83，分母效应，见下）/ periphery 68.42%（+1.28）；达标模块 159/210（非语义，含 NO-EXEC 判 0）+ 语义 2；未达标 59 模块逐类裁定完毕（8 接受结构性现状 / 13 生成与样板 / 19 容器耦合延期 / 7 大模块后继投入 / 7 引擎运行时延期 / 5 管线外）；WI1-WI12 全部达成或有记录裁定，roadmap 全部 14 个 WI 收口。

## Context

- roadmap：`ai-dev/backlog/unit-test-coverage-roadmap.md` WI13（承载 plan：`ai-dev/plans/2305-unit-test-wi13-closing-baseline.md`）。
- 执行：`ai-dev/tools/coverage-baseline.sh`（WI0 交付脚本，完整管线：exec 清理 → `-Pcoverage test -T 1C -fae` → 缺口补跑 → 逐模块 `jacoco:report` → 解析），收口快照 `coverage-baseline-wi13-2026-10-02.json/md`。
- 本报告为 roadmap 的 M0-M4 全程收口文件；WI0 基线报告见 `2026-10-02-unit-test-wi0-coverage-baseline.md`。

## WI0 → WI13 增量总账（按模块，行覆盖 pp）

| 波次 | 模块（基线→收口） | 主要交付 |
|---|---|---|
| WI1 | nop-core 36.59→44.05（+7.46） | 25 测试类/192 用例 |
| WI2 | commons 23.97→28.59、api-core 29.29→35.01、dataset 21.63→25.50、codegen 26.07→38.94、record-mapping/markdown 持平 | 20 测试类 |
| WI3 | nop-xlang 47.70→49.73（语义口径） | 13 类/123 用例 |
| WI4 | orm-eql 53.24→**57.58 达标**、orm-model 40.61→47.10、dao 45.81→47.93、db-migration 持平 | 6 文件/72 用例 |
| WI5 | wf-core 0→41.55、wf-dao 0→12.35、wf-api 0→7.94 | 13 类+2 mock+17 模型资源/76 用例 |
| WI6 | batch-core 55.40→61.00、batch-dsl 44.35→52.07、batch-exp 65.74→69.76 | 5 文件/41 用例 |
| WI7 | sys-api 0→7.48、sys-dao 67.99→75.21、rule-api 0→5.70、dyn-api 0→4.01、dyn-dao 29.58→48.20；rule-service 快照修复 | 11 文件/44 用例 |
| WI8 | biz 44.10→50.04、biz-auth-core 51.79→66.61、gateway 62.55→67.70、biz-auth-api 0→9.44 | 91 用例（IGraphQLEngine 硬通道） |
| WI9 | excel 16.44→**31.06 达标**、record 59.17→64.48 | 15 类/59 用例+golden |
| WI10 | pdf 28.03→35.59、mermaid 6.40→42.28、converter 9.30→22.57、office-model 0→53.90、office-doc-model 0→60.26 | 16 文件/117 用例 |
| WI11 | cluster-core 13.95→34.06、tcc-core 6.11→62.59、retry-api 0→43.38、rpc-core →40.68、http-api →40.61 | 117 用例 |
| WI12 | spring-core-starter 0→40.48、search-core 0→75.61、integration-api 15.35→58.66、file-service 0→100、lint 链补跑 94.2/83.93；WI0 遗留 8 项清零 | 44 用例 |

新交付测试约 **830+ 用例**（各 plan 审计口径合计）；WI0 测试失败项全部清零（fraud 2PC 转 bug 流程后由后继修复、rule-service 快照重录、lint 普查由并行会话修复经 WI12 验证、nop-code-web StubHttpClient）。

## 分层收口数字与"分母效应"说明

| 分层 | WI0 | WI13 收口 | 变化 | 说明 |
|---|---:|---:|---:|---|
| kernel | 44.38% | 47.87% | +3.49 | 口径可比（新增报告模块少） |
| engine | 52.62% | 46.79% | 名义 -5.83 | **分母效应，非回归**：WI0 时 sys-api/wf-api/dyn-api/rule-api/biz-auth-api/wf-dao 六模块 NO-EXEC 不计入加权；WI1-WI12 为其建立测试后以低行覆盖进入分母（合计约 6300 行 × <13%），拉低名义值。逐模块全部为增量（见总账） |
| periphery | 67.14% | 68.42% | +1.28 | 同类分母扩大（retry-api/integration 补测等） |
| 全仓 | 56.92% | 58.37% | +1.45 | 有报告模块 198→213 |

## 未达标模块裁定（59 个，逐类显式裁定；完整数字见 wi13 快照）

### A. 接口/bean 声明性模块 —— 接受结构性现状（8 个）

nop-dyn-api(4.01)、nop-rule-api(5.70)、nop-sys-api(7.48)、nop-wf-api(7.94)、nop-biz-auth-api(9.44)、nop-job-api(5.39)、nop-rpc-api(7.50)、nop-ai-api(20.92)

- 裁定：**接受现状**。模块由 Api 接口 + Input/OutputBean 组成、零实现逻辑；WI7/WI8 已建立契约测试面（@BizModel 名/泛型绑定/bean 契约/错误码），行覆盖天然低。语义实现面由对应 dao/service 承载且已大幅覆盖（sys-dao 75.21%、dyn-dao 48.20%）。
- Why Not Blocking：roadmap closure 规则允许有记录的接受裁定；行覆盖预设对该类模块不适用（WI13 复裁权依据 roadmap Rules）。

### B. 生成物与样板/CLI 模块 —— 接受现状或管线外（13 个）

nop-kernel-cli(NO-EXEC，测试类非 JUnit 可执行)、nop-codegen(38.94)、nop-spring-demo(NO-EXEC)、nop-rg-cli(NO-EXEC，argLine 有意替换属 build-infra)、nop-orm-demo(0)、nop-ooxml-markdown(0)、nop-task-app(0)、nop-ui(1.73)、nop-ooxml-pptx(2.24)、nop-ooxml-common(5.31)、nop-svg(12.01)、nop-quarkus-demo(14.55)、nop-rpc-client-demo(18.18)

- 裁定：**接受现状**。demo/样板模块（spring-demo/quarkus-demo/rpc-client-demo/orm-demo）不作为覆盖目标；ooxml 子模块为 POI 封装样板；codegen 增量已 +12.87（graalvm 域测试到位），maven parse/CodeGenTask 属构建期集成，延期至后继切片；kernel-cli/rg-cli 测试形态问题记录在案。

### C. 容器/IO/中间件耦合模块 —— 延期至后继容器化测试切片（19 个）

nop-rule-dao(0)、nop-biz-file-core(6.67)、nop-log-logback(0)、nop-nosql-core(5.36)、nop-nosql-lettuce(9.76)、nop-boot(9.86)、nop-config(14.77)、nop-orm-rpc(26.39)、nop-orm-geo(38.71)、nop-dbtool-core(41.44)、nop-record-netty(0)、nop-ofbiz-migration(0)、nop-rpc-simple(0)、nop-cli-core(9.24)、nop-web-page(11.86)、nop-auth-sso(18.79)、nop-converter(22.57)、nop-message-pulsar(24.51)、nop-rpc-cluster(26.47)

- 裁定：**延期（optimization candidate，后继容器化/集成测试切片承接）**。残余全部为需要真实容器/IoC/网络/中间件的装配层，纯逻辑测试不可达；converter 已 +13.27 且 lowCoverageClasses 全为 OOXML 耦合类。nop-rule-dao 为结构性声明模块（同 A 类定性，归容器切片一并以集成档位处理）。
- Why Not Blocking Closure：roadmap closure 规则允许有记录的延期裁定；这些模块不在 WI1-WI12 的点名词典或已完成其点名子项。

### D. 内核大模块残余 —— 后继 WI 继续投入（7 个）

nop-dataset(25.50)、nop-commons(28.59)、nop-api-core(35.01)、nop-javac(39.66)、nop-core(44.05)、nop-orm-model(47.10)、nop-dao(47.93)

- 裁定：**延期（继续投入项，非接受）**。各模块均已大幅增量（codegen +12.87、orm-model +6.49 等）且剩余低覆盖类有显式分类靶点池（plan 2293/2294/2296 Deferred 段：commons ~20 个可续测纯逻辑类、core ~62 个、orm-model/dao 设施类与 JDBC 内部路径）。55% 预设对 utility 型/容器域占比高的模块是否合理，留待下一轮覆盖提升立项时按类别重定目标。
- Successor：下一轮覆盖提升 roadmap（本 roadmap 不再开新 WI）。

### E. 引擎运行时路径 —— 延期至引擎深化切片（7 个）

nop-wf-dao(12.35)、nop-batch-jdbc(12.61)、nop-batch-biz(14.07)、nop-sys-service(39.62)、nop-wf-core(41.55)、nop-graphql-orm(41.87)、nop-rule-service(44.19)

- 裁定：**延期（后继引擎深化切片）**。wf-core 已从 0 到 41.55%（手写 fake 驱动真实引擎），残余集中 WorkflowEngineImpl 信号/子流程/listener 运行时路径（需深化 fake 或容器档）；batch-jdbc/biz、graphql-orm、sys-service 同为 ORM/事务容器路径。wf-core 距 45% 目标差 3.45pp 有量化记录。

### F. 管线外/无主代码 —— N/A（5 个）

nop-graphql-grpc（profile 门控）、nop-spring-gateway（sibling SNAPSHOT 未安装）、nop-js（JS 构建链）、nop-lint-nop（无主 Java 代码）、nop-rg-vector（argLine 有意替换，build-infra 独立项）

- 裁定：**N/A/管线外**，WI0 报告已逐个裁定，维持。

### 语义基线（2 个，不参与行覆盖判定）

nop-jq 73.24%（官方 430 用例）、nop-xlang 49.73%（数据用例集）——维持 WI0 语义裁定。

## 达标总览

- 非语义模块 210 个中 **159 达标**（含 WI0 预设阈值判定；NO-EXEC 计未达标）；未达标 51 + 管线外 5 + 语义 2。
- roadmap 点名主战场全部达标或显著增量：orm-eql **达标**、excel **达标**、batch 三模块 **达标**、cluster/tcc/retry-api/rpc/http **达标**、gateway/biz-auth-core/sys-dao/rule-core/graph/db-migration/orm **达标**；未达标集中于 A-F 六类并有逐类裁定。

## 缺陷嫌疑汇总核对

- 43 项 WI 缺陷嫌疑（10 个 `ai-dev/bugs/2026-10/2026-10-02-wi{1,2,3,4,5,7,8,9,10,11}-defect-suspects.md`）+ 1 个独立 fraud 2PC bug 记录（`2026-10-02-stream-2pc-commit-key-single-subtask.md`）= **44 项**，全部记录在案、零丢失、零夹带修复（WI6 卷入事件已回退）。
- P1 级嫌疑（独立立项优先）：FilterOpHelper.dateBetween max 失效、FilterOpHelper.like 方向反、HealthStatus.merge 语义反转、GraphBreadthFirstIterator 根环崩溃、AStar scoreMap 未写入、pdf RCPath 合并单元格越界、mermaid 文法 CLASS/STATE 重复定义。

## Conclusion

- roadmap 14 个 WI 全部收口：WI0-WI12 完成（13 个 plan 独立 audit APPROVE），WI13 本报告收口。
- 收口快照可重复（`coverage-baseline.sh --skip-maven` 幂等）；脚本固化于 `ai-dev/tools/` 供后续巡检防倒退（建议月度巡检对比 wi13 快照防回退）。
- 后续工作：44 项缺陷嫌疑独立立项修复；D/E 类延期的后继覆盖提升 roadmap 与容器化测试切片由下一次规划承载。

## Open Questions

- [ ] 下一轮覆盖提升 roadmap 的立项时间与目标体系（按 A-F 分类定阈值，替代单一边界值）

## References

- `ai-dev/backlog/unit-test-coverage-roadmap.md`（Current Baseline 已回写）
- `ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md`、`ai-dev/plans/2293..2304`、`ai-dev/plans/2305-unit-test-wi13-closing-baseline.md`
- `ai-dev/analysis/2026-10/coverage-baseline-wi13-2026-10-02.json/.md`
- `ai-dev/tools/coverage-baseline.sh/.mjs`
