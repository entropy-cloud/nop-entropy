# Wave 10 — 无状态常驻服务（HTTP 接入 / 同项目排队 / 全局并行预算）

> Plan Status: draft
> Last Reviewed: 2026-11-17
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 10（owner 指令 2026-11-17）
> Related: [07-service-mode-design.md](../../design/nop-incremental-build/07-service-mode-design.md)（IB-S1..S6）、[wave9-test-cache.md](wave9-test-cache.md)（cached 透传面）
> Review: 2026-11-17 对抗性审查 R1——B1 root 接缝、B2 预算语义已吸收进 Phase 1（items 1a/1f）；M3/M4/N5/N6 已吸收；N7/N8 已裁定

## Purpose

owner 需求：`nop-ib` 可启动为无状态服务；任何程序发送项目地址 + 指令即由服务负责编译/测试；同一项目地址下编译一次只执行一个、自动排队；单次执行按机器情况自适应并行（默认 CPU 个数的一半）。

## Current Baseline（live repo 核实）

- `ServeCommand`（stdio JSONL）已存在，但 engine 绑定启动 cwd，单项目、同步、无排队（`cli/ServeCommand.java`）。
- `IbEngine` 按 `discoverRepoRoot(startDir)` 定位仓库根，模型/索引状态全部落盘 `.nop-ib/`（`engine/IbEngine.java`）——多项目 engine 化的地基已具备。
- **root 接缝缺口（审查 B1）**：`NopIbMain.run(String[], out, err)` 在 `NopIbMain.java:122` 把 startDir 硬编码为 cwd（`Paths.get("")`）——服务进程的 cwd ≠ 请求 projectDir 时，现有入口无法指向指定 root。
- 并行现状：测试 worker 预算 `TestCommand.WORKER_BUDGET = max(4, Integer.getInteger("nop.ib.workerBudget", 4*cores))`（`cli/TestCommand.java:61`；显式设 property 时不与 4*cores 取大）；worker 数以参数 `workerCount` 传入 `runModuleTests`。**没有跨项目全局预算**。
- 无 HTTP 层、无 job registry、无 per-project 队列（grep 核实）。
- `.mvn/build.lock` 文件锁是跨进程仲裁（IB-D9，`lock/BuildLock.java`），服务执行与 CLI 同一代码路径自然继承。

## Goals

- `nop-ib serve --http [--port N]`：loopback HTTP 服务，`POST /v1/run {"projectDir","cmd","args","wait"}` 即可让服务代跑 `compile|test|check|plan|status`。
- 同项目串行 + FIFO 排队（默认上限 32，满则 429 显式拒绝）；排队可取消、运行中不可取消（`cannot-cancel-running`）。
- 全局 job 并发闸 = `max(2, cores/2)`（`nop.ib.serviceWorkerBudget` 覆盖）；**且**服务执行时把单 job 的 worker 预算压到 `max(2, permits / activeJobs)`，保证机器级 worker 总数真正封顶（多项目并发时不超载，单项目时仍可用满原预算）。
- 结果与 CLI `--json` 同构；fallback/partial/cached 标记原样透传。
- 无状态：崩溃重启后同请求重放幂等。

## Non-Goals

- 非 loopback 远程服务、鉴权、多机分布式构建。
- 结果全局内容寻址缓存（`--cache` 仍是每项目 opt-in）。
- 请求合并/去重（每个请求独立入队、独立显式结果）。
- 改变 CLI（非服务）路径的单项目内部调度语义。

## Scope

### In Scope

- `nop-tools/nop-ib`：`service/`（ServiceCore、ProjectQueue、JobRegistry、JobBudget）、`NopIbMain` root 感知重载与 `--http` 参数、`cli/ServeCommand` HTTP 分支、`TestCommand` worker 预算 override 接缝。
- 单元/端到端测试钉；design 07 原地修订（与实现逐条核对后）；roadmap/日志收口。

### Out Of Scope

- Maven fallback 路径的服务化缓存；stdio serve 协议变更；发布型模块接入。

## Execution Plan

### Phase 1 — root 接缝与服务内核

Status: completed
Targets: `NopIbMain.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/service/`、`cli/TestCommand.java`

- Item Types: `Feature`

- [x] 1a. **[Feature]**（审查 B1）`NopIbMain` root 感知重载：`run(String[] args, Path repoRoot, out, err)`，原签名委托 `Paths.get("")` 保持 CLI 行为零漂移；服务路径全部走新重载
- [x] 1b. **[Feature]** `ProjectQueue`：per canonical repo root 的 FIFO 队列 + 单执行闸门（同项目串行）；深度上限 32，满则显式拒绝（HTTP 429，不静默丢弃）
- [x] 1c. **[Feature]** `JobRegistry`：job 状态机 queued→running→done/failed；排队中可取消；running 取消返回 `cannot-cancel-running`（显式响应体字段）；registry 仅内存（无状态原则）；payload 大小上限 8MB，超出截断并附 `truncated:true` 标记
- [x] 1d. **[Feature]** `JobBudget`：全局信号量 permits = `max(2, cores/2)`（`nop.ib.serviceWorkerBudget` 覆盖）；job 执行前 acquire、结束释放；服务内把单 job worker 预算压为 `max(2, permits / activeJobs)`（经 `TestCommand` 的 override 接缝，见 1e）
- [x] 1e. **[Feature]**（审查 B2）`TestCommand` worker 预算 override 接缝：静态 volatile override（默认空 = CLI 行为不变），`runModuleTests` 的 workerCount 调用点经 override 生效；CLI 路径零漂移钉
- [x] 1f. **[Feature]** `ServiceCore`：engine cache（canonical root → IbEngine，LRU 上限 8）；**驱逐只作用于非 running root**（running job 持强引用，审查 M3）；执行入口复用 1a 的 root 感知 `NopIbMain.run`（同一 JSON 契约，无平行实现）
- [x] 1g. 单元钉：同项目串行互斥、排队顺序、队列满拒绝、预算封顶（活跃 worker 总数 ≤ permits，含 override 生效断言）、取消语义（queued 可取消 / running 返回 cannot-cancel-running）、engine 驱逐后重建、running root 不被驱逐

Exit Criteria:

- [x] 1g 钉全绿；并发/串行/预算断言可在测试中观测
- [x] **无静默跳过**：队列满/取消 running/未知 cmd/projectDir 非法均显式报错，无 no-op 分支
- [x] **接线验证**：ServiceCore 确实通过 root 感知 `NopIbMain.run` 执行（projectDir ≠ 服务 cwd 的钉在档），不是平行实现
- [x] CLI 零漂移：不带服务属性时 `run(String[], out, err)` 行为与现版本一致（既有钉全绿）
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [x] No owner-doc update required（Phase 2 完成后统一核对 design 07）

### Phase 2 — HTTP 接入

Status: planned
Targets: `cli/ServeCommand.java`、`NopIbMain.java`

- Item Types: `Feature`

- [ ] 2a. `serve --http [--port N]`：JDK HttpServer loopback only（绑定 127.0.0.1）；路由 `POST /v1/run`、`GET /v1/job/{id}`、`GET /v1/queue`、`GET /v1/health`、`DELETE /v1/job/{id}`
- [ ] 2b. `wait` 语义（审查 M4）：`wait=true`（默认）内部仍提交 job + 在 handler 内等待完成（非平行执行路径）；HTTP executor 固定 16 线程（≥ 排队面），长任务不饿死 /v1/queue、/v1/health 等读端点；`wait=false` 立即返回 job id
- [ ] 2c. payload 与 CLI `--json` 同构；429（队列满）与 DELETE-running 的响应体字段显式钉（审查 N5）
- [ ] 2d. stdio JSONL 模式保留，行为零漂移
- [ ] 2e. 单元钉：路由/参数错误显式 4xx；`projectDir` 非法（非 Maven 项目）显式 400；health 暴露 version/cores/workerBudget/activeProjects

Exit Criteria:

- [ ] HTTP 端到端钉：本地起服务 → `POST /v1/run`（status 命令）→ payload JSON 与 CLI `--json` 字段一致
- [ ] **端到端验证**（Minimum Rules #22）：从 HTTP 入口点到引擎结果 JSON 完整走通（真实临时项目 fixture）
- [ ] **接线验证**：wait=true 请求与异步 job 走同一 ServiceCore 提交路径（审查 M4 的"非平行执行路径"断言在档）
- [ ] stdio serve 既有钉保持全绿（零漂移）
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] design 07 与实现逐条核对，契约偏差原地修订（含 IB-S4 措辞同步为"job 并发闸 + 单 job worker 预算压制"双层语义）

### Phase 3 — 端到端 Proof + 文档收口 + closure

Status: planned
Targets: 临时 fixture 项目、design 07、roadmap

- Item Types: `Proof | Follow-up`

- [ ] 3a. 端到端实测：同项目并发 3 请求 → 串行排队、全部显式结果、无 target 踩踏；双项目并发 → 运行 worker 总数 ≤ permits（含 1d 压制生效）；`--cache` 命中经服务返回 `cached-up-to-date`；kill -9 后重启重放幂等——**幂等判据（审查 N8）：exit 与 JSON 结构字段一致；cached/时长等时效字段允许漂移**
- [ ] 3b. 实测表入 analysis；roadmap Wave 10 勾选 + Status；daily log
- [ ] 3c. 独立子 agent closure audit APPROVED，Evidence 落本 plan；提交

Exit Criteria:

- [ ] 实测表在档（串行/预算/cache/幂等四行）
- [ ] check-plan-checklist --strict 退出 0；scan-hollow-implementations 退出 0
- [ ] closure audit APPROVED

## Closure Gates

- [ ] 同项目串行 + 排队语义按 design 07 §2.2 成立（钉 + 实测双证据）
- [ ] 全局预算封顶成立：job 并发 ≤ permits **且** 活跃 worker 总数 ≤ permits（1d 压制证据）
- [ ] root 接缝成立：projectDir ≠ 服务 cwd 的请求正确路由到对应 repo root
- [ ] HTTP 契约与 CLI JSON 同构（端到端比对）
- [ ] 零假绿透传：fallback/partial/cached 标记经服务原样到达调用方
- [ ] 无状态：崩溃重启幂等验证在档（判据 = 3a）
- [ ] 钉全绿 + `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] design 07 与实现一致（无契约漂移）
- [ ] 独立 closure audit APPROVED（evidence 落 plan）
- [ ] 提交

## Deferred But Adjudicated

### 请求合并 / 结果去重

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 每请求独立显式结果是契约（design 07 §四）；合并会让第二调用方拿到不属于自己的执行语义
- Successor Required: no

### 非 loopback / 鉴权

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本机开发工具边界即 loopback；远程暴露属部署形态问题
- Successor Required: no

### job payload 持久化

- Classification: `watch-only residual`
- Why Not Blocking Closure: registry 仅内存 + 8MB 截断标记（1c）；崩溃后 job id 丢失 = 客户端重发请求，磁盘状态保证幂等；堆压力受 32 深度队列约束
- Successor Required: no

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时填写）
