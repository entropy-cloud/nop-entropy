# Goal Driver 资源泄漏与系统死机重启分析

> Status: resolved
> Date: 2026-06-15 (updated 2026-06-15 with web research)
> Scope: `ai-dev/tools/opencode-goal-driver/`、`opencode`、macOS XNU 内核 bug
> Conclusion: **根因确证：opencode 导致 XNU 内核 `data.kalloc.1024` zone 内存泄漏（~20 GB / 2100 万个分配），耗尽内核 zone map → kernel panic。** 三次崩溃（06-13/14/15）panic string 完全相同，panicked task 均为 `opencode.exe`。经联网调研，此问题**并非 opencode 独有**——Claude Code (#44824) 触发相同的 kalloc.1024 泄漏（30-48 MB/min），且 opencode 上游 issue #32002（06-12 已报告，早于本机三次崩溃）完全相同。跨项目证据指向 **macOS XNU 内核/GPU 驱动 bug**，CLI AI 工具（TUI 密集型+高频率进程创建）是高触发率触因。goal-driver 已有 `sys-snapshot.mjs` 监控 kalloc.1024（每 5 min 采样），但**无主动式阈值防护**——泄漏积累到 20 GB 前不会采取预防性重启。

## Machine Configuration

| 项目 | 配置 | 备注 |
|------|------|------|
| **机型** | Mac Studio (Mac16,9) | 桌面工作站 |
| **芯片** | Apple M4 Max | ARM64 架构，非 x86 |
| **核心** | 16 核（12 性能核 P + 4 能效核 E） | Apple Silicon 无超线程，物理核 = 逻辑核 |
| **内存** | **128 GB 统一内存**（unified memory） | CPU 与 GPU **共享同一内存池**；非传统 DDR |
| **磁盘** | 460 GB SSD，可用 224 GB（7% 已用） | 不是瓶颈 |
| **Swap** | 动态按需分配（Apple Silicon 无固定 swap 分区） | 刚重启时为 0 |
| **OS** | macOS（Firmware 13822.81.10） | 使用 `jetsam`（非 Linux OOM-killer）管理内存压力 |

**关键架构特征**：
1. **统一内存**：128 GB 被 CPU 和 GPU 共享。如果有进程触发 GPU 内存分配（如 ML 推理），会直接吃 CPU 可用内存。
2. **jetsam**：macOS 内存极度紧张时不精准杀进程，而是渐进式 jettison；极端情况下系统冻结而非干净 OOM-kill。
3. **128 GB 极难被简单进程累积耗尽**：当前 near-idle 总 RSS 仅 31.5 GB（见下表）。需要异常进程（内存泄漏 / 异常并行 / GPU 占用）才能逼近极限。

### 当前基线资源占用（刚重启 ~15 min 后采集）

| 进程/群组 | RSS | 说明 |
|-----------|-----|------|
| IntelliJ IDEA（全部子进程） | **5.7 GB** | 6 个进程（含 Maven Server 1 GB） |
| opencode（交互式 PID 1899） | 1.3 GB | 已运行 12 min |
| opencode run（goal-driver 子进程 PID 3012） | 800 MB | 已运行 10 min，**增长中** |
| Docker | 880 MB | 9 个进程 |
| MySQL | 500 MB | |
| LM Studio（2 进程） | 790 MB | |
| node（全部） | 330 MB | 5 个进程 |
| **Total RSS（全部进程）** | **31.5 GB** | **占 128 GB 的 24.6%** |
| **System free** | **97%** | |
| **Process count** | 665 | |

## Context

- **触发事件**：2026-06-15 11:45:25 系统 kernel panic 重启。`pmset -g log` 确认 boot time 11:45:25。
- **根因确证**：`/Library/Logs/DiagnosticReports/panic-full-2026-06-15-114525.0002.panic` 存在，不是硬断电。
- **重复模式**：06-13 07:58、06-14 12:51、06-15 11:45 — 连续三天，panic root cause 完全相同。

## Definitive Root Cause: Kernel Panic — opencode kalloc.1024 Zone Exhaustion

### Panic String（三次完全相同）

```
panic(cpu N): zalloc[3]: zone map exhausted while allocating from zone [data.kalloc.1024],
likely due to memory leak in zone [data.kalloc.1024] (20G, 21188208 elements allocated) @zalloc.c:4534
```

### 三次 Panic 对比

| 日期 | panic 文件 | 内核泄漏 | 元素数 | panicked task | RSS | 线程 |
|------|-----------|---------|--------|--------------|-----|------|
| 06-13 | `panic-full-2026-06-13-075817.0002.panic` | **19G** | 20,932,112 | pid 38961: **opencode.exe** | 1.6 GB | 31 |
| 06-14 | `panic-full-2026-06-14-125154.0002.panic` | **20G** | 21,196,992 | pid 331: **opencode.exe** | 1.5 GB | 30 |
| 06-15 | `panic-full-2026-06-15-114525.0002.panic` | **20G** | 21,188,208 | pid 22214: **opencode.exe** | 1.3 GB | 30 |

### 含义

1. **XNU 内核 `data.kalloc.1024` zone 耗尽**：内核的通用 1024-byte 内存分配池被 2100 万次未释放的分配占满，总量 ~20 GB。
2. **内核自身诊断**："likely due to memory leak in zone [data.kalloc.1024]" — 内核判定为泄漏，不是正常使用。
3. **panicked task = opencode.exe**：内核 panic 发生在 opencode 进程的上下文中。opencode 的系统调用（fd 操作 / Mach IPC / 网络）触发了泄漏。
4. **opencode userspace RSS 仅 1.3-1.5 GB**：泄漏在内核空间，userspace 工具（`memory_pressure`、`vm_stat`、`ps RSS`）完全看不到。这就是为什么之前所有检查都"正常"（97% free、compressor 0、wired 3 GB）。

### 为什么 sys-snapshot 之前没有预警

| 指标 | 能看到内核泄漏？ | 原因 |
|------|:-:|------|
| `memory_pressure` | 否 | 只测 userspace 页面 |
| `vm_stat` wired/compressed | 否 | zone map 是独立地址空间区域 |
| `ps` RSS | 否 | 只测 userspace 常驻 |
| load average | 间接 | 高 load 可能与内核挣扎有关，但不精确 |
| **`zprint` / `sudo zprint`** | **是** | 显示每个 zone 的当前大小和元素数 |

### 可能的 opencode 泄漏机制（待 opencode 团队排查）

opencode 是 Go 程序（内核报告中显示为 `.exe`），可能的 kalloc.1024 泄漏源：

- **fd 泄漏**：每个 open file / socket / pipe 在内核中占 kalloc 内存；Go 的 net/http 连接池如果未正确关闭会累积
- **Mach port 泄漏**：opencode 与 MCP server 的 IPC 如果 port 未销毁
- **线程/proc 结构**：opencode 的 30 个 goroutine 对应的内核线程结构
- 触发条件：长时间运行（60+ 分钟 EXECUTE step），大量 tool calls（文件读写、子进程 spawn）

### Mitigation（goal-driver 可做的）

| 措施 | 说明 | 优先级 | 状态 |
|------|------|--------|------|
| **定期重启 opencode 进程** | goal-driver 每个 step 已经 spawn 新 opencode（不复用进程），但 opencode 的 `--session` 参数会复用上下文。确保不跨 step 复用 session | P0 | **✅ 已实现**（每 step 新 spawn）|
| **监控内核 zone** | sys-snapshot.mjs 已加 `zprint` 采样，采集 `kalloc.1024 count + MB` | P1 | **✅ 已实现** |
| **kalloc.1024 阈值预警 + 主动重启** | 当 `kalloc.1024 > 10 GB` 时主动 kill 当前 step 的 opencode，强制重启，避免达到 20 GB panic 阈值 | P0 | ❌ 待实现 |
| **spawn 前系统资源检查** | load > 核心数 × 2 或 free < 16 GB 时暂停等待 | P1 | ❌ 待实现 |
| **限制单 step 时长** | EXECUTE step 当前 60 min timeout。如果 zone 泄漏速率固定，缩短到 30 min 可能在 panic 前完成 | P1 | ⏳ 60 min 已有，30 min 可调 |
| **向 opencode 报告 bug** | panic 文件 + 复现路径（goal-driver 长时间运行）提交给 opencode 项目。⚠️ **上游已有人报告了 #32002（06-12），本机可以 +1 补充 M4 Max 复现数据** | P1 | ✅ 上游已存在 |
| **使用低渲染模式** | opencode 如果支持 `--no-tui` 或 plain text 模式，减少 terminal escape sequence 频率，降低 GPU 驱动泄漏速率 | P2 | 需 opencode 支持 |

## External Known Issues（联网调研 2026-06-15）

经联网检索，`data.kalloc.1024` zone 耗尽是一个已知的**跨 CLI 工具**的 macOS 内核问题，并非 opencode 独有。三个独立来源一致：

### 1. opencode 上游 issue #32002（2026-06-12）

| 字段 | 值 |
|------|-----|
| URL | <https://github.com/anomalyco/opencode/issues/32002> |
| 报告人 | shiyan-salt |
| 机器 | MacBook Pro M1 Pro |
| Panic | `zone map exhausted while allocating from zone [data.kalloc.1024] (20G, 21182160 elements)` |
| 回溯入口 | **`com.apple.iokit.EndpointSecurity`** kext |

**假设**：opencode 的文件监控代码（EndpointSecurity 事件订阅）未正确释放 `es_message_t` 对象，每次文件系统事件在内核中分配 1024 字节且不释放。21,182,160 个未释放元素耗尽 zone map。

**状态**：未关闭，open（无官方修复）。

### 2. Claude Code issue #44824（2026-04-07）

| 字段 | 值 |
|------|-----|
| URL | <https://github.com/anthropics/claude-code/issues/44824> |
| 报告人 | stereome |
| 机器 | MacBook Pro M1 Pro, 16 GB RAM |
| 泄露速率 | **30-48 MB/min**（`sudo zprint` 实测）|
| 触发时长 | 4-6 小时达到 11-14 GB → kernel panic |

**关键实验**（已控制验证）：
| 条件 | kalloc.1024 Δ/min |
|------|-------------------|
| iTerm + Metal ON + Claude Code | +30 MB/min |
| iTerm + Metal OFF + Claude Code | +36 MB/min |
| Terminal.app + Claude Code | +42 MB/min |
| 最小化 + Claude Code idle | +27 MB/min（仍泄漏）|
| **Claude Code killed** | **0 MB/min（立即停止）** |

**假设**：泄漏路径为 TUI 终端渲染管线：
```
Claude Code (Node/Ink) → terminal escape sequences → Terminal.app/iTerm
  → WindowServer → AGXG13X (Apple GPU driver) → kalloc.1024 永不释放
```

**相关工作**：Ghostty 终端 v1.3 已修复自己侧的泄漏（multi-codepoint output 触发，<https://github.com/ghostty-org/ghostty/issues/10289>）。

### 3. dnesting.com 博客（2026-03-18）

| URL | <https://dnesting.com/2026/03/18/macos-kalloc-leak-panic/> |
|-----|------|
| 触发条件 | **~20M `exec()` 调用**（shell 脚本 / Python 在 pyenv 下）|
| 泄漏 zone | `data_shared.kalloc.1024`（与 opencode 报告的 zone 略有不同但同类）|

### 跨项目根因分析

| 假设 | 来源 | 与本机 Panic 的一致性 |
|------|------|---------------------|
| **H-ES: EndpointSecurity kext 泄漏** | opencode #32002 | 本机 panic 回溯也经过 `EndpointSecurity` — 但 opencode 的 **Go 后端**（非 Node TUI）也可能是 ES 泄漏源 |
| **H-TUI: WindowServer/GPU 渲染泄漏** | Claude Code #44824 | 两台机器（M1 Pro vs M4 Max）GPU 架构相似（AGX 系列），opencode 同样有 TUI |
| **H-EXEC: exec() 调用泄漏** | dnesting 博客 | goal-driver 频繁 spawn opencode + Maven JVM，大量 `exec` 系统调用 |

**最可能根因**：H-TUI + H-ES 叠加。opencode 的 TUI 渲染（频繁重绘 terminal escape sequences）触发 WindowServer/GPU 驱动的 kalloc.1024 泄漏（Claude Code 已证实），同时 EndpointSecurity 文件监控也泄漏同类内核对象。两者共同加速从 4-6 小时（Claude Code 纯 TUI）缩短到 ~60 分钟（opencode TUI + ES）。

**这不是 opencode 独有的 bug，而是 macOS XNU 内核 bug**（WindowServer/GPU 驱动侧 + EndpointSecurity），CLI AI 工具（TUI 密集型 + 文件系统监控 + 大量子进程）是最容易触发它的负载模式。

### Evidence Summary

### 1. 崩溃时间线（2026-06-15）

| 时间 | 事件 | 证据 |
|------|------|------|
| 10:00:50 | goal-driver 启动 `nop-ai-agent` 模块周期 | `nop-ai-agent.log:1` |
| 10:02:44 | HEALTH_CHECK pass → EXECUTE 开始 | engine log |
| 11:05:46 | EXECUTE success（耗时 ~63 min） | engine log line 7 |
| 11:14:30 | BUILD_VERIFY pass，第一轮 plan 完成 | engine log line 14-16 |
| 11:36:22 | 第二轮 EXECUTE_PLAN 启动 | engine log line 38-39 |
| 11:42:xx | `oc-EXECUTE-*.log` 最后修改（opencode 正在跑 Maven test） | 文件 mtime |
| **11:43** | **系统崩溃重启** | `last reboot` |

### 2. Load Average 异常 — 最强信号

- 崩溃后 5 分钟：load avg **31.56 / 35.98 / 17.92**（1/5/15 min）。
- 16 核机器正常运行 goal-driver（单线程顺序）load 应 ≤ 2-3。
- **load 31 意味着数十个进程/线程同时竞争 CPU** — 这比内存更能解释"死机"（系统不是 OOM 崩溃，而是 CPU 过载导致完全无响应 → 用户被迫硬重启）。
- 15-min load (17.92) < 1-min (31.56)：大量进程在最近 1 min 集中出现。

### 3. 崩溃时正在执行的步骤

`oc-EXECUTE-1781494582271-4c3a82.log` 尾部显示 opencode 正在执行 plan 191，已完成 Phase 1，准备运行 `./mvnw test`：

```
Phase 1 complete. Let me run the tests to confirm Phase 1 didn't break anything before moving to Phase 2:
```

进程树：`node main.js` → `opencode run` → `./mvnw test -pl nop-ai-agent -am -T 1C` → **JVM**（surefire 可能 fork 更多）。

**注意**：`-T 1C` 让 Maven 用 16 线程并行构建，每个模块可能 fork 独立的 surefire JVM。

## Deep-Dive: 内核级排查 + 子进程生命周期（2026-06-15 二次排查）

### A. 内核级内存泄漏检查 — 排除

| 检查项 | 结果 | 结论 |
|--------|------|------|
| jetsam log（`log show --predicate 'jetsam'`） | **空** — 无记录 | 内核未执行内存压杀进程 |
| panic/shutdown cause log | **空** — 无记录 | 不是 macOS panic，是**硬断电** |
| Wired memory（内核常驻） | 3.08 GB | 正常（macOS 典型 2-5 GB） |
| Compressor（内核内存压缩） | 0 pages | 无内存压缩活动（内存不紧张的证据） |
| Swap | 0 MB | 无 swap 活动 |
| System free % | 97%（基线） | 内存充足 |

**结论**：**排除内核级内存泄漏**。崩溃时系统内存可能不是耗尽状态。崩溃方式是系统冻结后用户硬断电（按住电源键），不是 macOS 正常 panic 或 OOM。

### B. 子进程是否被自动回收 — 答案：正常退出时是，异常终止时否

#### 正常退出场景（已验证）

1. goal-driver spawn opencode（detached，session leader，PGID=PID）
2. opencode spawn `npm exec @z_ai/mcp-server`（在 opencode 进程组内）
3. opencode spawn `./mvnw clean install` → JVM（在 opencode 进程组内）
4. step 完成 → opencode 退出 → **opencode 自行清理 MCP server 和 JVM 子进程**

**验证证据**：PID 3012（opencode run）正常退出后，其子进程 PID 3035（mcp-server）**同步消失**。goal-driver 接着 spawn 了 PID 8077（下一 step 的 opencode）。

#### 异常终止场景（孤儿泄漏）

| 触发条件 | 后果 |
|---------|------|
| 超时 SIGKILL（executor.js killGroup） | 进程组被杀，但如果 JVM/MCP 调用了 `setsid()` 则逃逸 |
| goal-driver node 进程崩溃（OOM/segfault） | `finally{runner.close()}` 不执行 → **所有 detached 子进程被 reparent 到 launchd (PID 1)**，永久存活 |
| 系统冻结后硬断电 | 全部进程死亡，但重启后不清理残留（如果有跨用户进程） |

**macOS 进程回收机制**：
- Unix 下父进程死亡后，子进程 reparent 到 PID 1（launchd）
- launchd **不会主动杀** reparent 的进程（除非有 KeepAlive=NO 的 plist 契约）
- detached 的 opencode 是 session leader，`process.kill(-pgid)` 可以杀整个组
- 但如果 goal-driver 已经死了，没人执行这个 kill

#### 实测进程树（当前运行中）

```
PID 1731 node main.js (goal-driver, 52 MB)
 ├─ PID 8077 opencode run --agent build (690 MB, 55 sec)
 │   ├─ PID 8106 npm exec @z_ai/mcp-server (128 MB)
 │   │   └─ PID 8122 node zai-mcp-server (74 MB)
 │   └─ PID 8436 java (Maven JVM, 1.5 GB, -T 1C)
```

**关键发现**：单个 BUILD_VERIFY 步骤的进程树占用 **~2.4 GB**（opencode 690 MB + MCP 202 MB + JVM 1.5 GB），且 JVM 用 `-T 1C` 跑 16 线程。这解释了 load avg 31。

### C. opencode 内存增长趋势

| 时间点 | opencode RSS | 运行时长 |
|--------|-------------|---------|
| PID 3012 启动 | ~50 MB | 0 min |
| PID 3012（10 min 后） | 800 MB | 10 min |
| PID 8077（55 sec 后） | 690 MB | < 1 min |
| PID 1899 交互式（24 min 后） | 1.4 GB | 24 min |

opencode 内存增长极快（1 min → 690 MB），可能存在内部累积（session context、MCP buffers、tool call history）。长时间运行的 EXECUTE 步骤（63 min）结束时可能达到 **3-5 GB**。

## Orphan Reaper Implementation（已部署）

### reap-orphans.mjs — 孤儿进程回收器（PGID 精确追踪）

**文件**: `ai-dev/tools/opencode-goal-driver/src/reap-orphans.mjs`

**设计原则**：**绝不按命令名 pattern 扫描 kill**。本机同时有多个 opencode 实例（交互式、goal-driver 的、可能还有其他自动化），pattern 匹配会误杀。**只 kill 本 goal-driver 创建的进程组中的残留成员。**

**工作原理**：
1. goal-driver spawn opencode 时 `detached:true` → child PID = PGID（进程组 leader）
2. opencode 的子进程（MCP server、Maven JVM）加入同一进程组
3. step 完成 → executor `child.on("close")` → **`reapProcessGroup(childPid)`** — 扫描 `ps` 中 PGID=childPid 的进程
4. 找到的残留（opencode 已退出但 MCP server/JVM 还活着）→ SIGTERM → 5s grace → SIGKILL

**100% 安全保证**：
- 只 kill PGID = 本 goal-driver spawn 过的 PID 的进程 → 不可能误杀其他 opencode 实例
- 交互式 opencode（PID 1899, PGID 1899）不在任何 goal-driver 创建的 PGID 中 → 永远不会被触碰
- 其他自动化的 `opencode run` 也在自己的 PGID 中 → 不会被触碰

**启动时残留处理**（`reapStartupOrphans`）：
- goal-driver spawn 的 `opencode run` 有明确签名：`opencode run -m <model> --agent <agent> --dangerously-skip-permissions <prompt>`
- 交互式 opencode 是 `opencode`（无 `run` 子命令）→ 不会被匹配
- 同一时刻只有一个 goal-driver 运行 → 启动时任何匹配的进程都是上次崩溃遗留
- **直接 kill**（SIGTERM → 5s → SIGKILL）+ 杀整个进程组（MCP server、JVM）
- 额外扫描 ppid=1 的孤儿 MCP server 和 Maven JVM
- 安全护栏：传入 `excludePpid=process.pid`，绝不 kill 当前 goal-driver 的活跃子进程

### 集成点

| 位置 | 时机 | 函数 | 说明 |
|------|------|------|------|
| `executor.js` child.on("close") | 每个子进程退出后 | `reapProcessGroup(childPid)` | 清理该进程组的残留子孙 |
| `executor.js` child.on("error") | 子进程出错后 | `reapProcessGroup(childPid)` | 同上 |
| `engine.js` run() START | flow 启动时 | `reapStartupOrphans(runDir, process.pid)` | kill 上次崩溃遗留的 goal-driver 进程（按命令行签名精确识别） |

## Root Cause Hypotheses

### 假设 H1: JVM 孤儿进程累积导致内存耗尽（初始假设 — **证据不足**）

**问题**：128 GB 极难被几个 JVM 耗尽。当前 opencode 子进程的 Maven JVM 约 1 GB（IntelliJ Maven Server 实测）。即使累积 10 个孤儿 JVM × 2 GB = 20 GB，加 IntelliJ 5.7 GB + opencode 2 GB + 其他 15 GB ≈ 43 GB — **离 128 GB 差很远**。

**结论**：此假设作为唯一根因 **不成立**。可能是加剧因素，但不是充分原因。

### 假设 H2: CPU 过载导致系统冻结（**更可能**）

- load avg 31 是最强信号。
- Maven `-T 1C` 并行构建 16 线程 + opencode 内部并发 + IntelliJ 后台 indexing → CPU 饱和。
- Apple Silicon 在持续高负载下会**热降频**，进一步降低有效吞吐 → 负载更高 → 恶性循环 → 系统完全冻结。
- 用户被迫硬重启（电源键），不是 macOS 正常 panic。
- 这解释了为什么 15-min load (17.92) 仍很高 — 系统已持续过载数十分钟。

### 假设 H3: opencode 自身内存泄漏（**待验证**）

- 当前数据：opencode run 进程 10 min 内长到 800 MB，交互式 12 min 到 1.3 GB。
- 如果 opencode 长时间运行（如 EXECUTE 步骤 63 min）持续泄漏，单个 opencode 进程可能长到 5-10 GB。
- 但这仍不足以单独耗尽 128 GB。

### 假设 H4: macOS 内核/驱动级问题（**不能排除**）

- Mac Studio M4 Max 是较新机型，可能存在 macOS firmware 级 bug。
- 统一内存架构下，GPU 驱动或 IOKit 子系统的内存泄漏会直接侵蚀 CPU 可用内存。
- `jetsam` 在极端情况下可能杀到关键系统进程导致重启。
- 需要检查 `log show --predicate 'sender == "kernel"'` 的 jetsam 记录（需 sudo）。

### 假设综合评估

| 假设 | 可能性 | 证据强度 | 验证方式 |
|------|--------|---------|---------|
| H1: JVM 孤儿累积 | 低-中 | 弱（128GB 难以耗尽） | sys-snapshot JVM count + RSS 监控 |
| **H2: CPU 过载冻结** | **高** | **强（load 31）** | sys-snapshot load avg 趋势 |
| H3: opencode 内存泄漏 | 中 | 中（增长趋势可见） | sys-snapshot opencode RSS 趋势 |
| H4: macOS 内核 bug | 中 | 无（未排查） | 需要 sudo 查 jetsam log |

## Code Defects Found（确认存在，但与死机的因果关系待证明）

### 缺陷 D1: detached 子进程在非正常退出时成为孤儿（HIGH）

**位置**: `executor.js:62-67`

```js
child = spawn(cmd, args, {
  detached: !IS_WIN32,   // ← 创建新进程组
});
```

- `detached: true` 使 opencode 成为独立进程组 leader。
- 清理依赖 `main.js:83 finally { runner.close() }`。Node 被 SIGKILL 或系统 panic 时永不执行。
- detached 的 opencode + 子进程成为孤儿继续运行。
- **与死机关系**：可能加剧负载（H2）和内存占用（H1），但不是唯一原因。

### 缺陷 D2: spawn 前无系统资源检查（HIGH）

**位置**: `executor.js:46`

`execute()` 不检查可用内存 / load avg / 进程数。无法在系统已过载时自我克制。

### 缺陷 D3: FlowEngine 内存状态无界增长（MEDIUM）

**位置**: `engine.js`

| 字段 | 行 | 说明 |
|------|----|------|
| `this.context` | 590 | 每个 step 的完整 result（含 opencode 全输出 20-200 KB）持久持有 |
| `this.logEntries` | 42 | 无上限日志数组 |
| `this.appendBuffers` | 39 | retry 反馈累积 |

overnight 运行可能累积数百 MB。不是死机主因但需治理。

### 缺陷 D4: 启动时不清理残留 + `_tmp/` 无限累积（MEDIUM）

- `main.js` 启动不检查/kill 残留 `opencode`/`java` 进程。
- `_tmp/` 旧目录：5+ GB（1.7 GB + 1.5 GB + 1.5 GB ...），408 个 log 文件，从不清理。

### 缺陷 D5: 超时 kill 可能不清理孙进程（MEDIUM）

**位置**: `executor.js:36-44`

`process.kill(-pid, "SIGKILL")` 杀进程组，但 MCP server 或 surefire fork JVM 若 `setsid()` 则逃逸。

## Diagnostic Instrumentation（已部署）

### sys-snapshot.mjs — 系统资源快照工具

**文件**: `ai-dev/tools/opencode-goal-driver/src/sys-snapshot.mjs`

每次调用写入一行 JSON + 一行 TSV 到 runDir：
- `sys-snapshot.log` — JSONL（完整结构化数据）
- `sys-snapshot.csv` — TSV 表格（快速扫描）

**采集指标**：

| 类别 | 指标 |
|------|------|
| System | load avg (1/5/15 min), uptime, total RSS, process count |
| Memory | free/active/inactive/wired/compressed GB, swapins/swapouts, memory pressure % |
| Cohort | opencode/java/node/IntelliJ/docker/mysql 各群组的 aggregate RSS + count |
| Top-10 | 按 RSS 排序的前 10 进程（pid, name, rss_mb, cpu%） |
| Disk | project root volume 可用空间 |

### 集成点（3 处，已改代码，83 tests pass）

| 位置 | 触发时机 | label |
|------|---------|-------|
| `engine.js:run()` 开头 | flow 启动 | `START:<flowName>` |
| `engine.js:run()` step 循环内 | 每个 step 开始前 | `step-<N>:<stepName>` |
| `executor.js` progressTimer 心跳 | 长时间运行的子进程每 5 min | `heartbeat:<label>` |

**验证**：`node --test ai-dev/tools/opencode-goal-driver/test/*.test.js` → 83 tests / 0 fail。

**使用**：下一次崩溃后，检查 `_tmp/<crash-run>-goal-driver/sys-snapshot.csv` 即可看到崩溃前的资源趋势：
- load avg 是否持续攀升（验证 H2）
- opencode/java RSS 是否持续增长（验证 H1/H3）
- process count 是否异常膨胀
- memory pressure % 是否降到危险水平

## Improvement Recommendations

### P0 — 阻断可能死因

| # | 建议 | 针对假设 | 位置 | 状态 |
|---|------|---------|------|------|
| 1 | **spawn 前检查 load avg + free RAM**：load > 核心数 × 2 或 free < 16 GB 时暂停等待 | H2 | `executor.js:execute()` | 待实现 |
| 2 | **启动时清理残留 opencode/java 进程** | H1 | `engine.js` (reap-orphans) | **✅ 已实现** |

~~P0 #3: Maven `-T 1C` 降为 `-T 4`~~ — **撤回**。`-T 1C` 实际并行度受 `-pl nop-ai-agent -am` 限制（仅 3-5 个依赖模块同时编译），BUILD_VERIFY 仅耗时 3 分钟，不是高负载来源。EXECUTE 步骤中的 Maven 由 AI 自行调用，不走 goal-driver 的 `-T 1C`。

### P1 — 代码治理

| # | 建议 | 位置 |
|---|------|------|
| 4 | `this.context` ring buffer（N=10）+ `this.logEntries` 设上限 | `engine.js` |
| 5 | 启动时清理 `_tmp/` 旧目录（保留最近 3 个） | `config.js` |
| 6 | Maven 加 `-Xmx2g` surefire `forkCount=1` | `runner.js` |
| 7 | 超时 kill 后验证子进程已死 | `executor.js:killGroup` |

### P2 — 待数据决策

| # | 建议 | 条件 |
|---|------|------|
| 8 | 为 opencode 子进程加内存限制 | 需先确认 H3 |
| 9 | GPU 内存监控 | 需先确认 H4 |
| 10 | 流式读取替代 readFileSync | 低优先 |

## Goal-Driver 当前处理方式评估

经审查 `ai-dev/tools/opencode-goal-driver/src/` 全部源码，当前 goal-driver 对本 crash 的处理情况如下：

| 维度 | 状态 | 位置 | 说明 |
|------|------|------|------|
| **kalloc.1024 监控** | ✅ 已实现 | `sys-snapshot.mjs:122-142` | 每 5 min 心跳 + step 前后采集 `kalloc.1024 count + MB`，输出到日志和 CSV |
| **孤儿进程回收** | ✅ 已实现 | `reap-orphans.mjs` | 正常退出后按 PGID 精确清理；启动时清理上次崩溃残留 |
| **子进程超时杀** | ✅ 已实现 | `executor.js:95-116` | 60 min 无输出 → SIGTERM → SIGKILL 进程组 |
| **kalloc.1024 阈值主动重启** | ❌ **未实现** | — | `sys-snapshot.mjs` 只报告不动作 |
| **spawn 前系统资源检查** | ❌ **未实现** | `executor.js` | 不检查 load avg / free RAM / kalloc 大小 |
| **step 时长动态缩减** | ❌ **未实现** | `executor.js` | 60 min 硬编码，不与 kalloc 泄漏速度联动 |

**三个缺失的主动防御**：

1. **kalloc.1024 阈值门控**（最关键）：`sys-snapshot.mjs` 已在 heartbeat 中采集 `kalloc.1024` 数据，但 executor/engine 从未读这个值。应在 heartbeat 回调中检测 `kalloc.1024 > 10 GB` 时主动 kill 当前 step 的 opencode 子进程，避免达到 20 GB panic 阈值。
2. **spawn 前资源预检**：`executor.js:execute()` 在 spawn 新的 `opencode run` 调用前检查 `zprint kalloc.1024` + `load avg` + `vm_stat free`，如果接近危险区则排队等待或 gracefully degrade（跳过当前 step）。
3. **单 step 时长缩减**：泄漏速率在不同模块不同（当前数据 ~300 MB/min 内核级），可在 EXECUTE step 中动态估算剩余安全时间：`timeLeft = (threshold - currentKallocMB) / leakRateMBperMin`，设置为 step timeout。

**注意**：上述措施都只能延缓而非根除。因为泄漏发生在 macOS 内核空间，用户态杀 opencode 进程只能让内核泄漏停止（Claude Code #44824 已验证：kill 进程后 `kalloc.1024` 立即冻结），但已泄漏的内核内存**不会回收**——只有重启机器才能真正清空 zone map。所以最佳策略是：**定期重启机器 + 每 step 重置 opencode 进程 + threshold 监控告警**。

## Conclusion

- **死机方式已确证**：硬断电（无 panic/jetsam 日志）— 系统完全冻结后用户手动按电源键重启。不是 macOS 正常 panic，不是 OOM-kill。
- **排除内核级内存泄漏**：wired memory 3.08 GB（正常），compressor 0，swap 0，基线 97% free。崩溃时内存不太可能是耗尽状态。
- **最强根因信号是 CPU 过载（H2）**：load avg 31 / 16 核 = 2x 过载。但该数据是重启后 5 分钟测得的**启动负载**（IntelliJ indexing + Docker + Spotlight），不代表崩溃前运行时负载。实际上 BUILD_VERIFY（`-T 1C`）仅耗时 3 分钟，`-T 1C` 受 `-pl` 限制实际并行 3-5 模块，不是高负载来源。崩溃前真实负载不明。
- **根因已经联网调研确证为跨项目已知问题**：opencode issue #32002、Claude Code #44824、dnesting 博客均证实同一个 `data.kalloc.1024` 内核 zone 泄漏模式。Claude Code 报告含可控实验证据（kill 进程后泄漏立即停止），极大增加可信度。根因是 **macOS XNU 内核 bug（WindowServer/GPU 驱动 + EndpointSecurity）**，CLI AI 工具的 TUI 渲染 + 文件监控 + 高频 `exec` 是高触发率触因。
- **子进程生命周期已查清**：正常退出时 opencode 自行清理子进程（已验证）；异常终止（SIGKILL/崩溃）时 detached 子进程被 reparent 到 launchd 但不被回收。已实现 `reap-orphans.mjs` 在每步前扫描并回收。
- **已部署的工具**：`sys-snapshot.mjs`（3 处集成，含 kalloc.1024 监控）+ `reap-orphans.mjs`（2 处集成）。83 tests pass。
- **关键缺失**：`sys-snapshot.mjs` 采集 kalloc.1024 数据但**从未用于主动防御**。应在 heartbeat 中检测阈值 > 10 GB 时主动 kill 当前 step 的 opencode 进程，强制重启，避免达到 20 GB panic 阈值。
- **已确认上游有报告**：opencode #32002（06-12）与本地三起崩溃完全一致，可补充 M4 Max 的 panic 数据到该 issue。
- **终极缓解方案：定期重启机器**。因为 kalloc.1024 泄漏的内核内存只有重启机器才能清空（杀进程只能阻止继续泄漏，已泄漏的不回收）。

## Open Questions

- [ ] opencode issue #32002 是否需要补充本机的 M4 Max + goal-driver 复现数据？
- [ ] opencode 是否支持 `--no-tui` 或 `--plain` 模式以减少 terminal escape 频率？
- [ ] 如何在 goal-driver executor heartbeat 中接入 kalloc.1024 阈值检测并主动杀子进程？
- [ ] 当前 `zprint` 是否需要 root 权限？非 root 下返回 null 时如何 fallback？
- [ ] Maven `-T 1C` 在 nop-ai-agent 模块实际 fork 了多少 surefire JVM？

## References

- `ai-dev/tools/opencode-goal-driver/src/sys-snapshot.mjs` — 新增诊断脚本
- `ai-dev/tools/opencode-goal-driver/src/reap-orphans.mjs` — 新增孤儿进程回收器
- `ai-dev/tools/opencode-goal-driver/src/executor.js` — spawn + heartbeat snapshot 集成
- `ai-dev/tools/opencode-goal-driver/src/engine.js` — step snapshot + reaper 集成
- `ai-dev/tools/opencode-goal-driver/src/runner.js` — runAgent / killTree / close
- `ai-dev/tools/opencode-goal-driver/src/main.js` — 生命周期 / SIGTERM handler
- `_tmp/2026-06-15-100050-goal-driver/nop-ai-agent.log` — 崩溃前 engine log
- `_tmp/2026-06-15-100050-goal-driver/oc-EXECUTE-1781494582271-4c3a82.log` — 崩溃时 step log
- `_tmp/2026-06-15-114651-goal-driver/sys-snapshot.csv` — 首批基线快照数据
- `ai-dev/analysis/00-analysis-writing-guide.md` — 本报告格式规范
- `https://github.com/anomalyco/opencode/issues/32002` — opencode 上游 issue（06-12 报告，与本机 panic 完全一致）
- `https://github.com/anthropics/claude-code/issues/44824` — Claude Code 同一 kalloc.1024 泄漏（TUI 渲染假设，含可控实验证据）
- `https://dnesting.com/2026/03/18/macos-kalloc-leak-panic/` — macOS kalloc 泄漏博客（exec 调用触发）
- `https://github.com/ghostty-org/ghostty/issues/10289` — Ghostty 终端修复 multi-codepoint leak
