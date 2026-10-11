# 17: 全量回归的三重假象——陈旧 ~/.m2 工件、fail-fast 截断与被硬配置压制的 ignore 开关

> Date: 2026-10-10
> Severity: High — full-code-review 轮次 V.1（4 次全量 + 2 次补查才得到可信普查）；job 测试"回归"实为陈旧工件假象，而 lint 疑似回归实为负载 flaky——两类假象方向相反，不做甄别会同时产生漏报与误报

## 场景

V.1 全量回归（`./mvnw test -T 1C`）连续 4 次出现"仅 1 个失败"，且失败点各不相同（lint→job→stream→graphql），每次 reactor 因 fail-fast 在失败模块处截断（454 模块分别跑到 26,805/~40,218/40,218/18,840 tests）。逐项甄别发现三类互相叠加的假象：

**假象 A：陈旧 ~/.m2 工件制造假绿/假红。**
`TestNopJobFireBizModel`（同 fire 写两个 `taskNo=1`）在 HEAD 主树必现 duplicate-key（R1.9 物化的 UK `(jobFireId,taskNo)`），但在 6e521b4f28 与 10e95f133c 的 **git worktree** 里通过——因为 worktree 无 `-am` 时依赖解析落到 `~/.m2`，其中的 nop-job-dao snapshot 是 R1.9 之前的旧工件（内嵌 orm 不含该 UK）。结论方向完全反转："HEAD 相对基线的回归"实为**基线侧的陈旧工件假绿**；该测试数据缺陷自 R1.9 起一直存在，只是从未在新鲜工件上跑过。

**假象 B：fail-fast 截断让"全量"名不副实。**
每次全量只跑到第一个 flaky/失败模块，"仅 1 个失败"≠"全套只有 1 个失败"——只是"本轮最先撞上的 1 个"。三轮撞上三个不同模块的三个不同失败。

**假象 C：`-Dmaven.test.failure.ignore=true` 被根 pom 压制。**
根 pom `<testFailureIgnore>false</testFailureIgnore>` 为显式插件配置，CLI 属性无法覆盖（surefire 3.2.2），本想借它做完整普查的 run 4 仍在 nop-graphql-core 处失败截断。

## 根因

1. **模块级测试的依赖解析不区分"源码态"与"工件态"**：`-pl X`（无 `-am`）在 Maven 4 下落到 `~/.m2` snapshot——snapshot 的陈旧度决定测试看到的是哪个版本的被测依赖。"改 core 后须 install 再跑下游"（R1.20/R1.22 已知教训）的同源放大形态：**不止下游模块测试会读到旧 jar，基线对照实验（worktree）也会**。
2. **负载敏感 flaky 在满负载 `-T 1C` 下必然周期性出现**（lint dataflow 规则漏报、stream checkpoint 时序、graphql 两 loader 完成顺序致 JSON 键序漂移），单跑全绿。fail-fast 机制把"哪一个先撞上"变成随机事件。
3. **pom 显式配置优先于同名 CLI 属性**——用属性开关做临时策略前必须查插件配置是否硬编码。

## 正确做法

1. **模块级绿跑的结论限定工件态**：下"X 模块测试通过/失败"结论时，注明依赖来自 reactor 还是 `~/.m2`；对可疑差异用 `git worktree add <tmp> <commit>` + 全新构建做对照（worktree 是唯一能固定"源码态"变量的对照实验）。
2. **全量普查用"截断并集 + 未覆盖补查"**：允许各轮 fail-fast 截断，但每轮记录 reactor 进度（`Building X [n/454]`），对从未启动的模块用 `-pl :a,:b,...`（Maven 4 需 `:artifactId` 语法）补查；直到并集覆盖全部模块。
3. **甄别顺序**：失败先单模块复跑（区分负载 flaky vs 确定性）→ 再基线 worktree 复跑（区分本会话引入 vs 预存）→ 最后 `git diff <基线>..HEAD -- <模块>` 确认触碰面。三者齐全才能下"新增/预存"结论。
4. **负载 flaky 的归类纪律**：单跑绿 + 与本会话触碰面无交集 → 入预存失败清单 watch，不阻塞收口；但必须留清单（本次 3 条：lint dataflow 漏报、stream checkpoint 时序、graphql testLoad 键序）供后续测试加固（如结构化断言替代 JSON 字符串比较）。

## 判定规则

> **全量回归报告必须包含：每轮 reactor 进度（截断点）+ 失败清单的甄别证据（单跑结果、基线对照、触碰面 diff）+ 未覆盖模块的补查记录。** 缺任一项，"新增失败=0"不可声明。
>
> **模块级测试结果不可直接外推为分支级结论**——先问：依赖工件是哪个时代的？

## 适用范围

- 全量回归/发布前验证；模块级测试与分支级结论之间的任何推断
- 依赖 snapshot 的多模块仓（Maven 3/4 皆然；Maven 4 的 `-pl` 另需 `:artifactId` 语法）
