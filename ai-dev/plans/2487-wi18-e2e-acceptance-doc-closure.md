# 2487 WI18 端到端验收与文档收口

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI18，deps: WI1-WI17）

## Purpose

PDF 能力全链路验收通过 + 文档/路由收口。

## Current Baseline

WI0-WI17 全部完成（各 WI closure audit 全 PASS）。模块测试 nop-report-pdf 168 + nop-pdf 168 全绿。TestPdfExportAudit 20 模板全绿。card-kb amex 样本 outline 对版 39/39 一致。

## Closure

Status Note: WI0-WI17 全部独立完成并提交（各 WI closure audit 全 PASS）；文档收口完成（PDF 能力矩阵 docs-for-ai/03-modules/nop-pdf.md 落档：导出/装配/抽取三面 + 已知限制）；构建恢复遍证据见各 WI Closure 段。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: WI0-WI17 各自独立 closure audit 全 PASS（累计 7 个独立审计子代理：agent_ac7f17a1/e9c39d65/1fc031fd/0656d79b/01226a00/7b07114e/c70657a7 + WI11-WI17 各自 auditor）
- Evidence:
  - export 侧：TestPdfExportAudit 20 模板全绿（nop-report-demo）；各 WI golden + 人工截图抽查 PASS
  - 抽取侧：card-kb amex 样本 outline 对版 39/39 一致（WI14）；目录页三形态/路由/回填由 35 个测试钉住（WI15）；版面文本/锚/扫描/乱码由 9 个测试钉住（WI16）
  - 装配侧：merge/stamp/watermark/pageNumbers/fromImage 由 15 个测试钉住（WI12）；元数据/加密 5 个（WI13）
  - 文档收口：docs-for-ai/03-modules/nop-pdf.md（能力矩阵）+ 5 篇 design 子文档落 ai-dev/design/nop-report/
  - 构建恢复遍：各 WI Closure 段记录排除清单（均单跑复核绿或预存缺陷已上报）
  - WI18 终局全量构建（2026-10-20 补录）：首遍 `-T 1C` 恢复遍完成 200/456 模块后在 4 个模块挂死；`jstack` 定位根因为 8 个测试类的无界 Docker 探测（本机 Docker Desktop 半挂死遗留陈旧 socket）——与 PDF roadmap 无关的测试基建缺陷，同族修复 8 处（变体 A testcontainers clinit 无读超时 ×8、变体 B drain-before-wait ×2 方法），6 个受影响模块串行 solo 复核 BUILD SUCCESS（debezium 70/pulsar 73/kafka 98/stream-runtime 1212/ai-toolkit 302/job-local 50，skip 均为 Docker 门控 E2E 设计内可见跳过）。根因分析见 `ai-dev/bugs/2026-10/2026-10-20-testcontainers-detect-docker-blocking-clinit.md`。

## WI 完成清单

- WI0: 参考实现 + 视觉验收工具链（plan 2463）
- WI1: Print_Titles（plan 2464）
- WI2: fit 缩放（plan 2465）
- WI3: 分页边界（plan 2466）
- WI4: 页眉页脚占位符（plan 2467）
- WI5: 页眉页脚富文本（plan 2469）
- WI6: 图片嵌入保真（plan 2468）
- WI7: 图表嵌入接线（plan 2470）
- WI8: 边框线宽/线型（plan 2471）
- WI9: 颜色保真（plan 2472）
- WI10: 字体渲染保真（plan 2473）
- WI11: 富文本/竖排/数字格式（plan 2480）
- WI12: 装配 API（plan 2481）
- WI13: 元数据与保护（plan 2482）
- WI14: outline 抽取（plan 2483）
- WI15: 目录检测/路由/回填（plan 2484）
- WI16: 版面文本/页锚/扫描乱码（plan 2485）
- WI17: 图片普查/统计（plan 2486）
- WI18: 本 plan（端到端验收 + 文档收口）
