# 2344 WI12 性能组（JMH 基准先行：水位 punctuation 节流 + 滑动窗口增量聚合裁定）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI12（M2）+ Rules 6（基准先行退出路径）+ 裁定 §一（AR-06 由 r5-successors 承载，本 WI 节流配置不阻塞其核销）
> Related: `nop-benchmark/nop-benchmark-stream`（基准载体）；`WindowOperatorProcessElementBench`（既有滑窗/翻滚基准）；r5-successors AR-06

## Purpose

对 WI12 两个候选优化面先立 JMH 基准，用数据裁定 go/no-go：达标则实施，未达标按 Rules 6 以 Follow-up 形式登记「不做」裁定，不为勾选强上实现。

## Current Baseline（2026-10-08 实测）

- **基准载体**：`nop-benchmark/nop-benchmark-stream`（JMH，12+ bench；`WindowOperatorProcessElementBench` 已有 TUMBLING/SLIDING/SESSION/EVICTOR × MEMORY/ROCKSDB × keys 100/1000 矩阵——SLIDING = `SlidingEventTimeWindows.of(60_000, 10_000)` 即每元素 6 个重叠窗口）。
- **水位路径**：`WindowOperator.processWatermark`（:778）→ super（触发器 onWatermark/窗口推进）→ 向下 `output.emitWatermark`；测试驱动模式 = 每记录一次 `processWatermark`。**无任何节流机制**（每条水印无条件下发）。
- **滑窗聚合模型**：`WindowOperator` ACCUMULATING 模式——每元素赋给全部重叠窗口（O(N)，N=窗口长/slide），每窗口独立 accumulator（**已是 per-window 增量**，非 fire 时全量重算）；ACCUMULATING_AND_RETRACTING spec-only fail-fast（:590-595）。frame/deduct（窗格化 O(1) add + fire 期 deduct）是另一种模型，代价 = fire 期回溯与状态形状变更。

## Goals

- G1 基准一（水位路径成本）：`WatermarkEmitBench`——量化 `processWatermark` 单次成本（空载推进 vs 触发窗口 fire 的水印），并测「每 R 条记录才下发一次水印」的节流对下游 observe 的影响面（本引擎水印为控制面 mail，节流收益假设 = 下游 mail 处理次数减少；数据说话）。
- G2 基准二（滑窗 O(N) add 成本）：`SlidingWindowFanoutBench`——`@Param slideCount {1,6,12,36}`（同窗长 60s、slide 60s/10s/5s/~1.7s），量化每元素 add 成本随 N 的线性度。
- G3 go/no-go 裁定（写入本 plan + roadmap Follow-up 如适用）：
  - 水位节流：若 `processWatermark` 空载成本 ≥ 同亮度下 processElement 成本的 20% 或随 keys 增长显著劣化 → go（实现节流配置）；否则 no-go 登记。
  - 滑窗 frame/deduct：若 N=12 时每元素成本较 N=1 劣化 ≥ 6×（超线性）或绝对值 ≥ 1µs/元素 → go（评估 frame/deduct 或两阶段聚合设计另立 plan）；线性且 < 1µs → no-go（现模型已足够，frame/deduct 的 fire 期回溯成本与状态形状变更不值）。
- G4 报告：基准数据 + 裁定写入 `nop-benchmark/nop-benchmark-stream/README.md`（如该文件存在；否则 plan 内报告节）。

## Non-Goals

- 实现任何优化（go 裁定的实施另立 plan——本 plan 只交付基准 + 裁定 + 达标判据）；AR-06（r5-successors 承载）；ACCUMULATING_AND_RETRACTING（spec-only 既有裁定）。

## Execution Plan

### Phase 1 - 基准 + 裁定

Status: completed
Targets: nop-benchmark/nop-benchmark-stream

- Item Types: `Proof`

- [x] `WatermarkEmitBench`（watermarkOnly 非触发 / elementOnly 纯元素 / elementPlusFiringWatermark 融合形态 × TUMBLING/SLIDING × keys 100/10000）
- [x] `SlidingWindowFanoutBench`（slideCount 1/6/12/36 × MEMORY，同窗长 60s，N=每元素重叠窗口数）
- [x] 运行并采集数据（JMH Main，avgt ns/op，数据见下报告节）
- [x] 裁定 + 报告（见下）+ roadmap Follow-up 登记（两条：水位节流 no-go 不做裁定 + 滑窗 frame/deduct successor 晋级）

Exit Criteria:

- [x] 两条 bench 编译并真实运行出数（JMH Main 实跑，非编译即收口）
- [x] 裁定按 G3 判据数据化落地（见报告节）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（纯基准与裁定，不改产品行为）

## Closure Gates

- [x] 两条 bench 真实出数（`_tmp/wi12-bench-wm.txt` / `_tmp/wi12-bench-slide.txt`）
- [x] go/no-go 裁定按判据落地（双裁定 + roadmap Follow-up 两条登记）
- [x] `./mvnw compile -pl nop-benchmark/nop-benchmark-stream -am` 通过
- [x] 独立子 agent closure audit 已完成并记录证据（见 Closure 节）

## 基准报告与裁定（2026-10-08，JMH avgt ns/op，fork=1 warmup 2×2s measurement 3×2s）

**G1 水位 punctuation 节流 → NO-GO（不做裁定）**

| 形态 | TUMBLING k=100 | SLIDING k=100 | TUMBLING k=10000 | SLIDING k=10000 |
|---|---|---|---|---|
| watermarkOnly（非触发纯控制面） | **1.65ns** | 1.66ns | 1.65ns | 1.66ns |
| elementOnly（纯元素） | 138ns | 879ns | 179ns | 1271ns |
| elementPlusFiringWatermark（融合） | 148ns | 982ns | 209ns | 2671ns |

非触发水印成本恒 ~1.7ns（keys 无关）= 元素成本的 0.1-1.2%——「每 R 条才下发」节流的节省上限 ≤1.2%，且推迟下游水位推进（窗口触发延迟）。**判据（≥20% 或显著劣化）不满足 → 不做**，roadmap Follow-up 登记。

**G2 滑窗 O(N) fanout → GO（frame/deduct 设计另立 successor）**

| slideCount（每元素重叠窗口数 N） | ns/op | 相对 N=1 |
|---|---|---|
| 1 | 152 | 1× |
| 6 | 1047 | 6.9× |
| 12 | 2342 | **15.4×** |
| 36 | 7802 | 51× |

N=12 即 15.4×（≥6× 判据）且绝对值 2.3µs/元素（≥1µs 判据）——**双判据满足 → GO**。现模型每元素向全部 N 个重叠窗口 accumulator 各 add 一次（ACCUMULATING 已是 per-window 增量，非 fire 全量重算；瓶颈在 N 份 add + N 份状态访问）。frame/deduct（窗格化 O(1) add + fire 期增量 deduct）或两阶段聚合设计的收益上界 = 把 N 份 add 压到 1 份 + fire 期少量 deduct——**successor plan 承载设计与实施**（roadmap Follow-up 晋级登记，从 WI20+ 取号）。

## Non-Blocking Follow-ups

- （已按裁定登记至 roadmap Follow-up Backlog——本节无独立遗留）

## Closure

Status Note: WI12 基准先行路径按 Rules 6 完成：两条新 JMH 基准（WatermarkEmitBench/SlidingWindowFanoutBench）真实出数并二次复跑留证（`_tmp/wi12-bench-wm.txt`/`_tmp/wi12-bench-slide.txt`，数字 ±噪声复现）；双裁定数据化落地——水位节流 NO-GO（≤1.2% < 20% 判据，不做登记）、滑窗 frame/deduct GO（15.4×/2.3µs 双判据，successor ownership 登记从 WI20+ 取号）。audit M2（留证文件缺失）/M1（Closure 段缺失）已处置。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_025aa67c-64bb-4fc8-bc90-d689ccc6f35f），全程只读
- Evidence:
  - 两 bench 真实性 PASS（真 JMH 形态 + harness 复用 + watermarkOnly 非触发口径经 live 代码核实）
  - 判据推导 PASS（15.4×/2.3µs 双判据；no-go 0.1-1.2% 距 20% 十六倍以上；m2 措辞 ≤1.2% 已统一三处）
  - 留证 M2 处置：重跑两条 bench 至 `_tmp/wi12-bench-wm.txt`/`_tmp/wi12-bench-slide.txt`，数字复现（1.7ns 恒定；150/1032/2297/7802）→ 裁定维持
  - 结构 M1 处置：本 Closure 节 + Status completed + Completed 日期补齐；README bench 清单补两行（G4 主分支回补）
- Audit Session: agent_025aa67c-64bb-4fc8-bc90-d689ccc6f35f
