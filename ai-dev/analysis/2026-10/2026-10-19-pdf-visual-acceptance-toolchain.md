# PDF 视觉验收工具链口径裁定（WI0）

> Status: resolved
> Date: 2026-10-19
> Scope: nop-format/nop-pdf（io.nop.pdf.verify）、nop-report/nop-report-pdf 及 nop-report-demo 的渲染类验收（WI1–WI11、WI18 复用）
> Conclusion: 视觉验收 = 固定 110 DPI 渲染 + `_tmp/` golden 快照（同机像素一致门）+ 逐像素通道容差/SSIM diff + 区域结构采样断言（跨环境可移植门）；工具落 `io.nop.pdf.verify`（main scope），执行口径为「先建 golden → 改渲染 → diff 必红 → 修复 → diff 转绿」。

## Context

- roadmap `ai-dev/backlog/nop-pdf-report-export-roadmap.md` WI0 要求建立"实际渲染截图 + golden/图像 diff"验收能力，作为全部渲染类 WI 的完成门（Cross-Cutting 第 2 条"视觉优先红线"）。
- 仓库此前仅有 `TestPdfExportAudit`（110 DPI 逐页 PNG 落 `_tmp/`，人工查看，无断言）。
- 本文档裁定工具口径，供后续 WI 与 closure audit 引用；工具实现见 `nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`。

## 裁定

### D1. 渲染 DPI = 110（默认）

- `PdfImageRenderer.DEFAULT_DPI = 110`，与既有 `TestPdfExportAudit` 基线一致，避免历史审计证据换口径。
- 渲染用 PDFBox `PDFRenderer.renderImageWithDPI`（纯 Java 光栅化、固定 RenderingHints）：同一 JVM + 同一平台下，同一文件两次渲染像素级确定；golden 同机重渲稳定成立。
- A4 @110dpi ≈ 909×1287 px，单页 PNG 约 20–60 KB，`_tmp/` 体量可控。

### D2. 抗锯齿归一化 = 逐像素通道容差 8/255

- 两个像素视为相等：R/G/B 每通道差 ≤ 8（`ImageDiff.DEFAULT_CHANNEL_TOLERANCE`）。理由：PDFBox 抗锯齿在图形边缘产生半透明过渡像素，容差吸收边缘抖动；8/255 ≈ 3% 亮度差低于人眼可辨的布局性差异。
- 差异占比阈值：`ImageDiffResult.assertClose(maxDiffRatio, minSsim)` 显式传参；`RECOMMENDED_MAX_DIFF_RATIO = 0.001`（0.1%）为占位建议值。语义：**回归判定以"差异像素占比"为主指标**，SSIM 为结构辅助指标。

### D3. SSIM 口径

- 灰度化：luma（0.299R + 0.587G + 0.114B）；8×8 像素窗口、标准常数 C1=6.5025、C2=58.5225；对全图所有完整窗口求均值（右/下不足一窗的边缘忽略）；图像任一边 < 8 px 显式抛 `IllegalArgumentException`（PDF 页面永不触发）。
- 相同图像 SSIM ≡ 1.0（含常量窗口，由 C1/C2 稳定项保证）。
- 纯 Java 实现（约 60 行），不引入 OpenCV/imgdiff 等外部依赖。

### D4. golden 快照：`_tmp/` 不入库

- golden 根目录 = `<repo-root>/_tmp/pdf-golden/<fixture>/`。`.gitignore:123` 已忽略 `_tmp/`。
- 不入库理由：标准 14 字体（Helvetica 等）**非内嵌**，渲染时按环境字体替换（D5），跨机器像素相等不可移植；入库的 golden 会在异机 CI 上必然红，制造虚假回归。**跨环境可移植门是区域结构断言（D6），不是全图 golden。**
- golden 缺失语义：首跑自动创建基线并报告 `BASELINE_CREATED`（`assertPass()` 抛错并提示审查）——**基线创建不伪装成 pass**（No Silent No-Op）。
- 更新流程：渲染结果经人工审查确认正确后，(a) 删除对应 PNG 让下轮重建，或 (b) `-Dnop.pdf.golden.update=true` 重跑（`PdfGoldenManager.UPDATE_MODE_PROPERTY`），差异覆盖后报告 `UPDATED`（同样不伪装 pass）。
- golden 比对判据 = 像素完全一致（容差 0）。同机渲染确定性（D1）使该判据稳定；需要容差比对时调用方直接用 `ImageDiff.compare` 自行传阈值。

### D5. 字体环境替换的确定性边界

- PDFBox 对未内嵌的标准 14 字体做环境字体替换：不同 OS/安装字体会改变文本光栅化，甚至字形宽度。因此：
  - verify 工具自身的单测 fixture **只画矢量图形（矩形/直线），不画文本**，彻底规避该变量；
  - 含文本页面的 golden 只在**同一台机器**上有效；跨机器（CI/异机）验收一律用 D6 区域断言或文本级断言；
  - 该边界是 nop-report CJK 字体策略（`ai-dev/design/nop-report/pdf-font-strategy.md` D3：CI 需 CJK 字体）的独立补充——即使字体发现一致，标准 14 字体的替换差异仍存在。

### D6. 区域结构采样：跨环境可移植断言门

- `RegionProbe.averageColor`：矩形区域平均色（探测填充形状内部时精确等于填充色，抗锯齿边缘被平均掉）。
- `RegionProbe.measureHorizontalStrokeWidth / measureVerticalStrokeWidth`：扫描线上与背景色差超容差的像素计数 = 线宽估计；物理期望值 `pt × dpi / 72` px（2pt @110dpi ≈ 3.06 px，断言区间 ±1–2 px 吸收抗锯齿）。
- 用法：对"线宽/颜色/布局是否正确"类断言（视觉优先红线的核心场景），以物理坐标换算 + 区域采样写成 repo-observable 断言；golden 只承担同机回归。

### D7. verify 包落位与异常分工

- 落位裁定：`nop-format/nop-pdf` **main scope**（`io.nop.pdf.verify`），偏离 roadmap 原文"测试支撑包"措辞。理由：(a) nop-report 侧（nop-report-pdf test / nop-report-demo test）以普通 test-scope 依赖即可消费，免 test-jar 双端配置；(b) 包定位是"PDF 视觉验收支撑原语"，属 nop-pdf 通用工具箱能力面（渲染/比对/探测），非领域 API；(c) 不污染 nop-autotest（否则 pdfbox 依赖被强加给全部测试场景）。
- 异常分工：断言辅助（`assertClose`/`assertPass`）抛 `AssertionError`（JUnit 原生呈现，含期望/实测/阈值与修复指引）；IO 错误沿用模块 `NopException.adapt` 惯例；参数/状态错误 `IllegalArgumentException`/`IllegalStateException`（Guard）。不新增 ErrorCode 注册——该包不是业务 API 错误通道。
- 依赖边界不变：nop-report-pdf 生产依赖仍不含 nop-pdf；接入发生在后续 WI 的 test-scope 依赖。

### D8. 后续渲染类 WI 的验收操作口径

1. 写测试构造/复用模板 fixture → 首跑生成 golden（`BASELINE_CREATED`）。
2. 人工查看 `_tmp/pdf-golden/<fixture>/*.png` 确认基线正确。
3. 断言写法：golden `assertPass()`（同机回归）+ 区域采样断言（可移植结构门）+ 必要的文本级断言。
4. 修改渲染逻辑前先确认"扰动必红"：临时改动（如颜色/线宽偏移）应使 golden 或区域断言失败——证明断言真的在测（变异探针纪律）。
5. 修复后 diff 转绿；渲染语义变更时按 D4 更新流程重建 golden 并在 plan/日志记录重建原因。

## 参考实现索引（`~/sources/`，仅对版/设计参考，不引入运行时依赖）

| 仓库 | 用途 | 状态 |
|------|------|------|
| apache/pdfbox | 渲染引擎本尊；`examples` 有 render/merge/split 样例 | 就位（有效克隆，含 pom） |
| LibrePDF/OpenPDF | 装配/盖章/水印 API 设计对版（WI12/WI13） | 就位 |
| dromara x-easypdf（实际仓库 xsx344646090/x-easypdf） | PDFBox 上层封装的 API 设计对版 | 就位（URL 勘误后克隆） |
| TIBCOSoftware/jasperreports | 报表→PDF 导出的样式/分页语义对版（WI1–WI11） | 就位 |
| `~/sources/print/`（report-designer、openprint、fastprint-designer、vue-plugin-hiprint、vue-print、open-press、myprint、vue-print-designer） | 版式/打印语义 UI 参考 | 就位（8 个设计器） |

**x-easypdf URL 勘误**：roadmap 所记 `dromara/x-easypdf` 已失效（勘误核实 404；`~/sources/x-easypdf.clone.log` 记录对旧 URL 的 60 次网络重试全失败）。实际仓库为 `xsx344646090/x-easypdf`，已用 `~/sources/download-github.sh` 重试脚本克隆成功。roadmap 该行由 WI18 收口时一并勘误。

## Conclusion

- 工具能力：`PdfImageRenderer`（渲染）、`ImageDiff`/`ImageDiffResult`（diff）、`PdfGoldenManager`/`GoldenCompareResult`（golden 生命周期）、`RegionProbe`（区域采样）四类原语 + package-info 口径说明，16 个单测全绿（含 golden 创建→pass→扰动必红→更新恢复生命周期、SSIM=1/扰动<1、尺寸不一致显式失败、2pt 线宽 3.06px 采样命中）。
- 被否决方案：golden 入库（跨机器字体替换必红，制造虚假回归）；nop-autotest 承载（强加 pdfbox 依赖）；外部图像库（SSIM 自实现足够，避免新依赖）。
- 后续工作：WI1–WI11 各 plan 按 D8 口径接入本工具；WI18 端到端收口时勘误 roadmap 参考仓库行并汇总能力矩阵。

## Open Questions

- [ ] 无（占位默认阈值 0.1%/0.99 若在 WI1 实战中频繁误报/漏报，由首个消费 WI 的 plan 裁定修正并回写本文档）

## References

- `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI0、Cross-Cutting 视觉优先红线）
- `ai-dev/plans/2463-wi0-pdf-visual-acceptance-toolchain.md`（本裁定的承载 plan）
- `ai-dev/design/nop-report/pdf-font-strategy.md`（CJK 字体策略，D5 相关）
- `ai-dev/design/nop-report/pdf-pagination.md`（分页 D1–D4）
- 工具源码：`nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`
