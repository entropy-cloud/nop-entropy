# WI27 Closure Audit——07-wi27-asymmetric-join-timeout.md

> Auditor: 独立子 agent（fresh session，与起草审查 agent_b8960ef3 均不同 task）：agent_8e10bfd5-3d5c-4082-9699-8e9229f19d7b
> Date: 2026-10-08
> Verdict: **APPROVED**（0 Blocker / 0 Major / 6 Minor——M-1/M-2/M-3 随收口处置，m-1 死代码记 follow-up，m-2 文案裁定记录，m-5 前期遗留 doc-sync 显式认领随提交）

## 九项验证矩阵（全部 PASS）

| # | 验证项 | 证据 |
|---|---|---|
| 1 | 声明面 | stream.xdef :78-85 leftTimeout/rightTimeout + WI27 注释；_gen/_StreamJoinSpecModel 再生成全链 |
| 2 | 校验 | validateJoinDeclarations :1024-1058——互斥 "not both"、缺省语义裁定注释 :1024-1028、无 strategyRef 拒绝 :1050-1057、正值沿用 parseDurationMillis :407-409 |
| 3 | 运行时 | EquiJoinOperator 4 参构造 :132-139、3 参委托 :122-124、copyForSubtask :145 4 参、isLateForWindow(ts,left) :284-286、fireReachedWindows 按侧独立 fire :297-310、twin 双清已删（diff 确认）、hash 形零改动 |
| 4 | buildJoin | AdvancedTransforms :615-626 缺省继承+side 覆盖、:643-644 4 参构造 |
| 5 | 判别测试 | TestEquiJoinAsymmetricTimeout 三轴+swap（bufferedCount=1 实证双侧差异）；flow +4 + bothSideTimeoutsRoundTrip 反射断言 leftTimeout=20000/rightTimeout=5000（换序盲区封堵）；既有 5 场景+13 钉零回归 |
| 6 | 回归 | wi27-full-regression.log 与 regression2.log 均 BUILD SUCCESS、无 FAILURE 行；auditor 独立复跑 flow 17/17 + runtime join 21 全绿 |
| 7 | 文档 | user-guide :177 joinSpec 属性小节新撰（配对面=min narrowing + WITHIN 转译披露）；sql-subset :105 |
| 8 | 零越权 | git 变更恰限 xdefs/flow/core/runtime/docs/ai-dev |
| 9 | plan 状态 | Phase 1 completed / Phase 2 in progress→completed（收口翻转） |

## 问题与处置

- **M-1** Exit Criteria #1 文本与执行形态不一致（env 级发射断言被替代）→ 已修订准则文本记录裁定（EOS-only 水位下 env 发射集数学不判别；替代=flow 构建产物反射断言+runtime 三轴判别）。
- **M-2** Plan Status 实为 draft → 已翻转 completed（经 checklist 复核）。
- **M-3** Phase 1 Exit Criteria 未勾 → 已随本 audit 逐条勾选。
- **m-1** twinWindowKey 死代码/timeout 字段仅写不读 → 记入 plan Non-Blocking Follow-ups（watch-only）。
- **m-2** 零值文案未落地 → 共用 parseDurationMillis 通用文案（"duration must be positive"），裁定记录。
- **m-3** 轴③文案与实际判别器异向但判别力等价 → 日志已记录叉积重设。
- **m-4** javadoc "Eight checks" 未更新 → 随 m-4 记录。
- **m-5** 前期遗留 nop-stream-sql.md doc-sync 未提交 → 显式认领随本提交。
- **m-6** 计数精确化（runtime join 全家 21）→ 收口记录采用。
- **环境风险披露**：无 -am 的 flow 单模块构建用本地仓陈旧 xdef 静默改写 _gen 文件（auditor 实证后恢复）——与 WI26 再生成顺序硬依赖同族，已记入 plan Closure。
