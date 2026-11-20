# nop-ib 增量编译与测试（AI 日常入口）

> 状态: 规范性文档（Wave 0–11 已落地；Wave 10 服务化 / Wave 11 治理）。
> 设计权威与执行状态见 AI 开发记录区的设计文档 05/06/07 与单文件 roadmap（按边界规则此页不直接链接 ai-dev 路径）。

## 何时用

**平台开发期的日常编译与测试一律走 nop-ib；`mvn` 只用于最终打包**（`install`/`deploy`/发布镜像/CI/全量冷构建）。

两种使用形态：

1. **服务形态（默认，多 AI/多项目共享）**：`~/nop-ib/bin/start.sh` 启动后，任何程序向 `http://127.0.0.1:8188/v1/run` 发送 `{projectDir, cmd, args, wait}` 即可；同项目自动串行排队、跨项目按机器预算并行。安装/使用说明见 `~/nop-ib/README.md`（由 `nop-tools/nop-ib/bin/package-ib.sh` 打包生成）。
2. **CLI 形态（单次调用）**：`nop-tools/nop-ib/bin/ib <cmd>`（worktree 内）或 `~/nop-ib/bin/ib <cmd>`（任意项目）。

**回滚开关**: 任何时候直接用 `./mvnw` 即可（mvnq 已于 Wave 11 移除出依赖链，裸 mvn 可直接使用），`nop-ib` 不占用任何排他资源（无文件锁，并发由进程内 lane 控制）。

## 前置（每个 worktree 一次性）

```bash
./nop-tools/nop-ib/bin/ib model refresh   # 导出 Maven 构建事实（~50s）
./nop-tools/nop-ib/bin/ib model index     # 扫描产物建单元索引（~4s）
```

模型过期（任一 pom 变化）会让所有快路径结论失效——此时先 `model refresh`，或直接回退 Maven。Maven 委托默认 10 分钟超时（`nop.ib.maven.timeout` 分钟数可覆盖），超时显式报 `maven delegation timed out`。

## 命令面

| 命令 | 用途 |
|---|---|
| `ib status [--json]` | 模型/索引健康、当前变更集 |
| `ib plan --paths <files...> [--json]` | 只算不做: 编译集/测试集/回退原因/下游候选 |
| `ib compile --paths <file...>` / `ib compile -pl <module>` | 选择性编译 |
| `ib test --affected` / `ib test -pl <module>` / `ib test --class [mod:]Class` / `--rerun-failed` | **默认先增量编译**（与 `check` 同通道，编译范围镜像测试作用域），再自研 JUnit Platform fork 启动器执行（每类隔离 fork+4 并行;launcher 版本自模块 classpath 推导;快照录制显式禁用;批超时看门狗）。`--no-compile` 显式关闭预编译（JSON `compiled:false`，不静默）; 编译失败以编译退出码结束、不派发测试（零假绿）; JSON 契约 `compiled: true\|false\|"skipped"`。`--runner surefire` 可退出到 Maven Surefire 委托（setup 不满足时也自动显式回退） |
| **绿结果缓存（test/check 默认开启）** | 输入指纹未变 + 上次运行绿 → 跳过重执行，输出显式 `cached:true` + advisory（不冒充执行结果）。指纹覆盖：模块单元索引 + 传递依赖 ABI + externalArtifacts + surefire 全部 facts。指纹不命中即真跑（fail-safe）。`--no-cache` 恢复 run-always；`--force` 单次绕过命中；`--class`/`--rerun-failed`/Maven 回退路径永不缓存 |
| **compilerArgs 镜像** | pom `compilerArgs` 白名单参数（`-parameters`/`-g`/`--add-modules <值>`/`--enable-preview`）由快路径逐字回放；白名单外参数 → 该模块自动 Maven 回退且 reason 点名（不静默省略） |
| `ib check --paths <file...>` | **AI 日常主入口**: 编译 + 受影响测试（`--no-compile` 对 `check` 无效，编译恒开） |
| `ib explain <file\|module>` | 解释归属/能力分级/依赖者 |
| `ib model refresh\|show\|index` | 模型与索引维护 |
| `ib bench <module> [--json]` | 四类成本基线实测 |
| `ib serve` | JSONL 常驻协议；`serve --http [--port N]` = 多项目 HTTP 服务（见 `~/nop-ib/README.md`） |
| `ib clean [-pl <module>] [--json]` | mvn-clean 同义面：删选中模块 target 输出 + 单元索引 + 测试缓存条目（模型过期显式拒绝） |
| `ib mvn <args...> [--json]` / 服务 `{"cmd":"mvn","args":[...]}` | 白名单真 Maven 透传（Wave 11）：goal 仅 `install \| package \| clean \| dependency:* \| surefire:*`，经同一 per-project lane 委托真 mvn（`nop.ib.maven` > mvnw > PATH）；白名单外 goal（deploy/release 等）拒绝 exit 3；mvn 退出码原样透传，`--json` 输出 `{cmd,args,exit,outputTail}` |

退出码: `0` 成功; `1` 编译/测试失败; `2` 回退被 `--on-fallback=fail` 拒绝; `3` 用法错误; `6` partial; `75` fallback Maven 进程超时。

## 代码生成任务声明（nop-ib.tasks.json）

模块可在自己目录（或仓库根）放一个 `nop-ib.tasks.json`，声明**代码生成任务**——ib 在快路径编译前/后增量执行它，模板变更不再整体回退 Maven（设计权威见 AI 开发记录区的 nop-incremental-build 设计目录 08 号文档；按边界规则此页不直接链接 ai-dev 路径）：

```json
[
  {
    "name": "nop-precompile",
    "trigger": "before-compile",
    "inputs": ["precompile/*.xgen"],
    "outputs": ["src/main/java/**/_gen/**"],
    "exec": { "type": "java", "mainClass": "io.nop.codegen.task.CodeGenTask",
              "arguments": [".", "precompile"], "classpathScope": "test" }
  }
]
```

- **分层可选**：根级文件对全 reactor 生效；模块级同名任务覆盖根级（非 exec 字段整体替换、exec 逐字段合并）。
- **classpathScope**：`compile|runtime|test`（默认 `test`），语义对齐 exec-maven-plugin——由 ib 用模块依赖闭包自建 classpath。
- **执行体**：`java`（项目 classpath 上的 mainClass）或 `xscript`（runner mainClass + `.xscript` 脚本，ib 不内嵌 XLang）。
- **增量**：inputs 内容指纹 + outputs 存在态未变 → 跳过（`compile/check` JSON 的 `tasks` 数组显式区分 `skipped/executed/failed`）；指纹与任务中间产物落各模块 target 目录的 nop-ib/tasks/ 下（`mvn clean` 自动清）。
- **失败显式回退**：任务失败/无产物/classpath 不完整 → 该轮构建自动委托 Maven 并给出来自任务的 reason；配置错误是 exit 3 用法错误。
- 参考声明：`nop-wf/nop-wf-meta/nop-ib.tasks.json`。

## 运行记录（mvn 输出等价物）

每次 `ib compile/test/check` 自动生成两个持久记录（进程退出后仍可读，AI 会话与事后审计的证据面）:

- **`_tmp/nop-ib/<cmd>-<时间戳>.log`** — 人类可读运行日志（仓库统一临时目录 `_tmp/`）: 每模块结果行 + 缩进的失败明细（类/方法/异常类型/消息，等价 surefire 报告内容）+ javac 编译诊断块 + Maven 回退输出。
- **`.nop-ib/last-run.json`** — 机器可读状态（与 `last-failed.json` 同类的 `.nop-ib/` 状态文件，非临时产物）: cmd/exitCode/modules[]（ok/kind/run/failed/failedDetail/durationMs/warnings/diagnostics）。

编译报错与测试报错的完整信息（含 javac file:line:col 诊断、断言消息、worker stderr 尾部、污染事件）都在这两个文件里; console 只保留汇总行。历史日志按时间戳累积在 `_tmp/nop-ib/`（临时目录，可随 `_tmp` 一起清理）。

## 正确性语义（调用方须知）

- **零假绿**: 回退、`partial`、`wideImpact` 全部显式出现在人类输出与 JSON；未执行的测试不会计为通过。
- **回退是显式状态**: pom 变更 / XLang 模型或模板变更 / L2 注解处理器模块 / L3 AOP 模块 / 模型过期 → 自动委托 Maven（`nop.ib.maven` > mvnw > PATH mvn；不再经 mvnq），并给出原因。
- **产物一致性**: 快路径编译与 `mvn compile` byte 级对拍零差异（-g/--release/-proc 通道对齐）；对拍 harness 在 `nop-tools/nop-ib/tools/ab_compare.py`。
- **能力分级**（产物证据法）: L0 快路径 343 模块 / L1 xgen 模板 92（模板变更回退,手写源码快路径）/ L2 注解处理器 12（恒回退）/ L3 AOP 产物 10（恒回退）。
- **模块串行 + 拓扑序**执行；产物目录与 Maven 共用，禁止 intra-project jar 进 fast-path classpath。
- **并发模型（Wave 11）**: 无文件锁——服务 per-project lane（同项目 FIFO 串行，上限 32）+ 跨项目全局预算 `max(2, cores/2)` + 单 job worker 压制；项目身份以 reactor 根 real-path 归一（子模块目录/symlink 别名提交收敛到同一 lane）。
- **仓库分层**: 本项目模块间依赖=reactor 输出目录（永远最新，永不 install）；第三方依赖=公共仓（`~/.m2/repository`，可用 `NOP_IB_COMMON_REPO` 覆盖）**只读解析**；worktree 私有仓（`.m2-repo`）仅作冷路径回退时的 fallback，且 nop-ib 从不写任何仓库。本项目编译产物只存在于各 worktree 的 `target/`，天然无跨 worktree 竞争。

## 实测基线（本机,勿外推）

- `noop`（status+plan）0.8–1.2s;单文件方法体编译 0.7–1.5s（Maven 骨架 3.4s）;ABI 变更扩散 5 下游 11 文件 1.37s;`check` 端到端 4.5s。
- 刷新模型 52s（457 reactor project）;全量索引 4s（383 模块）。
- **`test` 对 `mvn test`（2026-11-14）**: 4 模块切片中位数 **9.2s vs `mvn test -T 1C` 11.9s**;单模块对照（orm-geo/spring-core-starter/stream-connector-jdbc 14/15/71 tests）与 surefire 逐类 parity 零差异、4.2s vs 13.2s;**共同语料 sweep（149 模块交集）302s/14001 tests vs mvn 345s**;全语料（267 派发模块）nop-ib 一次确定性跑完——mvn 在本机四次尝试全部因时序敏感类失败级联 skip 147-220 模块且存在无看门狗挂死（单跑无法完成全语料）。失败集逐类 parity 零未解释差异（时序/顺序敏感类裁定表见 wave6 plan Evidence）。
- 测试执行拓扑: 总 worker 预算 `4×cores`（`nop.ib.workerBudget` 覆盖）+ 模块槽位 `budget/4`,模块公平份额 = min(类数, budget/并发模块数)——全仓每模块 ≈4 worker（对齐 surefire `forkCount=4`）。派发面 = 索引判定的可执行测试类（自有 JUnit 测试注解/@Nested/索引内超类链;嵌套 helper 与 codegen 入口类不派发）。
