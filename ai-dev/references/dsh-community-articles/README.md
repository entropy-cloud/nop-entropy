# DSH/Cordis 社区解读文章（微信公众号）

DeepSeek Harness / Cordis 发布后社区产出的六篇解读文章的本地副本。作为《从可逆计算看 DeepSeek Harness 的架构设计》（现整合于 `ai-dev/articles/dsh-architecture-from-reversible-computation.md`）修订的参考材料入库。

> **入库纪律（2026-08-19 事故后确立）**：每篇存档的标题与作者必须与原链接页面的 og:title / og:description 元数据核验一致方可入库；正文由 curl 原始 HTML 经统一脚本（`_tmp/wx_convert.py` 流程）转换，不做人工扩写。首轮入库时抓取工具对 4/6 个链接返回了错误内容（"数字双生子"四篇系列，实际不属于本组链接），已全部替换为核验过的真实文章。

## 元数据（编号与 v3 文章参考文献 [17]–[22] 对应）

| 编号 | 标题 | 作者 | 出处（与 og 元数据核验一致） | 抓取日期 | 内容概要 |
|---|---|---|---|---|---|
| [17] | DeepSeek Harness 拆解：一套能拼装的 Agent 架构 | chino（腾讯程序员） | https://mp.weixin.qq.com/s/DeIty-Nn8tQvE4osy7_bpg | 2026-08-19 | 机制深读：Fiber/effect/服务解析 Proxy、agent loop 细节、preset 两层 scope 链、Code Mode（worker_threads）、工具遮蔽算法、与 Codex 对比、插件生态清单 |
| [18] | Cordis如何支撑DeepSeek Harness的插件化构建：基于源码与架构互证的深度分析 | Kerry | https://mp.weixin.qq.com/s/YTE0rKXFa3zcr5T4lMOtTA | 2026-08-19 | 系统视角：vendor 九包框架族、插件规范五要素、能力接缝三层模式、事件词汇表、双端插件化、运行时自修改扩展、"代价与挑战"一节 |
| [19] | DeepSeek Harness做Agent蒸馏——我翻了源码，发现这架构简直是天然的蒸馏数据工厂 | 唐成 | https://mp.weixin.qq.com/s/YCJe84mPd51AuUMrT4Cgcw | 2026-08-19 | 独特视角：append-only 事件流日志即完整 ReAct 轨迹（turn/start → tool/call → tool/result → …），一份 JSONL 即一份蒸馏训练样本；headless 批量跑任务生产数据 |
| [20] | DSH：DeepSeek Harness 架构解析 | lencx | https://mp.weixin.qq.com/s/Kf87hcNdSmY4ODWI4UZ8cg | 2026-08-19 | 源码逐行核读（标注 commit 快照）：运行时插件图 + 事件流双引擎、五个配置概念、Fiber/effect 细节（含"顶层 disposer 并发清理而非严格 LIFO"的纠错）、patch 语义（按行 id 整段替换/插入）、四大预设、与 Pi/OpenClaw/Codex 对比 |
| [21] | 看懂 Cordis，才能真正看懂 DeepSeek Harness | 八小时之上 | https://mp.weixin.qq.com/s/M-qiI071iceYMocKJytHrA | 2026-08-19 | 入门教程：从"为什么普通插件系统不够用"（热切换 Provider/局部热重载/卸载清理）出发，商场比喻 + 可运行示例讲 Plugin/Context/Service/Injection/Event/Effect 五概念 |
| [22] | 崔添翼最新论文！DeepSeek Harness从插件机制开启自进化 | 关注AI Infra（智猩猩AI整理） | https://mp.weixin.qq.com/s/QbfUiGPE6yUBIDHUG5mDTg | 2026-08-19 | 论文科普：三作者背景（崔添翼为 DSH 团队负责人）、时空可组合性通俗版、VSCode 87/100 且仅 7/100 声明依赖、Koishi 是论文生产案例（四年 4000+ 插件；运行 Cordis v3 而论文讲 v4）、"DSH 尚未实现论文设想的持续自进化" |

## 文件

文件名与上表标题一致（冒号等文件系统不友好字符替换）。文首保留来源 URL、作者、抓取方式说明。

## 使用注意

- 这些文章中的 **dsh 源码细节（路径、机制描述、统计数字）属于二手转述**，与本地 `~/ai/deepseek-harness` 工作区核对一致后方可作为事实引用；未核对的转述在引用时应标注"据 [n] 描述"。
- 生态数据（star 数、插件仓库数等）随时间漂移；[19] 写文时 star 数为 41K，与本文集 2026-08-19 实查值（164,984）差距即为例证，引用时一律以自 query 为准并标注查询日期。
- 首轮错位入库的"数字双生子"四篇系列已移出本目录（在 `~/app/ref-doc/_quarantine-数字双生子系列-来源URL不明/` 留档，来源 URL 不明，**禁止引用**）。
- 原使用规划文档（2026-08-19 的 DSH 文章重构计划）已随 2026-08 草稿清理一并移除；其 §三 吸收清单写作于错位语料之上，[19]–[22] 条目已被 §十 勘误取代。
