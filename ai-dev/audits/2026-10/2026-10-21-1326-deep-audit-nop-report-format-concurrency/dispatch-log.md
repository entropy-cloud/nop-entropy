# Dispatch Log — CRA WI 5.4 nop-report-format 并发审计

- **Plan**: `ai-dev/plans/2485-cra-wi5.4-nop-report-format.md`（active；R1 agent_a310ae0b 1 Blocker[编号 2484 重复→2485]+5 Major+3 Minor 全修）
- **基线**: commit 6d2ca31438；基线文件 `_tmp/cra-baseline-nop-report-format.txt`（12 命令组 × 19 源码根 = 1,737 命中行）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-report-api 27f/2,782 plan-first——审计只读不受限；P0 即修仅限 impl 叶

## 源码根实测（Phase 1 回填，src/main 手写口径）

**19 源码根**：nop-report（api 27f/2,782 + core 61f/10,421 + pdf 14f/2,204 + docx 5f/443 + ext 3f/108 + service 12f/194 + dao 19f/261 + demo 1f/179 + app 1f/38 = 143f/16,630）+ nop-format 13 有源子模块（ooxml 125f/26,069 + record 122f/12,744 + excel 224f/15,023 + pdf 107f/13,635 + mermaid 39f/4,740 + svg 12f/3,684 + chart-export 29f/4,918 + converter 35f/2,085 + tablesaw 9f/1,276 + record-netty 9f/529 + markdown-ext 6f/429 + office-model 17f/756 + office-doc-model 17f/343 = 751f/86,231；chart-echarts 纯 pom）= **894f/102,861**。

## 预注锚点核实（Phase 1，R1 实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| C1 细化口径 | 26 命中/8 文件（ExcelDateHelper 16/FontManager 3/MarkdownNormalizer 2 等） | C1 重点 |
| C5 全量物化 | 30 命中（record reader 族为主——IBinaryDataReader 8/BinaryReaderHelper 5 等） | C5 主战场 |
| C6 资源获取 | 7 命中 + 宽口径 8 命中 | C6 面 |
| C9/C2 锁 | 25 命中（FontManager 7/DocumentConverterManager 2 等） | C9 重点 |
| Protected Area | nop-report-api 命中 plan-first | P0 即修仅限 impl 叶 |
| nop-format owner doc | 不存在 | drift 登记 |

## owner doc 漂移登记

- nop-report.md 58 行/0 处提及 format/excel/ooxml/pdf；**nop-format 无 owner doc**（占单元 84% 行数）——按 drift 登记。

## 历史审计对照口径

- `ai-dev/audits/` 下 report/format **并发主题**直接审计 0 命中。

## 维度级派发记录

（Phase 2 起逐条追加）

## 配额中断恢复记录（2026-10-22）

- Phase 2/3 执行期间（2026-10-21 晚）遭遇配额中断；恢复后发现以下产物已完整落盘，逐批 agent 记录随中断丢失：
  - 初审 4 维度文件：`C1-shared-mutable-state.md`（5 发现）/`C5-memory-bounds.md`（6，含 P1×1）/`C6-resource-lifecycle.md`（4）/`C9-hot-path.md`（5）
  - 深挖第 2 轮 4 文件：`C1-round2.md`（1）/`C5-round2.md`（2）/`C6-resource-lifecycle-round2.md`（2）/`C9-hot-path-round2.md`（1）——各文件以全-safe 清扫表收尾（终止信号）
- 主 agent 恢复后核验（2026-10-22）：格式门禁 26/26（触发时序+失效面枚举+严重程度）；C1 锚点核对（ExcelDateHelper 16 命中全为 static final Pattern/ThreadLocal，误报排除）随 C1 文件落盘保留。
- 处置：Phase 2 exit 以落盘产物 + 上述核验为证据；批次覆盖完整性（批 A=excel/批 B=ooxml/批 C=pdf+record/批 D=report+小模块）由 Phase 4 各维度复核子代理对照 19 源码根逐项核验。

## Phase 4 维度级派发记录

1. C1 复核（agent_d4e87146，串行 1/4）：保留 7（P2×4/P3×3，无驳回无降级）；C1-006 缺失实体补写立案 P3（FieldCodecRegistry static DEFAULT 非 final）；R2-001 行号更正 345-352→430-442 并扩 ByteBufferBinaryDataReader 同构基；SHAPE_TYPES 13→12 条目更正。覆盖缺口登记：tablesaw/office-model/office-doc-model/record-netty-ext 零痕迹。
2. C5 复核（agent_fb7958b1，串行 2/4）：保留 8（P1×1/P2×2/P3×5）；C5-002/003 降级 P2→P3（零生产调用方实证）；P1 C5-001 核实且证据强化（readBytes 先分配后读 + 两条生产路径可达 + ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG 可复用）。覆盖缺口：+mermaid/svg/chart-export。
3. C6 复核（agent_7ffba455，串行 3/4）：保留 5（P2×1/P3×4）；**驳回 C6-R2-002**（use-after-close 全调用序不成立：ScopeIdentifierExecutable 仅持名解析 + 唯一消费链不求值表达式）；R2-001 确认回归地雷不触发。覆盖缺口：+nop-converter/nop-markdown-ext/nop-ooxml-pptx；chart-export 对 C6 非缺口。
4. C9 复核（agent_d19e7855，串行 4/4）：保留 5（P2×2/P3×3）；**驳回 C9-004**（readBytesTerm 全仓零生产调用方，频率=0 量化链不成立）；C9-002 保留附校准（per-document fontCache 缓解，保留依据改为 CJK 回退秒级持锁）。

**Phase 4 汇总**：26 发现（初审 20 + R2 6）+ C1-006 补写 1 = 27 实体；保留 25 = **P1×1 + P2×9 + P3×15**；驳回 2（C6-R2-002/C9-004）；降级 2（C5-002/003）。全维零 P0。

