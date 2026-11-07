# 测试时间确定化 Roadmap

> Last updated: 2026-11-07
> Sources:
> - 用户 2026-11-06 指令：「所有测试必须不依赖敏感的时间等待」
> - `ai-dev/logs/2026/11-06.md`（同日全 reactor 恢复全绿过程中两处偶发失败以放宽预算处理，与该指令相悖）
> - 盘点脚本：`_tmp/classify_test_sleeps.py`（临时，git 忽略；WI-3 晋升为 `ai-dev/tools/` 守卫。启发式，按 `Thread.sleep` 出现处的上下文分类；不覆盖墙钟 deadline 与真实定时器，后者逐模块人工核对）

## Purpose

把全平台单元测试从「靠真实时间流逝与固定等待」收口到「测试显式驱动虚拟时间、跨线程交接显式同步」，使测试结果与机器负载无关。判定口径见 `docs-for-ai/02-core-guides/testing.md` 的时间等待条目（WI-1 入册）。

## Evidence Ledger（2026-11-06 盘点）

测试源中 `Thread.sleep` / `TimeUnit.*.sleep` 共 530 处：

| 分类 | 处数 | 文件数 | 含义 |
|---|---|---|---|
| SLEEP_THEN_ASSERT | 113 | 68 | sleep 后紧接断言，期间无显式同步 |
| POLL_LOOP | 209 | 125 | 循环体内 sleep（轮询），敏感与否取决于上限是否承载语义 |
| SIMULATED_WORK | 17 | 12 | lambda / run() 内的模拟耗时 |
| OTHER | 191 | 122 | 其余（步骤间隔、时间戳错开等），逐处核对 |

SLEEP_THEN_ASSERT 集中模块（处数 / 该模块 sleep 总数）：nop-ai-agent 17/66、nop-stream-runtime 15/144、nop-ai-core 13/15、nop-stream-core 9/48、nop-auth-service 7/16、nop-nosql-lettuce 6/12、nop-ai-gateway 5/11、nop-message-pulsar 5/11、nop-commons 4/10、nop-graphql-core 4/11、nop-xlang-debugger 4/14、nop-xlang-debug-web 4/6，其余 13 个模块各 1–3 处。

## Work Items

> **这是唯一动态状态块。只在这里改状态。**

| WI | 内容 | 状态 | Plan |
|---|---|---|---|
| WI-1 | 调试子系统（nop-xlang-debugger / nop-xlang-debug-web）+ 规则入册 testing.md | done | `ai-dev/plans/2507-debug-subsystem-test-time-determinism.md` |
| WI-2 | 共享确定性时间测试工具（可推进时钟 / 手动调度器）的归属模块裁定与归并 | done | `ai-dev/plans/2508-shared-deterministic-time-test-tools.md` |
| WI-3 | 新增违规守卫：时间等待分类结果入 `ai-dev/tools/` 检查脚本，存量基线只减不增 | done | `ai-dev/plans/2509-test-time-waits-guard.md` |
| WI-4 | nop-stream（runtime / core / connector-*） | done | `ai-dev/plans/2510-nop-stream-test-time-determinism.md` |
| WI-5 | nop-ai（agent / core / gateway；shell / toolkit 同族面由 2511 一并承接） | done | `ai-dev/plans/2511-nop-ai-test-time-determinism.md` |
| WI-6 | nop-auth、nop-persistence（nosql-*）、nop-message（pulsar / kafka） | todo | — |
| WI-7 | nop-kernel（commons / xlang / xlang-truffle）、nop-service-framework（graphql-core / gateway） | todo | — |
| WI-8 | 其余模块（nop-code、nop-network、nop-datav、nop-metadata、nop-report-service、nop-sys、nop-job 等） | todo | — |

## Status values

- `todo`：未开始
- `in-progress`：plan 执行中
- `done`：plan closure audit 通过
