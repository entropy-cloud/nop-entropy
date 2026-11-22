# nop-ib — 无状态增量编译与测试服务

面向 AI（与开发者）的日常编译/测试反馈服务。**平台开发期的编译与测试一律走 nop-ib；`mvn` 只用于最终打包**（`install`/`deploy`/发布镜像/CI/全量冷构建——或经 `ib mvn` 白名单透传排队）。

## 目录结构

```
~/nop-ib/
├── nop-ib.jar      # CLI + 服务 jar（shade 单 jar，自包含，无外部依赖目录）
├── bin/
│   ├── ib          # CLI 客户端（任意 cwd、任意 Maven 项目；调试用）
│   ├── ib-run      # 服务 HTTP 薄壳（AI/脚本主入口，CLI 形态 + 队列治理）
│   ├── start.sh    # 启动 HTTP 服务（默认端口 8188，env NOP_IB_PORT 可换）
│   └── stop.sh     # 停止服务
├── nop-ib.log      # 服务日志
├── nop-ib.pid      # pid 文件
└── README.md       # 本文档
```

## 启动 / 停止

```bash
~/nop-ib/bin/start.sh     # 幂等：已在跑则报告健康状态；默认端口 8188
~/nop-ib/bin/stop.sh      # 停止（SIGTERM，2 秒不退升级 SIGKILL）
NOP_IB_PORT=9000 ~/nop-ib/bin/start.sh   # 换端口
```

健康检查：`curl http://127.0.0.1:8188/v1/health` → `{"version","cores","workerBudget","activeJobs","pendingTotal","engines","heapUsedMb","uptimeMs"}`。

服务自描述（AI 零先验接入入口）：`curl http://127.0.0.1:8188/v1/help` —— 返回命令白名单、端点、退出码、配置项与任务声明契约的机器可读摘要。

> 只 loopback 监听（127.0.0.1），无鉴权——本机开发工具边界。

## 服务调用（AI 主入口）

任何程序向服务发送 **项目地址 + 指令**：

```bash
# 同步等待结果（多数场景）
curl -s -X POST http://127.0.0.1:8188/v1/run -d '{
  "projectDir": "/path/to/project",
  "cmd": "check",
  "args": ["--paths", "src/main/java/Foo.java", "--json"],
  "wait": true
}'

# 异步：wait=false 返回 job id，轮询 GET /v1/job/{id}
curl -s -X POST http://127.0.0.1:8188/v1/run -d '{"projectDir":"...","cmd":"test","args":["-pl",":module"],"wait":false}'
curl -s http://127.0.0.1:8188/v1/job/job-42
curl -s "http://127.0.0.1:8188/v1/jobs?limit=20"     # 最近 job 摘要（exit/时长）
curl -s http://127.0.0.1:8188/v1/queue               # 各项目 lane：depth/runningCmd/pending
```

### 异步 Job API（长轮询 / 进度 / 运行中取消 / 任务日志，design 09）

- **幂等 submit**：请求体可选 `clientRequestId`——AI 侧超时重试带同一 key（绑 projectDir）不会重复排队；命中时返回既有 job 并标 `replayed:true`。
- **长轮询**：`GET /v1/job/{id}?waitMs=N&logTail=N`。`waitMs` clamp [0,60000]，未终态等满后返回 HTTP 200 中间态 `{id,state,phase:queued|running,queueAhead,elapsedMs,progress?,logPath,waitedMs}`（**不是错误**，AI 侧循环调用直到终态）；`waitMs=0` 纯快照。`logTail=N` 附带任务日志尾部 N 行（clamp 500）。
- **进度**：job 进行中快照/中间态带 `progress`（test 报 `modulesDone/modulesTotal`，compile 报编译选择集规模）。2026-11-21 起 `phase`/`elapsedMs` 覆盖长 Maven 子进程阶段：`model refresh` 的每个 maven 子步骤（--version / localRepository / effective-pom dump / dependency:list 及分块重解析）、`maven-delegate` 回退（附 cmd 摘要）——长刷新不再是无信息的"假死"。
- **运行中快照自带诊断**：非终态 `/v1/job/{id}` 快照含 `elapsedMs` 与 `logTail`（任务日志尾部 20 行），job id 重启回收时旧日志会被清掉，不会混入上一轮内容。
- **任务日志**：每个 job 落盘 `{repoRoot}/.nop-ib/jobs/{id}.log`（stdout `[out]` / stderr `[err]` 双流带标记），路径在 `logPath`；job prune（终态 10 分钟）时同步删除文件。
- **运行中取消**：`DELETE /v1/job/{id}` 对 QUEUED 与 RUNNING 均有效——RUNNING 走协作式取消：树终止该 job 登记的全部子进程（含 surefire 孙进程）+ 中断 job 线程，置 `cancelled`（exit=-1），日志尾写取消记录；终态 job 返回 409 `already-terminal`。
- 长轮询走独立 4 线程小池：占满时 `/v1/job` 立即返回纯快照（`waitedMs=0`），读端点（health/jobs/queue）永不被饿死。
- **历史与合并**：终态任务最近 100 条内存保留（重启清空），`GET /v1/jobs` 查询；与排队中（未启动）job 完全同参的提交自动合并（`coalesced:true`），详见下方「多 agent 排队协调」与 `/v1/help` 的 `queueing`/`coalescing` 节。

- `cmd` 白名单：`status | plan | compile | test | check | model | clean | mvn`
- `projectDir` 可以是仓库根、**子模块目录**或 **symlink 别名**——服务统一归一到 reactor 根（同项目只有一条构建队列，杜绝并发踩踏）
- payload 与 CLI `--json` 同构（NDJSON 序列）；`exit` 字段即构建结果：`0` 成功 / `1` 失败 / `3` 用法或配置错误 / `6` partial / `75` 回退 Maven 进程超时
- `fallback.reason` 非 null 表示该轮走了 Maven 回退及其原因；JSON `cached:true` 表示测试缓存命中（未执行）、`compiled` 表示 test 前的预编译状态
- **同项目串行 + 自动排队**：同一项目一次只跑一个构建，其余 FIFO 排队（上限 32，满则 429）
- **跨项目自适应并行**：全局预算 = CPU 核数一半（本机 16 核 → 8）

## 新项目接入（一次性）

```bash
~/nop-ib/bin/ib model refresh    # 在项目根目录执行：导出 Maven 构建事实（大项目 ~50s）
~/nop-ib/bin/ib model index      # 扫描产物建单元索引（~4s）
```

之后模型随 pom 变化自动检测过期；过期时服务自动回退真 Maven 并给出原因，重新 `model refresh` 即恢复快路径。

### 项目根识别契约（重要）

- 服务把 `projectDir` 归一到 **Maven reactor 根**：优先识别 `mvnw` + `.mvn/` 标记；否则沿连续 pom 链向上取顶。同 reactor 只有一条构建队列。
- **嵌套项目告警**：一个带自己 `pom.xml` 的独立项目若嵌套在一个含 `mvnw` 的父仓目录内，会被归一到父仓——提交相对路径时构建的是父仓而非嵌套项目。嵌套/无仓独立项目一律用 `ib-run --dir <绝对路径>` 或绝对 `projectDir` 显式指定。
- **单模块项目一等支持**：非聚合器项目（repo 根即唯一模块）的 refresh/compile/test 与多模块完全同权。

## ib-run 薄壳（推荐的命令行形态）

```bash
~/nop-ib/bin/ib-run check --paths src/main/java/Foo.java --json   # projectDir 从 cwd 的 git 根推断
~/nop-ib/bin/ib-run test -pl :module-name
~/nop-ib/bin/ib-run model refresh
~/nop-ib/bin/ib-run --dir /path/to/project status                  # 显式项目（嵌套/多仓场景必用）
~/nop-ib/bin/ib-run test -pl :module --quiet                       # 关闭 stderr 进度通道
```

- 自动从所在 git 仓推断 `projectDir`（`git rev-parse --show-toplevel`），异步提交后轮询服务，**排队时在 stderr 显式提示**：`queued: N job(s) ahead (running: job-K) — waiting, Ns so far | log: <路径> (poll GET …)`（每 10s 刷新），运行期输出 `running: <phase> (Ns) | log: <路径>`；结束在 stderr 打印 `exit=… state=… fallback=…` 与日志路径，并透传构建退出码（0/1/3/6/75）。
- **stdout 契约**：参数含 `--json` 时，stdout 输出终态 job 快照 JSON（含 payload），stderr 只承载进度/汇总——stdout 恒为机器可解析形态；无 `--json` 时 stdout 为空。
- 服务不可达时报错并提示 `bin/start.sh`（exit 75）。`IB_PORT` 可换端口。

### 多 agent 排队协调（要点，详见 /v1/help 的 queueing/coalescing 节与 design 10）

- 同一项目一次只跑一个构建（FIFO lane，排队上限 32、满则 429）；不同项目按全局预算并行。
- 排队中的 job 快照含 `queueAhead`/`queueAheadIds`/`runningJobId`/`queueWaitMs`；**绝不携带** `progress`/`logTail`（那是正在运行的 job 的 lane 级事实，泄漏会被误读为自己的执行进度）。
- **历史保留**：终态任务（done/failed/cancelled）在**内存中**始终可查——最近 100 条跨项目共享（不受时间淘汰、重启即清空），`GET /v1/jobs` 返回。提交前先查历史可评估 lane 拥挤度与典型耗时。
- **超限拒绝**：lane 排队上限 32，满则对新提交 429；**排队中的记录绝不自动丢弃**（等待方不能看到自己的 job 消失）。自治 agent 收到 429 应带 clientRequestId 指数退避重试。
- **同参合并**：与**排队中**（未开始执行）job 完全相同（projectDir+cmd+args）的提交会合并到它（响应 `coalesced:true`），不重复排队；**运行中**的 job 不吸收提交——相同命令重新排队，源码没变时重跑会命中测试缓存，变了则正确重建。
- **缓存阶梯**：模型新鲜 + 指纹未变 → 测试缓存命中（秒级返回）；有变更 → 快路径增量；模型 stale → 每个排队 job 各付一次全量 Maven 回退（最贵）。**pom 变更后先 `model refresh` 一次再发起批量构建**，把整个 lane 拉回快路径。

## CLI 直用（不经服务；仅限本地一次性调试，AI 调用方一律走 ib-run/HTTP——CLI 绕过服务队列与并发治理）

```bash
~/nop-ib/bin/ib check --paths src/main/java/Foo.java   # 项目根目录下执行（编译 + 受影响测试）
~/nop-ib/bin/ib test -pl :module-name                  # 单模块测试（默认先自动增量编译；--no-compile 关闭）
~/nop-ib/bin/ib compile --paths <file...>              # 仅编译
~/nop-ib/bin/ib clean -pl :module-name                 # mvn-clean 同义面 + 索引/缓存失效
~/nop-ib/bin/ib mvn -pl :module-name install           # 白名单透传：install/package/clean/dependency:*/surefire:*
```

`test` 默认派发前先做增量编译（编译失败不派发测试，零假绿）；`--no-compile` 恢复 v1 不编译行为并在 JSON 显式 `compiled:false`。

**依赖解析前置条件**：快路径的外部依赖事实来自本机 Maven 仓库（refresh 时解析）。一个从未 install 过 reactor 内 sibling 的多模块仓，`test -pl :module` 会按 `depsUnresolved` fail-safe 回退 Maven（首次全量 `ib mvn install -DskipTests` 一次后恢复快路径）。

## 代码生成任务声明（nop-ib.tasks.json）

模块目录（或仓库根）可放 `nop-ib.tasks.json` 声明代码生成任务，ib 在快路径编译前/后增量执行——模板变更不再整体回退 Maven：

```json
[
  {
    "name": "nop-precompile",
    "trigger": "before-compile",
    "inputs": ["precompile/*.xgen"],
    "outputs": ["src/main/resources/_vfs/**"],
    "exec": { "type": "java", "mainClass": "io.nop.codegen.task.CodeGenTask",
              "arguments": [".", "precompile"], "classpathScope": "test" }
  }
]
```

- 分层可选：根级文件对全 reactor 生效，模块级同名任务覆盖（非 exec 字段整体替换、exec 逐字段合并）
- `classpathScope`：`compile|runtime|test`（默认 test），语义对齐 exec-maven-plugin，由 ib 自建 classpath
- 执行体：`java`（项目 classpath 上的 mainClass）或 `xscript`（runner mainClass + 脚本）
- 增量：inputs/outputs 指纹未变 → 跳过（`tasks:[{name,kind:skipped|executed|failed}]` 显式区分）；指纹落 `target/nop-ib/tasks/`（`mvn clean` 自动清）
- 失败显式回退 Maven；配置错误 exit 3
- 平台参考声明：`nop-wf/nop-wf-meta/nop-ib.tasks.json`

## 缓存与状态（全在项目内，服务无状态）

全部落在各项目自己的 `.nop-ib/` 目录：`model.json`（构建事实）、`units/`（类图/ABI 索引）、`test-cache.json`（opt-in `--cache` 的模块级绿结果缓存）。服务进程崩溃/重启零影响——重建连接重发请求即可（幂等）。engine 缓存空闲 30 分钟（`nop.ib.engineIdleTtlMillis`）自动回收，重建冷装载无损。

## 配置

| 属性 | 作用 | 默认 |
|---|---|---|
| `NOP_IB_PORT`（env） | 服务端口 | 8188 |
| `nop.ib.maven`（-D） | 回退/透传用 Maven 可执行文件路径 | mvnw > PATH 全局 mvn |
| `nop.ib.serviceWorkerBudget` | 全局 job 并发上限 | max(2, cores/2) |
| `nop.ib.workerBudget` | 单 job 测试 worker 上限 | 4×cores |
| `nop.ib.engineIdleTtlMillis` | engine 空闲回收阈值 | 1800000（30 分钟） |
| `nop.ib.taskTimeoutMillis` | codegen 任务看门狗 | 300000（5 分钟） |

## 何时仍然用 mvn

**只在这些场景**：`deploy` / 发布镜像 / CI 流水线（`install`/`package`/`clean`/`dependency:*` 可经 `ib mvn` 白名单走服务排队）；代码生成链中无法声明为任务的部分（ib 显式回退并给原因）。日常开发（改代码→编译→跑测试）**不碰 mvn**。

## 重建分发

```bash
cd <nop-entropy 源码>/nop-tools/nop-ib && ./bin/package-ib.sh   # 重新打包到 ~/nop-ib
```

改了 nop-ib 本身之后必须重打包并重启服务（服务进程加载的是打包时的 jar）。
