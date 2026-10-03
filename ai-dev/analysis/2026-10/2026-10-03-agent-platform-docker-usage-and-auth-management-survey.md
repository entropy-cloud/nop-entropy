# Agent 平台调研：Docker 容器隔离使用现状 与 鉴权/授权管理实践

> Status: resolved
> Date: 2026-10-03
> Scope: `~/ai/agent-platforms/` 下 10 个自托管 AI agent 平台（codeg / agent-orchestrator / kandev / omnigent / optio / paseo / MagesticAI / vibe-kanban / sandboxed.sh / codeforge-agent），外加 `~/ai/movo`（himovo/movo，DSH 商业封装）与 `~/ai/dsh-src/deepseek-harness`（DSH 主仓库）
> Conclusion: 10 个样本中「每任务容器隔离」零真实现、「鉴权默认安全」零实现；容器隔离只有 optio（K8s 每任务 pod）与 sandboxed.sh（systemd-nspawn，但非 Linux 默认退化）达到真隔离，鉴权只有 omnigent 与 movo 的分层做法可直接上公网，其余 8 个均为「能跑起来」而非「能暴露到公网」。

## Context

- 决策点：自建「远程 AI agent 任务平台」时，容器隔离层与鉴权层是**唯二无法从现有方案直接复用**的部分，必须自建，因此需要先摸清业界现状与可借鉴边界。
- 硬需求（本报告只覆盖其中两条轴）：
  1. 本机不装客户端，浏览器远程访问平台，提交任务 / 查看进度 / 人工插话 / 起停控制
  2. 任务针对 git 工程：给 git 地址 → clone 到本地目录 → 在目录中执行 → 结果提交为一个 git 分支
  3. **每个任务用 Docker 做隔离**
- 约束条件：docker 29.4.3（macOS / Docker Desktop）、部署形态为单台机器 + `docker compose`、不引入 Kubernetes。
- 本报告不覆盖：任务状态机设计、流式协议与断线重连、git 交付链路（另见综合代码质量审计）。

## 调研方法与样本

| 项 | 说明 |
|---|---|
| 样本 | 10 个平台，均 `git clone --depth 1` 浅克隆，**无提交历史可参考**，全部结论基于当前代码实读 |
| 方法 | 4 个并行审计代理按统一 rubric 读代码，所有判定带 `文件路径:行号` |
| 交叉验证 | README 声明逐条 grep 验证；定量指标（LOC / 测试比 / lockfile / CI workflow）由脚本统计 |
| 已知偏差 | 浅克隆无法判断演进历史；star 数等外部流行度信号不作为质量依据 |

规模基线（生产代码行数 / 测试占生产比）：

| 仓库 | 生产 LOC | 测试/生产 | 主语言 | lockfile | CI workflow |
|---|---|---|---|---|---|
| codeg | ~743k | 22% | Rust + TS | 双 | 2 |
| agent-orchestrator | ~284k (Go) | 101% | Go + TS | 双 | 14 |
| kandev | ~457k | 115% | Go + TS | 双 | 25 |
| omnigent | ~503k (py) | 193% | Python + TS | 双 | 89 |
| optio | ~204k | 45% | TS + Kotlin/Swift | 双 | 4 |
| paseo | ~2716k (TS) | 81% | TypeScript | 有 | 12 |
| MagesticAI | ~218k | 25% | Python + React | 有 | 2 |
| vibe-kanban | ~143k | 0.9% | Rust + TS | 双 | 9 |
| sandboxed.sh | ~490k | 5% | Rust + TS | 双 | 3 |
| codeforge-agent | **931** | 4% | TypeScript | 有 | 1 |

---

# Part A — Docker 使用与容器隔离

## A1. 隔离真伪矩阵

「每任务隔离」列的判定标准：agent 是否运行在**与宿主文件系统/网络/进程树隔离**的执行环境里。

| 仓库 | 实际隔离手段 | 每任务真隔离 | 证据 |
|---|---|---|---|
| **optio** | K8s 每任务 pod + 每 pod NetworkPolicy | ✅ **真**（但需 K8s） | `apps/api/src/services/repo-pool-service.ts:1177-1186`；`helm/optio/templates/rbac.yaml:63-91` 命名空间级 Role |
| **sandboxed.sh** | systemd-nspawn（namespace + cgroup） | ⚠️ Linux 真隔离；**macOS/Windows 默认退化宿主执行** | `src/nspawn.rs:606-616`；退化逻辑 `src/nspawn.rs:91-94` + `src/workspace/mod.rs:4779-4784` |
| omnigent | 12 个远程沙箱 provider（E2B microVM / Modal / Daytona / K8s…）+ 本地 bwrap/Seatbelt | ⚠️ 远程路径真隔离，**但都不是 Docker**；本地路径是进程级 confinement | `omnigent/onboarding/sandboxes/`；`sandbox/bwrap.py`、`sandbox/seatbelt.py` |
| kandev | 每任务容器 + 独立 network + 内存/CPU 限额 | ❌ 名义隔离，零安全加固 | `apps/backend/internal/agent/docker/client.go:354-370` |
| codeforge-agent | 挂载宿主 `docker.sock` 后拉兄弟容器 | ❌ **等价宿主 root** | `docker-compose.yml:19-20` |
| MagesticAI | 宿主机 `asyncio.create_subprocess_exec` | ❌ 仅 git worktree 文件级隔离 | `apps/web-server/server/services/agent_service.py:783,866,2239,2458` |
| agent-orchestrator | 宿主机进程（Electron 桌面） | ❌ agent 以宿主用户权限运行 | `backend/go.mod` 中 `k8s.io` / `containerd` / `docker` 全零命中 |
| codeg | 单容器部署（应用自身），任务共享 `/data` 卷 | ❌ 非每任务 | `docker-compose.yml`；`src-tauri/src/web/mod.rs` |
| paseo | daemon 单容器 + node-pty 沙箱 | ❌ 非每任务 | `packages/server/src` 内 docker 仅 2 处注释 |
| vibe-kanban | **0 行 Docker**，`container_ref` 只是 worktree 目录路径别名 | ❌ 命名误导 | `crates/local-deployment/src/container.rs:1250-1273`；`ensure_container_exists` 实为 `ensure_workspace_exists` |

## A2. 五种「假隔离」模式分类

所有未达标样本可归入 5 类，每类都有可复用的识别特征：

**类型 1 — 命名误导（最危险）**
把 worktree 目录 / 逻辑容器命名为 `container`，代码里零行 Docker。外部评估者（含 AI）会据此误判安全模型。
- `vibe-kanban`：`services/container.rs:762` `ensure_container_exists`、`is_container_clean`、`ContainerRef` 全部指向 `PathBuf`
- README 从未声称 Docker 隔离，但类型命名构成实质误导

**类型 2 — 共享 daemon 逃逸（最严重）**
把宿主 Docker socket 挂进一个对外暴露 HTTP 端口的容器。
- `codeforge-agent/docker-compose.yml:20`：`/var/run/docker.sock:/var/run/docker.sock`，同时 `:10` 暴露 `8080:8080`
- 后果：非 root 容器用户（`Dockerfile:71` `USER codeforge`）在该 socket 上等同于宿主 root —— `docker run -v /:/host` 即完全控制宿主。**非 root 承诺在此完全无效。**
- 该项目 README 同时声称 "Every task runs in an isolated Docker container" 与 "isolated from the host"

**类型 3 — 默认静默退化（最隐蔽）**
真隔离代码存在，但在目标平台不可用时自动 fallback 到宿主执行，只留一行 warn，UI 上仍显示为「容器模式」。
- `sandboxed.sh/src/nspawn.rs:91-94`：`allow_container_fallback()` 默认值 = `!cfg!(target_os = "linux")`
- `sandboxed.sh/src/workspace/mod.rs:4779-4784`：nspawn 不可用 → 走 `build_container_fallback`（宿主执行）
- 且官方部署文件自己把隔离开关注释掉了：`docker-compose.yml:16-17` `# privileged: true` / `# cgroup: host`
- **结论：在 macOS Docker Desktop 上用官方 compose 部署 sandboxed.sh，得到的是零隔离**

**类型 4 — 有容器无加固（名义 vs 实质）**
`kandev/apps/backend/internal/agent/docker/client.go:354-370` 的 `buildHostConfig`：

```
Mounts / NetworkMode / AutoRemove / PortBindings / ExtraHosts / Resources(Memory, CPUQuota)
```

缺失项（全仓 grep `cap-drop|no-new-privileges` 零命中）：

| 缺失控制 | 风险 |
|---|---|
| `CapDrop` | 默认 capability 集（含 `SYS_ADMIN` 语义面）保留 |
| `SecurityOpt: no-new-privileges` | setuid 二进制可提权 |
| `ReadonlyRootfs` | 容器内可改写镜像层 |
| `PidsLimit` | fork bomb 可打满宿主 |
| `UsernsMode` | root 映射回宿主 |
| 镜像白名单 / digest pin | 可拉取任意镜像 |
| seccomp / AppArmor profile | 默认 profile 宽松 |

额外问题：`internal/agent/runtime/lifecycle/container.go:617-619` 默认注入 `host.docker.internal:host-gateway`，容器内可直连宿主。
而 README:132 写的是 "Docker | Runs the agent in an isolated Docker container"。

**类型 5 — 文件级隔离冒充容器隔离**
- `MagesticAI/apps/backend/core/workspace.py:6-7` 模块 docstring 自述："Handles workspace isolation through Git worktrees" —— 诚实，但产品表述常被升格为「sandboxed」
- 实例级而非任务级资源上限：`docker-compose.yml:71-76` 整个应用 `memory: 2G`，`:77-78` 整个实例共享 `tmpfs /tmp:size=500M`，无 per-task 配额

## A3. 真实隔离方案横向对比

| 方案 | 内核隔离 | 网络隔离 | 启动开销 | 部署依赖 | 样本 |
|---|---|---|---|---|---|
| Docker 默认 | namespace+cgroup | 需显式配置 | 低 | Docker | kandev（未加固） |
| Docker 加固 | +capdrop/userns/seccomp | `--network=none` / 内部网络 | 低 | Docker | **无样本** |
| systemd-nspawn | namespace+cgroup+可配 capability | `--private-network` | 中（rootfs 缓存） | Linux 宿主 + `systemd-container` | sandboxed.sh |
| K8s pod + NetworkPolicy | 同 Docker + 声明式 | 每 pod egress 白名单 | 中 | K8s 集群 | optio |
| microVM（E2B/Firecracker） | 硬件虚拟化 | 独立网络栈 | 高 | 远端 provider | omnigent（远程路径） |
| 进程级 confinement | Landlock/bwrap/Seatbelt/ACL | 无（共享宿主网络） | 极低 | 无 | DSH `packages/sandbox/`、omnigent 本地路径 |

`optio` 的 NetworkPolicy 值得单独标注：`repo-pool-service.ts:1177-1186` 给每个 repo pod 限制 egress —— 这是隔离的关键一环，多数同类项目会漏。同项目的 RBAC 也克制：`helm/optio/templates/rbac.yaml` 用命名空间级 `Role` 而非 `ClusterRole`，可选的 `ClusterRole` 只读 nodes/namespaces 且 `rbac.clusterRole.create=false` 可关。

DSH 主仓库的立场值得记录（`docs/subsystems/sandbox.md`）：官方明确把容器/微虚机**排除**在 `ctx.sandbox` 抽象之外 —— "Containers, microVMs, and remote execution are sibling implementations of whole capability seams, not providers of `ctx.sandbox`"。其 `ctx.sandbox` 只管文件系统效应三档（`read-only` / `workspace-write` / `danger-full-access`），后端为 Linux bwrap/Landlock、macOS Seatbelt、Windows ACL restricted-token。

## A4. Docker 隔离最小加固基线（从负面样本反推）

若采用 Docker 作为每任务隔离，建议的最小控制集（**注意：10 个样本无一完整实现**）：

```
# 每任务容器必备
--cap-drop=ALL                      # 或白名单（kandev 缺失）
--security-opt=no-new-privileges    # kandev 缺失
--read-only                         # + tmpfs /tmp 白名单（kandev 缺失）
--pids-limit=1024                   # kandev 缺失
--network=none                       # 或独立内部网络 + 显式 egress 白名单
--user=<non-root uid>
--memory=2g --cpus=2 --pids-limit    # 资源限额（kandev 仅前两项）
--ulimit nofile=1024:1024
--security-opt seccomp=<profile>
image@sha256:<digest>               # digest pin，非 tag（无样本）
# 禁止注入
# - 不挂 docker.sock（codeforge 错误示范）
# - 不注入 host.docker.internal:host-gateway（kandev 错误示范）
```

宿主侧配套（各样本的正向做法）：
- 生命周期：落库 → SIGTERM → 等待窗口 → SIGKILL 升级（`MagesticAI/agent_service.py:2516-2545` 的 5s 窗口；`vibe-kanban/local-deployment/src/container.rs:1404-1460` 的 5s + `kill_process_group`）
- 进程组而非单进程杀：`vibe-kanban/utils/src/process.rs`（防 MCP server 变孤儿）
- 僵尸回收：`omnigent/runtime/harnesses/process_manager.py:1541-1631`（读 `/tmp/ap-*` sentinel + `_pid_alive` 判定 → SIGTERM → 3s grace → SIGKILL）
- 崩溃恢复：`codeg/src-tauri/src/work_task/engine.rs:419-495` `boot_reconcile_interrupted`，`Merging` 态故意豁免改从 git 真相重建

## A5. 结论：Docker 隔离必须自建

- 唯一现成的每任务隔离实现是 `optio`，但绑定 Kubernetes，与「单台机器 + docker compose」的部署约束冲突
- `sandboxed.sh` 的 nspawn 路线在 macOS 上不可用（类型 3 退化），且其 read-only git guard（`src/workspace/mod.rs:3515-3691`，180 行内联 shell PATH shim，覆盖 `GIT_DIR`/`GIT_COMMON_DIR`/`commondir`/符号链接逃逸等绕过路径）**在退化到宿主执行时完全失效** —— 这是最危险的一点：UI 显示容器模式，但防护退化为宿主的 PATH shim
- DSH 的 `mirage` 插件含唯一 Docker runtime（`typescript/packages/node/src/runtime/sandbox/docker/runtime.ts`），但它是独立产品 strukto-ai，非 DSH 插件，集成成本高

---

# Part B — 鉴权与授权管理

## B1. 鉴权现状矩阵

「默认状态」指**不额外配置时**的实际行为。

| 仓库 | 默认状态 | 密码哈希 | Token | 会话吊销 | 多租户/授权 | 越权语义 | 安全分 |
|---|---|---|---|---|---|---|---|
| **omnigent** | 需登录 | argon2id（`passwords.py`，引 NIST SP 800-63B） | JWT + 设备码 | ✅ | ✅ members + 角色 | 独立 CSWSH 模块（`ws_origin.py` 348 行） | **9** |
| **movo**（DSH 封装） | 需登录 | PBKDF2-HMAC-SHA256 120k 轮（`admin-api/app/core/security.py:14-27`） | HMAC 签名 JWT 风格 + session_id | ✅ 登出/改密吊销 | ✅ 多租户 challenge token + RBAC | RBAC + 配额 + 审计 | **~8** |
| kandev | ⚠️ **默认关闭** | 未配置（`config_test.go:227` 反向锁定默认无鉴权） | 32B 不透明 token，只存 SHA-256 摘要（`auth/tokens.go:59-61`） | 滑动过期 | scope/role 存在 | — | 6（代码 9 / 默认关闭） |
| MagesticAI | 需登录 | bcrypt（`auth_routes.py:35`） | JWT，access/refresh 分离 + 算法白名单（`auth.py:35-45`） | refresh 不轮换（自述） | ❌ **只有认证无授权** | 无 | 5 |
| optio | ⚠️ **`.env.example` 出厂 `OPTIO_AUTH_DISABLED=true`** | session cookie + PAT | session + `optio_pat_*` | ✅ | ✅ workspace + admin/member/viewer | ✅ 越权返 **404** | 5 |
| sandboxed.sh | ⚠️ **`.env.example` 默认 `DEV_MODE=true`** | PBKDF2 100k 轮（2026 年偏弱） | JWT HS256，无 `jti`/`aud`/`iss` | ❌ 无状态，30 天有效 | 单租户 | — | 5 |
| codeforge-agent | ⚠️ 默认口令 `changeme`（`docker-compose.yml:12`） | 无，明文比对 | cookie 值 = **明文口令**（`auth.ts:28`） | ❌ | ❌ | — | 1 |
| paseo | 需密码 | bcrypt cost 12 | X25519 静态密钥对 + E2EE | — | 单用户 | — | 4 |
| agent-orchestrator | 需共享密码 | ⚠️ **无 salt SHA-256**（`mobilebridge/config.go:126-129`） | 全实例一个密码 | ⚠️ 旧密码永久保留 | ❌ `domain` 层零 owner 概念 | — | 3 |
| codeg | 需 token | 无 | ⚠️ 单静态 token + `==` 非恒定时间（`web/auth.rs:33`），明文落 SQLite | ❌ 无过期/轮换 | ❌ | — | 5 |
| vibe-kanban | ❌ **本地 server 零鉴权** | — | — | — | ❌ | — | 3 |
| DSH 主仓库 | ❌ 无鉴权层 | — | — | — | ❌ | — | — |

## B2. 六个鉴权反模式

**R1 — 默认关闭并把「无鉴权」写成测试契约**
- `kandev/apps/backend/internal/profiles/profiles.yaml:60-63`：`KANDEV_FEATURES_AUTH` 在 prod/dev/e2e 全部 `"false"`
- `kandev/internal/auth/httpmw/middleware.go:34-41`：`ModeDisabled` 时注入 `SyntheticIdentity()`（**RoleAdmin**）后 `c.Next()`
- `kandev/internal/common/config/config_test.go:227-228`：存在一条测试**专门锁定**「认证默认必须 opt-in」
- 组合后果：开箱即用是**裸奔 + 满权限**，而 CI 绿灯给出虚假安全感

**R2 — 出厂配置关闭鉴权且无生产保护**
- `optio/apps/api/src/services/oauth/index.ts:44-46`：`isAuthDisabled()` 只读 env，无 `NODE_ENV` 判断、无启动断言（已 grep `index.ts`/`server.ts` 零命中）
- `.env.example` 出厂 `OPTIO_AUTH_DISABLED=true`
- 爆炸半径：`plugins/auth.ts:31,198` HTTP/WS 鉴权钩子直接 return；`ws/ws-authz.ts:68` 所有 WS 授权通过；`routes/model-providers.ts:81` + `services/work-ownership.ts:32` `isAdmin: isAuthDisabled() || ...` → **误配即全员 admin、租户隔离全失效**

**R3 — 口令直接作为凭据载体**
- `codeforge-agent/src/auth.ts:28`：`res.cookie('codeforge_auth', password)` —— cookie 值即口令本身，无 session id、无服务端状态、无法撤销
- `src/auth.ts:27`：允许 `?password=xxx` 走 URL → 进 access log / Referer / 浏览器历史；`:46` 还 encodeURIComponent 后拼进 redirect
- `src/auth.ts:21,27,38`：`===` 非恒定时间比较
- `src/proxy.ts:27,37`：无条件 `console.log` 完整 URL → 口令进容器日志
- 旁证：`Dockerfile:8-10` 与 `:34` 两处安装 `python3 make g++`，全部开销来自一个**声明但从未 import** 的 `node-pty` 死依赖

**R4 — 弱哈希与可绕过的锁定窗口**
- `agent-orchestrator/mobilebridge/config.go:126-129`：无 salt SHA-256（注释称「明文从不参与比较」，但对 SHA-256 几乎无意义 —— 哈希一旦从内存转储/遥测/崩溃报告泄漏即彩虹表爆破）
- `config.go:108-121`：生成密码仅 8 字符，且 `int(b) % 62` 有约 1.6% 取模偏置
- `config.go:89`：锁定窗口每分钟过期 → 5 次/分钟 = 7200 次/天；`retired []string` 无上限无过期，轮换越多爆破面越大

**R5 — 有认证无授权（最易被误判为「已做鉴权」）**
- `MagesticAI/apps/web-server/server/routes/git.py` 全文 1585 行：**0 处** `owner_id` / `user_id` / 项目归属校验，**0 处** `Depends(get_current_user)`
- `git.py:41-42` 的 `path` 是客户端任意提供的宿主机路径，未校验是否属于该用户的已注册项目
- `git.py:23-36` `run_git_command` 是通用 `["git"] + args` → 任一登录用户可对服务器上**任意目录**执行任意 git 子命令，并可通过 `POST /clone`（`:184`）向任意路径 clone
- `routes/terminal.py:51,189`：`shell` 参数零校验（无白名单、无路径检查），`cwd` 任意
- 后果：**任一登录用户 = 服务器上该 uid 的任意命令执行**。`Dockerfile` 的 `USER codeforge` 非 root 承诺因此失效
- 同类但更轻：`codeg` 单 token 无角色无过期；`agent-orchestrator` `domain` 层零 owner 概念

**R6 — 中继/隧道路径绕过主鉴权**
- `paseo/packages/server/src/server/session-admission-auth.ts:26-31`：
  ```ts
  if (!credential) {
    // COMPAT(relayPasswordOptional) ...
    if (transport === "relay") {
      return { admission: { principalId: "owner", permissions: OWNER_PERMISSIONS } };
    }
  ```
  设了 daemon 密码，但 relay 传输不带 `auth` → 直接以 owner 放行。调用链无二次拦截（`bootstrap.ts:1748` → `websocket-server.ts:1673-1683`）
- `packages/relay/src/cloudflare-adapter.ts:421-431`：relay 服务端仅按 `serverId` 路由，**无共享密钥、无客户端证书、无签名**，而 `serverId` 明文在配对 URL 里（`connection-offer.ts:48-51`）
- E2EE 无前向保密、无重放窗口：`daemon-keypair.ts` 是**静态**长期密钥对
- `vibe-kanban` 的对照：`crates/remote/src/auth/middleware.rs:33-120`（托管版）做得很好 —— JWT 解码 → 服务端 session 查询 → `revoked_at` → user_id 交叉验证 → 统一 401 不泄露原因，但该 crate 被 `Cargo.toml:36` 排除出 workspace 且仅 17 个测试；自托管 server 走 `signed_ws.rs:26` `LocalPlain`

## B3. 正向实现清单（可直接借鉴）

### B3.1 密码与用户枚举

| 实现 | 位置 | 要点 |
|---|---|---|
| argon2id + 统一错误 | `omnigent/server/passwords.py` | 引用 NIST SP 800-63B §5.2.2；`:64-68` mismatch 与 malformed hash 抛同一个 `InvalidPasswordError` |
| 用户枚举时间均衡 | `omnigent/server/routes/accounts_auth.py:284-293` | **用户不存在时也跑一次 argon2 verify**（对 sentinel hash），保证响应时间恒定 |
| 机会性 rehash | 同文件 `:304-314` | `needs_rehash` → 登录时重写，`noqa: BLE001 — never fail login on rehash` |
| 登录时序均衡 | `kandev/internal/auth/service.go:118-124` | `dummyHash` 对齐未知邮箱耗时；`:44` 滑动窗口限流 5 分钟 10 次 |
| PBKDF2 + 随机盐 + 恒定时间 | `movo/services/admin-api/app/core/security.py:14-27,64` | 120k 轮 + 16B 盐 + `hmac.compare_digest` |

### B3.2 Token 与会话

| 实现 | 位置 | 要点 |
|---|---|---|
| 不透明 token 只存摘要 | `kandev/internal/auth/tokens.go:59-61` | 32B `crypto/rand`，DB 只存 SHA-256 → DB 泄漏不泄漏可用凭证；PAT 带 `kandev_pat_` 前缀便于 UI 识别 |
| JWT 算法白名单 + 类型分离 | `MagesticAI/apps/web-server/server/auth.py:35-45` | `algorithms=[...]` 防 alg=none/HS256 混淆；`:41-42` 拒绝把 refresh 当 access 用 |
| JWT secret 持久化 | `MagesticAI/apps/web-server/server/config.py:135-142` | 落盘 `~/.magestic-ai/.jwt_secret`，重启后既有 token 仍有效 |
| 会话吊销 | `movo/.../auth.py:380-393,328-334` | 登出吊销 session；改密吊销当前 session |
| 幂等重投 | `paseo/packages/server/src/server/message-receipts/index.test.ts:34-49` | 断线重投不重复投递；provider 结果不确定时**拒绝盲重放**并抛 `agent_request_outcome_unknown` |

### B3.3 网络暴露边界（最值得抄的一组）

DSH 主仓库的立场是明确拒绝：`packages/client/connection/README.md:9` 明文
> `dsh web --host 0.0.0.0` is **intentionally unsupported until remote access has an authentication layer**. The fence is a reachability policy, **not authentication**.

`docs/subsystems/web-server.md` 进一步说明 host 只接受 `127.0.0.1`（默认姿态）与 `0.0.0.0`（蓄意暴露），**无 TLS、无鉴权、无 origin 策略**。

`movo` 的解法是**结构性消除裸暴露面**，而不是打补丁：

| 措施 | 位置 | 说明 |
|---|---|---|
| loopback / secure host 强制校验 | `movo/services/chat-api/dsh/runtime-host/src/host-auth.mjs:3,9` | `assertLoopbackHost()`；`assertSecureHost()` 要求非 loopback 必须有 ≥32 字符 token |
| 构造期校验 | `runtime-http-server.mjs:16-17` | token < 32 字符直接 throw |
| 每请求 401 | `runtime-http-server.mjs:23-25` | realm `askai-dsh-runtime` |
| 恒定时间比较 | `host-auth.mjs:16` | `timingSafeEqual` |
| **不发布端口 + 内部网络** | `movo/docker-compose.yml:333-335` | Runtime Host 只在 `internal: true` 的 backend 网络，宿主机与外网都够不到 |
| 凭据注入 | `docker-compose.yml:119-140` | `/run/askai-secrets/runtime.env`，健康检查也带 Bearer |
| 禁止传原始 cwd | `runtime-http-server.mjs:123` | `raw cwd is forbidden over the Runtime Host API; use workspaceId` |

kandev 的反向代理信任边界同样值得抄（`internal/auth/httpmw/middleware.go`）：

| 措施 | 位置 | 说明 |
|---|---|---|
| `StripUntrustedForwardedHost` | `:61-95` | 非受信 peer 的 `X-Forwarded-Host` 直接删除，防攻击者自选端口诱导 cookie 名 |
| header 去重 | `:139-152` | SHA-256 摘要做 key，避免超大 header 撑爆内存 |
| 日志 rune 边界截断 | `:120-128` | 防截断产生非法字符 |
| 公开路径白名单逐条注明理由 | `:250-300` | 每条匿名路径附「自证身份」说明；`isPluginWebhookPath` 用结构化匹配替代 `strings.Contains` |
| 端口作用域 cookie 名 | `internal/auth/service.go:64-79` | `kandev_session_<port>`，多实例不互相覆盖 |

### B3.4 授权与越权语义

| 实现 | 位置 | 要点 |
|---|---|---|
| 越权返 404 不返 403 | `optio/apps/api/src/routes/tasks.ts:889-891` | 不泄露资源存在性 |
| 复合索引支撑租户隔离 | `optio/apps/api/src/db/schema.ts:230-232` | `(workspace_id, state)`、`(workspace_id, updated_at)` |
| token 不走 URL query | `optio/apps/api/src/plugins/auth.ts:210-212` | WS token 只走 cookie 与 `Sec-WebSocket-Protocol`；注释显式说明原因 |
| CSRF 白名单 | `optio/plugins/auth.ts:48-72` | `MUTATING_METHODS` + `SELF_SCOPED_MUTATIONS` 正则 |
| CSWSH 独立模块 | `omnigent/server/ws_origin.py` | 348 行；在任何 `websocket.accept()` **之前**执行（`:20-23`）；私有 close code 4403 与 auth 失败 1008、隧道不匹配 4004 区分；非 local 模式默认从 passthrough 翻转为 deny-by-default（`:68-71`） |
| 人机分工验收门 | `DSH-taskboard/README.md:38` | "Agents can submit verified work to `in_review`. **Only an authenticated human UI or CLI operation can accept it as `done`**." |

### B3.5 命令与 git 策略门禁（fail-closed）

这是 omnigent 最深的一段（`omnigent/policies/builtins/_shell.py:290-360`）：

| 措施 | 位置 | 说明 |
|---|---|---|
| wrapper 递归剥离 | `_shell.py:290-303` | 按 basename 匹配 → `/usr/bin/sudo -u root git push` 与裸 `sudo` 同等处理；`env -S` 特例留待 re-parse 而非当值吞掉 |
| **兜底反制** | `_shell.py:333-347` `is_unresolved_invocation()` | 注释：把无法解析的调用「当作非受管命令」正是让 `sudo -u root git push` 溜过门禁的原因，因此每个 policy 必须走自己的 un-parseable 处理（ASK / 配置动作）。这是 `_FLAG_WRAPPERS` 枚举可能落后于新 wrapper 时的 backstop |
| 目标不明则问人工 | `policies/builtins/github.py:30-33` | repo 无法确定 → **ASK** 而非猜测；MCP 结构化调用无法判定 → **DENY**（"fails closed"） |
| git URL 严格文法 | `omnigent/server/managed_hosts.py:913-980` | 只接受 `https://<host>/<path>` 或 `git@<host>:<path>`，else 直接 raise → 阻断 `ext::` / `file://` / `--upload-pack` |
| 拒绝 URL 内嵌凭据且错误不回显 URL | `managed_hosts.py:946-953` | 注释：token 否则会被逐字持久化进 session label 与 sandbox Pod spec，并泄漏进错误/日志 |
| 分支名逐条实现 check-ref-format | `omnigent/host/git_worktree.py:53-86` | 拒 `-` 开头、`/` 首尾、`.` 结尾、`.lock` 组件、`..`、`//`、`@{`、`@`、非法字符集 |
| git 命令纯 argv | `agent-orchestrator/.../gitworktree/commands.go:5-14` | 每个 git 调用是 argv 构造器，执行走 `CommandContext` **不经 shell**；分支名用 `git check-ref-format` 官方校验而非正则黑名单 |
| worktree 删除故意不加 `--force` | `commands.go:46-50` | 注释：脏 worktree（agent 未提交工作）**必须**让 `git worktree remove` 失败，从而把 session 路由到 Skipped 而非删除进行中的改动 |
| git submodule 加固 | `kandev/internal/worktree/submodule.go:41-52` | `protocol.allow=never`、`protocol.ext.allow=never`、`core.hooksPath=/dev/null`（防 CVE-2018-17456 / CVE-2022-39253 / CVE-2018-11235），注释写明每个 CVE 的攻击向量；`:50-60` 有真实恶意 `.gitmodules` 回归测试 |
| 凭证走 credential-helper | `kandev/internal/repoclone/git_credential_helper_unix.go` | 不进 argv、不进环境变量明文；日志脱敏 `clone.go:46` |
| 凭据文件不提交进仓库 | `optio/apps/api/src/utils/pod-env.ts:40-44` | 写入 `info/exclude`，防 agent 的 `git add -A` 把含凭据的 `.mcp.json` 提上去 |
| 参数校验的漏点（反面） | `optio/apps/api/src/schemas/common.ts:28-32` | `IdParamsSchema` 用 `z.string()` 而非 `z.string().uuid()`，而 `repo-pool-service.ts:1300-1331` 把该值原样插值进 `bash -c` 和 `rm -rf`。当前因 id 由 DB 生成而不可达，但同文件 `:130-133` 对 `repoBranch` **有**正则校验 → 说明是漏项而非无知 |

## B4. 推荐鉴权分层基线

综合 12 个样本，远程暴露的 agent 平台建议按五层组织，每层都有样本背书：

| 层 | 要求 | 参考实现 |
|---|---|---|
| L1 传输 | 外部反代终结 TLS；服务本身只监听 loopback 或内部网络 | `movo/docker-compose.yml:333-335`（`internal: true` + 不发布端口）；DSH `web-server.md` 的 loopback 默认姿态 |
| L2 网关 | 认证反向代理 + host 白名单 + 剥离不可信 `X-Forwarded-*` | `kandev/internal/auth/httpmw/middleware.go:61-95`；`dsh-task-board/README.zh.md` 的 `trustedProxyHosts` + `proxyTokenEnv` 范式 |
| L3 会话 | argon2id/bcrypt + 恒定时间比较 + 用户枚举时间均衡 + 会话吊销 + 登出 | `omnigent/server/passwords.py`、`routes/accounts_auth.py:284-293`；`movo/.../auth.py:380-393` |
| L4 授权 | 资源归属校验（**每个资源入口都要，不只是路由层**）+ 角色 + 越权返 404 | `optio/routes/tasks.ts:889-891`、`db/schema.ts:230-232`；反面教材 `MagesticAI/routes/git.py`（0 处归属校验） |
| L5 策略门禁 | git/命令写操作 fail-closed；无法判定时 ASK 或 DENY，绝不默认放行 | `omnigent/policies/builtins/_shell.py:333-347`、`github.py:30-33` |

三条必须写进门禁的硬规则（每条都对应至少一个真实缺陷）：

1. **鉴权不得默认关闭**，且关闭必须由启动期断言保护（`kandev/profiles.yaml:60-63` + `config_test.go:227` 反向锁定；`optio/oauth/index.ts:44-46` 缺 `NODE_ENV` 断言）
2. **口令不得作为凭据载体**：不进 cookie 值、不进 URL query、不进日志（`codeforge-agent/auth.ts:27,28`、`proxy.ts:27`）
3. **有认证不等于有授权**：每个接受客户端路径参数的入口都要做归属校验（`MagesticAI/routes/git.py` + `terminal.py:51`）

---

# 综合对比与选型含义

| 仓库 | 容器隔离真伪 | 鉴权默认安全 | 授权完整 | 安全分 |
|---|---|---|---|---|
| omnigent | ⚠️ 远程 provider 真隔离但非 Docker；本地进程级 | ✅ | ✅ | **9** |
| movo（DSH 封装） | ❌ 共享常驻容器 | ✅ | ✅ | ~8 |
| kandev | ❌ 有容器零加固 | ❌ 默认关闭 + RoleAdmin | 部分 | 6 |
| codeg | ❌ 单容器非每任务 | ⚠️ 单 token 非恒定时间 | ❌ | 5 |
| optio | ✅ **K8s 每任务 pod + NetworkPolicy** | ❌ 出厂 AUTH_DISABLED=true | ✅ | 5 |
| MagesticAI | ❌ 宿主机 subprocess | ✅ | ❌ **0 处归属校验** | 5 |
| sandboxed.sh | ⚠️ Linux 真隔离 / macOS 默认退化 | ❌ `.env.example` DEV_MODE=true | 单租户 | 5 |
| paseo | ❌ | ⚠️ relay 路径绕过密码 | 单用户 | 4 |
| agent-orchestrator | ❌ 宿主用户权限 | ❌ 无 salt SHA-256 + 8 字符共享密码 | ❌ | 3 |
| vibe-kanban | ❌ 命名误导，0 行 Docker | ❌ 本地 server 零鉴权 | ❌ | 3 |
| codeforge-agent | ❌ **docker.sock = 宿主 root** | ❌ 明文口令 cookie + 默认 changeme | ❌ | 1 |

## Conclusion

- **容器隔离**：10 个样本零真实现（单台机器 + Docker 约束下）。唯一现成实现 `optio` 绑定 K8s；`sandboxed.sh` 的 nspawn 在 macOS 上默认退化到宿主执行且其 read-only git guard 同时失效。因此**隔离层必须自建**，起点是 A4 的加固基线；`MagesticAI` 的 SIGTERM→5s→SIGKILL 生命周期、`vibe-kanban` 的进程组 kill、`omnigent` 的 sentinel 僵尸回收、`codeg` 的 `boot_reconcile_interrupted` 崩溃恢复可直接借鉴。
- **鉴权**：分层做法的最佳实践集中在 `omnigent`（恒定时间 + CSWSH + fail-closed 策略门禁）与 `movo`（loopback 强制 + 内部网络 + ≥32 字符 token，把 DSH 的裸暴露面结构性消除）。其余 8 个样本的共同缺陷是「默认不安全」而非「实现薄弱」——多数项目的鉴权代码本身可用，但默认值、示例配置或旁路（relay / 静态 IP / dev 模式）使其开箱即处于无鉴权或全权限状态。
- **被否决的方案**：
  - 以 `codeforge-agent` 为底座 —— 931 行薄壳，9 项硬需求中 3 项零实现，`docker.sock` 挂载等同宿主 root，README 5 处夸大（声称 20+ skills 实为 10、GitHub Native 无任何 git 代码、SWE-bench 成绩直接挪用 OpenHands）
  - 以 `vibe-kanban` 为底座 —— `crates/server` 无鉴权 + `Dockerfile:144` `HOST=0.0.0.0`；`container_ref` 命名造成实质安全误判；前端 296k 行仅 1 个测试文件
  - 以 `kandev` 为底座而不改默认值 —— 鉴权默认关闭且注入 RoleAdmin；`--new-from-rev`（`backend/.golangci.yml:82-83`）使 11029 行上帝文件永久豁免 lint
  - 直接采用 `sandboxed.sh` 的 nspawn 路线于 macOS —— 类型 3 静默退化，且防护同步失效
- **后续工作**：本报告只覆盖隔离与鉴权两轴。落地前需拆 `ai-dev/plans/`（容器编排层 + 鉴权网关两个独立 slice），并与 git 交付链路、任务状态机设计（`codeg/work_task.rs` 10 态模型为当前最佳参考）合并成一份 design 文档。

## Open Questions

- [ ] macOS Docker Desktop 下 `--network=none` 与内部网络对 agent 拉取依赖（npm/pip/maven）的冲突如何解耦？是否需要「构建期联网 / 运行期断网」两阶段镜像？
- [ ] 隔离强度目标定在哪一档？跑自家仓库（可信）vs 跑外部 fork PR（不可信）应采用不同隔离等级，判定规则如何落进任务模型？
- [ ] 多用户是否为一等需求？若否，可省掉 workspace/RBAC 层（`optio` 那一层），换取更小的攻击面
- [ ] 内部网络（DOCKER-NETWORK + `--internal`）能否替代 `--network=none`，以保留必要出站（git push、模型 API）？
- [ ] `MagesticAI` 那种「git 层零归属校验」是否也是 nop 平台自身的风险模式？需排查 `nop-biz` / `nop-api-core` 的资源入口是否有同类归属校验缺口
- [ ] relay / 移动端远程访问是否为必需？若需要，Paseo 的静态长期密钥对 + 无重放窗口（`daemon-keypair.ts`）不可照抄，需重新设计密钥轮换与重放防护

## References

### 本地样本（`~/ai/agent-platforms/`、`~/ai/movo`、`~/ai/dsh-src/`）

- 隔离矩阵证据：各仓库 `docker-compose.yml` / `Dockerfile` / `internal/agent/docker/client.go` / `src/nspawn.rs` / `apps/api/src/services/repo-pool-service.ts`
- 鉴权矩阵证据：各仓库 auth 模块路径见 B1 表格「证据」列
- DSH 立场：`~/ai/dsh-src/deepseek-harness/docs/subsystems/web-server.md`、`docs/subsystems/sandbox.md`、`packages/client/connection/README.md:9`
- MOVO 实现：`~/ai/movo/services/chat-api/dsh/runtime-host/src/host-auth.mjs`、`~/ai/movo/docker-compose.yml:333-335`

### 相关既有文档

- `ai-dev/analysis/00-analysis-writing-guide.md`
- 综合代码质量审计（本轮同期产出，尚未落盘；如需应先写为 `ai-dev/analysis/2026-10/2026-10-03-agent-platform-code-quality-audit.md`）

### 外部参考

- Docker 容器安全加固：<https://docs.docker.com/engine/security/>
- NIST SP 800-63B（数字身份指南，密码存储章节）：<https://pages.nist.gov/800-63-3/sp800-63b.html>
- CVE-2018-17456（git 子模块远程代码执行）：<https://nvd.nist.gov/vuln/detail/CVE-2018-17456>
- CVE-2018-11235 / CVE-2022-39253（git 子模块与本地克隆劫持）：<https://nvd.nist.gov/vuln/detail/CVE-2022-39253>