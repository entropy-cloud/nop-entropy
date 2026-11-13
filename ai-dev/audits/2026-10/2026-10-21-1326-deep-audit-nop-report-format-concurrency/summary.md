# 深度审核汇总报告 — nop-report + nop-format 并发·异步·资源专项审计（CRA WI 5.4）

## 基本信息

- **审核单元**: `nop-report-format`（campaign 最大单元：19 源码根 894f/102,861 lines——nop-report 143f/16,630 [api 27f/2,782 + core 61f/10,421 + pdf 14f/2,204 + docx 5f/443 + ext 3f/108 + service 12f/194 + dao 19f/261 + demo 1f/179 + app 1f/38] + nop-format 751f/86,231 [ooxml 125f/26,069 + record 122f/12,744 + excel 224f/15,023 + pdf 107f/13,635 + mermaid 39f/4,740 + svg 12f/3,684 + chart-export 29f/4,918 + converter 35f/2,085 + tablesaw 9f/1,276 + record-netty 9f/529 + markdown-ext 6f/429 + office-model 17f/756 + office-doc-model 17f/343；chart-echarts 纯 pom]）
- **审核日期**: 2026-10-21（Phase 1/初审/深挖）；2026-10-22（配额中断恢复：复核/C10/合并裁定/汇总收口）
- **执行维度**: C1 共享可变状态、C5 内存有界性、C6 资源生命周期、C9 并发高性能（roadmap 5.4 裁剪集，S 级按功能裁剪——Excel/OOXML/PDF 大文件流式 vs 物化为主战场）+ C10 测试保护力；C2/C3/C4/C7/C8 overlay 线索形态
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 6d2ca31438
- **方法论**: 盲审初审（按功能组批 A-D）→ 深挖迭代（每维度 2 轮）→ 独立复核 → C10 → 合并裁定
- **Plan**: `ai-dev/plans/2485-cra-wi5.4-nop-report-format.md`（R1 agent_a310ae0b 1 Blocker[编号冲突→2485]+5 Major+3 Minor 全修）
- **Protected Area**: `nop-report-api` 27f/2,782 plan-first——审计只读不受限，**0 张发现触碰 api 叶**
- **执行说明**: **2026-10-21 晚 Phase 2/3 执行期间遭遇配额中断**；恢复后核实初审 4 维度文件 + 深挖 4 round2 文件已完整落盘（格式门禁 26/26 通过），按产物接续执行；逐批 agent 记录以 dispatch-log「配额中断恢复记录」替代，批次覆盖完整性由 Phase 4 各维复核对照 19 源码根核验。全部子代理严格串行派发。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核补写 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|---------|------|------|
| C1 | 2 | 5 | +1 | +1（C1-006） | 7（P2×4/P3×3） | 0 | 0 |
| C5 | 2 | 6（P1×1） | +2 | 0 | 8（P1×1/P2×2/P3×5） | 2（C5-002/003） | 0 |
| C6 | 2 | 4 | +2 | 0 | 5（P2×1/P3×4） | 0 | 1（C6-R2-002） |
| C9 | 2 | 5 | +1 | 0 | 5（P2×2/P3×3） | 0 | 1（C9-004） |
| C10 | 1 | — | — | — | 25 行矩阵 + 缺保护 25 | 0 | 0 |
| **合计** | — | **20** | **+6** | **+1 = 27 实体** | **25** | 2 | 2 |

## 合并裁定

- 27 编号实体 → 复核保留 25（维持 25 / 跨维度调整 0 / 移交 1）+ 驳回 2 + 降级 2（折入 P3）。
- **P0×0/P1×1/P2×9/P3×15**；P0 即时通道显式空执行；P1×1（C5-001）移交 7.1 裁决通道（roadmap Phase 7 统一裁定）。
- 家族关联 5 处单列：C6-001=构造/失败路径泄漏族第 5 例；C9-002=锁内 I/O 族第 4 例；C1-002=全局注册表裸写族（与 C9-002 同站点互补面、方向相反，不合并）；C5-001=无界物化主题族机制新成员；C5-004=WI 1.7 C5-001 同缺陷第二实例（修复联动）。两组同站点互补对（C1-002↔C9-002、C1-003↔C9-001）裁定「单设计联合闭合」约束，防止单侧修复后另一侧恶化。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 1 | record 反序列化数据驱动长度无界物化（分配先于校验 + 文件/netty 双生产路径可达） |
| P2 | 9 | FontManager 锁内字体 I/O + 无锁写路径、DocumentConverterManager 内层裸集合、ExcelFormatHelper public 注册无守卫、RAF detach 契约违背、ExcelRecordInput 流式接口全表物化、rawVarName peek+hex 放大、C6-001 RAF 构造器泄漏、C9-001 日期格式缓存无界编译、C9-002 渲染热路径锁往返 |
| P3 | 15 | 各尾部条目（含降级 2：C5-002/003 零生产调用方潜伏缺陷） |

C10 缺保护登记 25 条（P1×1/P2×9 逐条 + P3×15 聚合 5 组）：矩阵 **会红 0/25**——campaign 第 7 例同型确认（4.5=第4/5.1=第5/5.2=第6/5.3 未记序，closure audit 裁定）；全单元测试套并发原语 grep 0 命中、nop-benchmark 对本单元 0 基准、3 个关键测试 @Disabled；P1 无保护（TestRecordRegionBoundary 只护短数据面，与立案的分配面正交），已建议以「lengthExpr 模型 + 超大长度断言 ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG」为修复准入测试。

## P0 记录

本单元 **P0 = 0**（显式空执行）。四维初审+深挖+复核全链无 P0 判级；nop-report-api 叶零触碰。

## 现状标注摘要

- 25 条全部为新发现（cra-index 对 report/format 零先期并发登记，跨单元查重 0 同站点重复）。
- **nop-format 无 owner doc**（占单元 84% 行数）——drift 登记维持；nop-report.md 对 format 族 0 提及。
- 主战场核实：record 反序列化链（C5-001/R2-001/C1-R2-001/C6-R2-001/C9-R2-001 五条集中于 BinaryDataReader 契约与长度面）为本单元缺陷密度最高点。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C5-001 | record 反序列化数据驱动长度无界物化：getFieldLength 无上限校验 + readBytes 先 `new byte[n]` 分配后读取——文件（ModelBasedResourceRecordIO←nop-batch FileBatchSupport）与 netty（ModelBasedPacketCodec）双生产路径 OOM 面；同族 CountArray 1M/DynLV 64MB/LV 相对长度三处有界保护反衬唯此无界；ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG 已存在可复用 | nop-format/nop-record 反序列化链 |

## 盲区自评

- 其余 5 维度（C2/C3/C4/C7/C8）为 overlay 形态：零候选实占，未做完整口径审计。
- **覆盖缺口登记（不补审，供后续轮次）**：四维重合零痕迹源码根 4 个（nop-tablesaw/nop-office-model/nop-office-doc-model/nop-record-netty——后者含 io.nop.record.netty vs io.nop.netty.ext 命名陷阱）；C5 额外：mermaid/svg/chart-export；C6 额外：converter/markdown-ext/ooxml-pptx；C9 额外：ooxml-pptx/mermaid/svg/markdown-ext。
- 配额中断导致逐批派发记录不可恢复，以产物证据 + Phase 4 覆盖核验替代（处置已记录于 dispatch-log 恢复节与本 plan Phase 2 修订注）。
- C1-006 系复核阶段补写立案（初审清单表引用了未落地的编号）——补写后表-实体一致，已由合并裁定终审。

## 与其他 roadmap 的重叠

- 与 WI 1.1–5.3 台账零同站点重复（本次一并补登 5.2/5.3 台账行缺口：P1 行×5 + 报告清单行×2）；家族关联 5 处单列（见合并裁定节）。
- security-audit roadmap：无重叠面。
