# Wave 10 — 服务化模式端到端实测

- Date: 2026-11-17
- Status: adjudicated
- 语料: 真实项目 `/Volumes/data/sources/liteflow`（testcase-el-nospring 233 tests，wave9 缓存绿在档）+ `/Volumes/data/sources/OpenPDF`；服务实例 `nop-ib serve --http --port 18188`（M4 Max 16 核，permits=8）；raw `_tmp/w10-e2e.log`、`_tmp/w10-t1-snap-*.json`、`_tmp/w10-t2b/t3b/t4b/t6-snap.json`、`_tmp/w10-t7-observe.log`

## 实测表

| 场景 | 预期 | 实际 | 判定 |
|---|---|---|---|
| T1 同项目并发 3 请求（liteflow status） | 串行排队、全部 exit=0 | job-1 [5255→5455]、job-2 [5456→5546]、job-3 [5546→5640]：startedAt(n+1) ≥ finishedAt(n) 严格交错，3×exit=0 | PASS |
| T2 双项目并发（liteflow test --force + OpenPDF status） | activeJobs ≤ permits(8) | 峰值 activeJobs=2，两 job 均 done | PASS |
| T3 `--cache` 经服务命中 | payload 结构化、kind=cached-up-to-date、cached=true | `payloadType=dict`，`{kind: cached-up-to-date, cached: true, run: 233, failed: 0, durationMs: 12}`（对照 `--force` 全量执行 ~44s） | PASS |
| T4 kill -9 后重启重放同请求 | exit 与结构字段一致（cached 标记允许漂移） | 重启后 exit=0、kind 仍 cached-up-to-date（磁盘状态幂等成立） | PASS |

## 过程发现（已修）

1. **symlink 别名触发模型过期闸门（正确行为）**：`~/sources/liteflow` 是 `/Volumes/data/sources/liteflow` 的 symlink；`discoverRepoRoot` 不解析 symlink → model.json 的 repoRoot 不匹配 → staleness 闸门强制 Maven fallback（exit=0、kind=maven-module(model-stale)）——fail-safe 方向正确；实测用真实 root 后走 fast path。
2. **payload NDJSON 解析**：CLI 输出是多 JSON 文档序列且字符串可含换行（failedDetail）——`JsonIn.silentParseSequence` 新增（复用自身递归解析器）；stdout 中的 `[nop-ib]` 进度行在解析前过滤；stderr 与 stdout 分离捕获（否则 Maven 诊断混入破坏 JSON）→ payload 解析失败时回退原始字符串（契约内披露）。

## 遗留

- JsonPath 等纯 Gradle 项目不在承接面（roadmap 非目标：跨语言构建）——服务返回显式 400 `not a Maven project`。
