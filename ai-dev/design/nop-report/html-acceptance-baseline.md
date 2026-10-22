# HTML 打印/交互 验收基线 设计

**范围**：`nop-report` HTML 渲染相关能力（分页打印、交互、条件格式等）的验收口径
**状态**：草案（规范性文档；实施编排见 `ai-dev/backlog/nop-report-capability-roadmap.md`）

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. HTML 相关能力的完成判定以**浏览器实际渲染结果**为准：视觉面用 headless 浏览器（Playwright）截图，行为面用 DOM 断言；禁止只看 HTML 文本源判定完成。
2. 视觉验收复用 PDF 视觉工具链的**口径**（渲染 DPI 口径、golden 快照、像素/区域 diff、容差裁定），工具实现为 HTML 侧独立设施——不建立 nop-report 对 nop-pdf 测试支撑包的模块依赖。
3. golden 快照断业务不变量，防快照漂移：字体渲染差异、浏览器版本差异通过基线环境固定（headless Chromium 锁版本）与容差带吸收。
4. 每个 HTML 能力交付必须同时给出：golden 截图（首屏 + 分页打印形态）、行为断言用例、以及已知环境差异说明。

## 二、背景与动机

HTML 渲染结果依赖浏览器排版，纯文本比对（HTML source diff）无法发现分页断裂、表头缺失、样式漂移等缺陷；而人工看截图不可回归。平台在 PDF 侧已建立「实际渲染截图 + golden/diff」验收体系（`io.nop.pdf.verify` 的 golden 管理与 `ImageDiff`/`RegionProbe` 口径），HTML 侧需要同等严格度的对应物。

## 三、核心设计

### 验收分层

| 层 | 手段 | 通过标准 |
|----|------|----------|
| 行为断言 | Playwright DOM 断言（超链接 href/钻取目标、排序/折叠交互后的 DOM 变化、条件可见性、重复表头在每打印节存在） | 断言全过 |
| 视觉分页 | headless Chromium 以打印媒介分页截图（逐页 PNG） | golden diff 在容差带内 |
| 视觉样式 | 指定区域采样（边框线宽/颜色/背景），沿用 `RegionProbe` 口径思想 | 采样值在容差带内 |

### 执行口径

- 截图环境：headless Chromium 固定版本 + 固定视口 + `prefers-print` 打印媒介 emulation；环境差异（字体缺失等）在 CI 环境前提中登记。
- golden 存放与更新：golden 属测试资产随用例存放；更新 golden 必须在提交说明中给出渲染差异理由（与 PDF 侧 golden 纪律一致）。
- diff 容差：整页像素容差 + 关键区域零容差（重复表头区、页脚占位区）；容差数值沿用 PDF 视觉验收裁定的量级，首个落地能力（HTML 分页打印）执行时固化并回填本档。
- 与 `nop-entropy-e2e`（Playwright 基座）的关系：端到端用户流走 e2e 工程；渲染级 golden/diff 属模块测试资产，落在被测模块测试树。

### 边界

- 本档约束 HTML 输出能力的验收，不约束 PDF（PDF 有自己的 golden 工具链）与 xlsx（结构断言足够）。
- 不要求所有 HTML 变更都做像素 diff：仅 HTML 渲染能力交付与回归时强制；普通模板示例变更走行为断言即可。

## 四、拒绝了什么

| 替代方案 | 拒绝理由 |
|----------|----------|
| HTML source 文本 diff 作完成判定 | 排版结果不可从源文本推断；分页/样式缺陷全部漏检 |
| 直接复用 `io.nop.pdf.verify` 类 | 模块依赖方向错误（报表 HTML 测试依赖 PDF 库）；复用的是口径不是代码 |
| 人工截图审阅替代 golden | 不可回归；历史上人工看图阶段已证明漏检 |

## 五、与已有设计的关系

- `html-pagination.md`：分页打印交付物按本档验收。
- PDF 视觉验收工具（`nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`）是口径来源（golden 管理/图像 diff/区域采样），非依赖对象。
- `nop-entropy-e2e/README.md`：端到端用户流验收的承载工程。
