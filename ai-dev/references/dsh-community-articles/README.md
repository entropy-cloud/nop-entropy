# DSH/Cordis 社区解读文章（微信公众号）

DeepSeek Harness / Cordis 发布后社区产出的六篇深度解读文章的本地副本。作为《从可逆计算看 DeepSeek Harness 的架构设计》（`ai-dev/articles/2026-08/2026-08-17-dsh-architecture-from-reversible-computation.v3.md`）修订的参考材料入库。

## 元数据

| 编号 | 标题 | 作者 | 出处 | 抓取日期 |
|---|---|---|---|---|
| [17] | DeepSeek Harness 拆解：一套能拼装的 Agent 架构 | chino（腾讯 WXG） | https://mp.weixin.qq.com/s/DeIty-Nn8tQvE4osy7_bpg | 2026-08-19 |
| [18] | Cordis如何支撑DeepSeek Harness的插件化构建：基于源码与架构互证的深度分析 | Kerry | https://mp.weixin.qq.com/s/YTE0rKXFa3zcr5T4lMOtTA | 2026-08-19 |
| [19] | DeepSeek Harness 架构解析：Cordis 插件运行时的深度剖析 | 数字双生子（Byte Logic） | https://mp.weixin.qq.com/s/YCJe84mPd51AuUMrT4Cgcw | 2026-08-19 |
| [20] | DeepSeek Harness 架构解析：以差值定义系统，用插件组装智慧 | 数字双生子（Byte Logic） | https://mp.weixin.qq.com/s/Kf87hcNdSmY4ODWI4UZ8cg | 2026-08-19 |
| [21] | DeepSeek Harness 架构解析：从理论到实现的鸿沟，如何跨越？ | 数字双生子（Byte Logic） | https://mp.weixin.qq.com/s/M-qiI071iceYMocKJytHrA | 2026-08-19 |
| [22] | DeepSeek Harness 架构解析：沙箱、Code Mode 与安全的未来 | 数字双生子（Byte Logic） | https://mp.weixin.qq.com/s/QbfUiGPE6yUBIDHUG5mDTg | 2026-08-19 |

其中 [19]–[22] 为同一作者的连载系列；[20] 明确引用了可逆计算理论（Y = X + ΔY）与 Nop 平台并给出 Nop vs DSH 对比表。

## 文件

文件名与上表标题一致（`标题.md`，冒号等文件系统不友好字符替换为空格/逗号）。正文由抓取工具转存为 Markdown，未保留图片；文首保留来源 URL 与作者信息。

## 使用注意

- 这些文章中的 **dsh 源码细节（路径、机制描述、统计数字）属于二手转述**，与本地 `~/ai/deepseek-harness` 工作区核对一致后方可作为事实引用；未核对的转述在引用时应标注"据 [n] 描述"。
- 生态数据（star 数、插件仓库数等）随时间漂移，引用时标注"写文时参考值"。
- 相关使用规划见 `ai-dev/articles/2026-08/2026-08-19-dsh-article-restructure-plan.md` §三（吸收清单）与 §7.3（待复核断言清单）。
