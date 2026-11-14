# nop-ib 增量编译与测试（AI 日常入口）

> 状态: 规范性文档（Wave 0–5 已落地）。
> 设计权威与执行状态见 AI 开发记录区的设计文档 05 与单文件 roadmap（按边界规则此页不直接链接 ai-dev 路径）。

## 何时用

AI 会话内的日常编译/测试反馈回路。**替换的是反馈回路，不是构建系统**: CI/发布/codegen/全量冷构建/`install`/`deploy` 仍必须走 `./mvnw`（经 `mvnq`）。**回滚开关**: 任何时候直接用 `./mvnw` / `mvnq` 即可，`nop-ib` 不占用任何排他资源（锁与 mvnq 共用同一把）。

## 前置（每个 worktree 一次性）

```bash
./nop-tools/nop-ib/bin/ib model refresh   # 导出 Maven 构建事实（~50s）
./nop-tools/nop-ib/bin/ib model index     # 扫描产物建单元索引（~4s）
```

模型过期（任一 pom 变化）会让所有快路径结论失效——此时先 `model refresh`，或直接回退 Maven。

## 命令面

| 命令 | 用途 |
|---|---|
| `ib status [--json]` | 模型/索引健康、当前变更集 |
| `ib plan --paths <files...> [--json]` | 只算不做: 编译集/测试集/回退原因/下游候选 |
| `ib compile --paths <file...>` / `ib compile -pl <module>` | 选择性编译（写产物取 `.mvn/build.lock`,锁超时退出 75） |
| `ib test --affected` / `ib test -pl <module>` / `ib test --class [mod:]Class` / `--rerun-failed` | 自研 JUnit Platform fork 启动器执行（每类隔离 fork+4 并行;launcher 版本自模块 classpath 推导;快照录制显式禁用;批超时看门狗）。`--runner surefire` 可退出到 Maven Surefire 委托（setup 不满足时也自动显式回退） |
| `ib check --paths <file...>` | **AI 日常主入口**: 编译 + 受影响测试 |
| `ib explain <file\|module>` | 解释归属/能力分级/依赖者 |
| `ib model refresh\|show\|index` | 模型与索引维护 |
| `ib bench <module> [--json]` | 四类成本基线实测 |
| `ib serve` | JSONL 常驻协议（`{"id","cmd","args"}`） |

退出码: `0` 成功; `1` 编译/测试失败; `2` 回退被 `--on-fallback=fail` 拒绝; `3` 用法错误; `6` partial; `75` 锁超时。

## 正确性语义（调用方须知）

- **零假绿**: 回退、`partial`、`wideImpact` 全部显式出现在人类输出与 JSON；未执行的测试不会计为通过。
- **回退是显式状态**: pom 变更 / XLang 模型或模板变更 / L2 注解处理器模块 / L3 AOP 模块 / 模型过期 → 自动委托 Maven（`mvnq`），并给出原因。
- **产物一致性**: 快路径编译与 `mvn compile` byte 级对拍零差异（-g/--release/-proc 通道对齐）；对拍 harness 在 `nop-tools/nop-ib/tools/ab_compare.py`。
- **能力分级**（产物证据法）: L0 快路径 343 模块 / L1 xgen 模板 92（模板变更回退,手写源码快路径）/ L2 注解处理器 12（恒回退）/ L3 AOP 产物 10（恒回退）。
- **模块串行 + 拓扑序**执行；产物目录与 Maven 共用，禁止 intra-project jar 进 fast-path classpath。
- **仓库分层**: 本项目模块间依赖=reactor 输出目录（永远最新，永不 install）；第三方依赖=公共仓（`~/.m2/repository`，可用 `NOP_IB_COMMON_REPO` 覆盖）**只读解析**；worktree 私有仓（`.m2-repo`）仅作冷路径回退时的 fallback，且 nop-ib 从不写任何仓库。本项目编译产物只存在于各 worktree 的 `target/`，天然无跨 worktree 竞争。

## 实测基线（本机,勿外推）

- `noop`（status+plan）0.8–1.2s;单文件方法体编译 0.7–1.5s（Maven 骨架 3.4s）;ABI 变更扩散 5 下游 11 文件 1.37s;`check` 端到端 4.5s。
- 刷新模型 52s（457 reactor project）;全量索引 4s（383 模块）。
- **`test` 对 `mvn test`（2026-11-14）**: 4 模块切片中位数 **9.2s vs `mvn test -T 1C` 11.9s**;单模块对照（orm-geo/spring-core-starter/stream-connector-jdbc 14/15/71 tests）与 surefire 逐类 parity 零差异、4.2s vs 13.2s;**共同语料 sweep（149 模块交集）302s/14001 tests vs mvn 345s**;全语料（267 派发模块）nop-ib 一次确定性跑完——mvn 在本机四次尝试全部因时序敏感类失败级联 skip 147-220 模块且存在无看门狗挂死（单跑无法完成全语料）。失败集逐类 parity 零未解释差异（时序/顺序敏感类裁定表见 wave6 plan Evidence）。
- 测试执行拓扑: 总 worker 预算 `4×cores`（`nop.ib.workerBudget` 覆盖）+ 模块槽位 `budget/4`,模块公平份额 = min(类数, budget/并发模块数)——全仓每模块 ≈4 worker（对齐 surefire `forkCount=4`）。派发面 = 索引判定的可执行测试类（自有 JUnit 测试注解/@Nested/索引内超类链;嵌套 helper 与 codegen 入口类不派发）。
