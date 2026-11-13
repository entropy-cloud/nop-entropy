# 死代码清理登记（R-G 治理批产出物④）

> 定位：汇总 CRA campaign 各批 deferred/watch-only 涉及的死代码面与残留面，作为 roadmap 9.1 对账议题输入。**本批只登记，不执行删除**（plan 2567 Non-Goal；删除实施归后续独立计划）。
> 落档日期：2026-11-12（plan 2567）

| # | 死代码/残留面 | 来源 | 指针 | 登记处置 |
|---|---|---|---|---|
| 1 | 2.1-C1-006 失败毒化死代码（删除优先） | P2-G30 deferred | cra-index P2-G30 行 | **登记维持 deferred**——R-G successor 对账留痕（`r-g-successor-reconciliation.md` #6）；删除实施归 9.1 后独立计划 |
| 2 | 平台 `IZipTool.zipDirTo`（nop-core :75-85 就地覆盖写、无 temp+rename） | R4-29（plan 2563）越界单行申报 | `ai-dev/design/concurrency/search-watch-testinfra-batch.md` §裁定 C（「平台侧 temp+rename 改造留档 backlog」） | 登记为平台侧 backlog 项；使用方层（CliWatchZipCommand 原子写+串行化）已闭合写-读窗口，平台面删除/改造归 9.1 |
| 3 | 2.1 ResourceTimestampCache 死代码 | R1-05（plan 2500）**已移除** | cra-index P1 台账 2.1-C7-007 行 | 已清理（随 R1-05 单点修复移除）——登记为已闭合样本 |
| 4 | 1.6 CL-005（同位点假保护面） | R2-12/R4-10 | cra-index P2-G17 行收敛注（「C8-003=CL-005 同位点主处置随 W-13 关闭」） | 已关闭——非死代码残留，登记防 9.1 双计 |
| 5 | 4.5 CL-005a（redelivery 队列无界/failureAttempts cap=0，watch 拆分半边）+ CL-011 footgun 移交轨 | R4-29（plan 2563） | cra-index P2-G71 行（「CL-011/CL-005 移交轨维持」）+ WI 4.4 CL 小节 | 登记维持移交轨（本批产出物② D 项已将 CL-011 footgun 成文化为检查项） |
| 6 | 6.5 N4 平台布线缺陷（SimpleRpcClient 覆写工厂回调）watch-only | R3-10（plan 2533） | cra-index 6.5-C6-001 行收敛注（「N4 零触碰 watch-only 登记」） | 登记维持 watch-only；平台 rpc 契约面复核归 9.1 |
| 7 | 6.5-C6-019 SDK 升级复检（AbstractClient 无 shutdown——前提证伪） | R2-20（plan 2523） | cra-index 4.3 C6-019 行（「SDK 升级复检义务登记 watch-only」） | 登记维持 watch-only（沿 plan 2518 C7-014 前提证伪先例） |
| 8 | 2.1-C1-007/C2-010 零生产调用方潜伏面 | P2-G30 | cra-index P2-G30 行（successor=CL/域留档条目，接线触发复核） | 登记维持 CL/域留档；接线触发复核通道=产出物①登记册 G30 行 |
| 9 | 3.3 渠道栈休眠面（13 张条件化——C4 全部 P1 条件化挂起等） | 7.1 终裁 | cra-index 3.3 计数口径块 + P2-G57 行 | 非删除类——休眠面维持条款监控（产出物① G57）；任何清理/接线前须过产出物② checklist |
| 10 | 6.3 dormant readText 站点（:539/:701/:787）与死常量激活残留面 | R3-09（plan 2532） | cra-index 6.3-W-02 行（「dormant 三站点统一检查点」已落） | 已闭合——登记防 9.1 重开 |
| 11 | 「Path X 残差」 | 任务指定 grep 项 | 全仓 grep（cra-index/batch-proposal/design 集）**无命中** | 未检索到在案指代——登记为待 9.1 对账确认项（如指代存在，以 9.1 议题补录） |
| 12 | HEAD 预存缺陷（TestIndexNopXlangModule、nop-rpc TestDefaultFlowControlRunner） | plan 2565/2566 实施期发现 | `ai-dev/logs/2026/11-12.md`（8.71/8.72 收口条目，登记 9.1 议题） | 非 CRA deferred 死代码——一并列入 9.1 对账议题输入清单 |

## 9.1 议题输入汇总

- 本登记册全部行（含已闭合样本防双计注记）随 roadmap 9.1 对账宣读；
- 议题一：2.1-C1-006 死代码删除独立计划立项与否；
- 议题二：`IZipTool.zipDirTo` 平台侧原子写改造（或登记接受现状）；
- 议题三：watch-only 三项（N4 布线/C6-019 SDK 复检/CL-005a）量化或关闭裁定；
- 议题四：「Path X 残差」指代确认。
