# 多 Agent 并发构建治理：mvnq 排队包装器与 per-worktree repository

> Status: resolved
> Date: 2026-09-21
> Scope: 开发工具 `ai-dev/tools/mvnq`（新增）与本仓库多 worktree 构建流程；不涉及 nop-entropy 产品代码与 `docs-for-ai/` 平台文档
> Conclusion: 多 AI agent 同仓并发构建冲突由 `ai-dev/tools/mvnq` 包装器解决——同一 worktree 内 Maven 构建经 `.mvn/build.lock`（`FileChannel` 锁，持有者崩溃自动释放）串行排队；每个 worktree 注入独立 `<root>/.m2-repo` 本地仓库实现本地 install 构件隔离，第三方依赖经 APFS clonefile 从全局仓库块级共享播种（3.8GB 仓库 17 秒、近似零增量磁盘）。全部行为已在 macOS + Maven wrapper 4.0.0-rc-5 上实测通过。

## Context

- 决策点：多个 AI agent 在同一仓库（多个 git worktree）上工作时并发执行 `mvn`，互相践踏 `target/`、codegen 产物与本地仓库；且共享 `~/.m2/repository` 时一个 worktree install 的 SNAPSHOT 会被另一个 worktree 解析到（错版本污染）。要求：
  1. 同一项目上的 Maven 指令排队串行执行；
  2. 共享依赖用全局 repository 省空间，各 worktree 自己编译的构件进自己的 repository。
- 前置调研：[2026-09-20c-mvnd-daemon-performance-research.md](2026-09-20c-mvnd-daemon-performance-research.md)——已证实 mvnd/mvn 无任何跨调用协调（无排队、无最大 daemon 数），普通 `mvn` 更无。

## 调研结果（方案依据）

- **排队必须在外层做**。mvnd 客户端 `DaemonConnector.connect()` 只连"空闲且兼容"的 daemon，否则直接新建（并发 N 命令 = N daemon 并行）；daemon 端 accept 循环一次只服务一个客户端但客户端从不排队。源码锚点：`~/sources/mvnd/client/src/main/java/org/mvndaemon/mvnd/client/DaemonConnector.java`（connect 方法）、`~/sources/mvnd/daemon/src/main/java/org/mvndaemon/mvnd/daemon/Server.java`（accept/handler.join）。先例：Bazel 对同一 workspace 的命令默认持阻塞锁并打印 "Another command is being run"，即本方案模型。
- **Maven 一次构建只有一个 local repository**，"下载进全局、install 进本地"没有官方单开关；把全局仓库声明为 `file://` remote 会触发复制而非共享（不省空间）——否决。
- **但 per-worktree 仓库路径可以做得很轻**：Maven 4 的 `-D` 参数（含 `.mvn/maven.config` 传入的）会用 `${session.rootDirectory}`/`${session.topDirectory}` 早期路径占位符插值（`~/sources/maven/impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/BaseParser.java` 的 `populateUserProperties`/`extraInterpolationSource`；特性自 2023 年 MNG-7038/MNG-6303 落地，早于本仓库 wrapper 的 4.0.0-rc-5）。即提交一行 `-Dmaven.repo.local=${session.rootDirectory}/.m2-repo` 到 `.mvn/maven.config` 即可实现仓库隔离。
- **本方案选择"包装器注入"而非提交 maven.config**，理由：a) 不强制不经过 mvnq 的普通构建（人和主 checkout 的裸 `./mvnw` 仍走全局仓库，避免"同一 checkout 两套仓库"的混乱）；b) 包装器可在注入前做 APFS 克隆播种，省空间一步完成；c) `--shared-repo` 提供单次逃逸。两者可叠加（值相同互不冲突）。
- **空间共享靠本地仓库的文件不可变性 + APFS clonefile（CoW）**：release 构件一个坐标一个文件永不改写、SNAPSHOT 只新增带时间戳文件，因此从全局仓库克隆出的块会持续共享，仅 metadata 小文件在 install 后分叉。Linux 用 `cp -al` 硬链接等价（注意点见 Open Questions）。
- **根治路线存在但暂不做**：Maven 4 的 `LocalRepositoryManager` 是 `@Named` Sisu SPI（`~/sources/maven/impl/maven-impl/src/main/java/org/maven/impl/DefaultLocalRepositoryManager.java`），可覆盖实现"读穿透全局、写入本地"的链式管理器；工程量大，留作后续。

## 落地方案：`ai-dev/tools/mvnq`

组成：`ai-dev/tools/mvnq`（bash 入口，macOS 自带 bash 3.2 兼容）+ `ai-dev/tools/MvnqLock.java`（JEP 330 单文件源码启动，无需编译步骤，JDK 11+ 即可）。

### 行为语义

| 维度 | 默认 | 可选项 |
|---|---|---|
| 排队 | 同一 worktree 经 `.mvn/build.lock` 互斥，阻塞等待，超时默认 3600s（exit 75），等待时打印持有者 pid/cmd | `--no-lock`、`--lock-timeout=<秒>`、`MVNQ_LOCK_TIMEOUT` |
| 本地仓库 | 注入 `-Dmaven.repo.local=<root>/.m2-repo`（用户显式传过 `-Dmaven.repo.local` 则尊重不覆盖） | `--shared-repo`/`MVNQ_REPO=shared` 用全局；`--repo=<路径>` 自定义 |
| 播种 | `<root>/.m2-repo` 为空时从 `~/.m2/repository` 克隆（macOS `cp -Rc` APFS clonefile / Linux `cp -al` 硬链接） | `--no-seed`/`MVNQ_NO_SEED=1`、`MVNQ_GLOBAL_REPO` |
| 旁路 | — | `MVNQ_DISABLE=1` 完全透传 |
| 状态 | `mvnq --status` 显示当前 worktree 锁占用（idle/busy + pid/cmd） | — |

底层 Maven 命令：`MVNQ_MAVEN` > `<root>/mvnw` > PATH 上的 `mvn`。信号与退出码透传（bash `exec`）。

### 锁设计要点

- 锁载体是 `MvnqLock` 进程对锁文件的 `FileChannel.lock`：持有者无论正常退出还是 `kill -9`，OS 立即释放锁——不存在残留死锁（mkdir/标记文件方案被否决的原因）。
- 锁文件**只写不删**：删除锁文件会让排队者持有的旧 inode 与新排队者创建的新文件脱钩，导致双持有。
- 锁文件内记录持有者信息（pid、启动时间、命令行），供 `--status` 与排队提示读取。
- 竞争窗口说明：两个首次构建同时播种 `.m2-repo` 时 `cp` 可能部分失败——无害，缺失构件由 Maven 正常下载补齐。

### 已验证（macOS arm64 + JDK 26 + Maven 4.0.0-rc-5 wrapper）

- 并发排队：持有者持锁期间，第二个构建打印 `queued: waiting ... pid=... | cmd=...` 并等待，持有结束后自动执行（实测等待 ~4.5s）。
- 崩溃释放：`kill -9` 持有者后，新构建 0.3s 内获得锁。
- 超时：`--lock-timeout=2` 对 5s 持有者恰好在 2s 以 exit 75 退出并打印持有者。
- 播种：3.8GB 全局仓库 APFS clonefile 17 秒完成，`du` 外观 3.8GB 而磁盘增量近似为块共享。
- 仓库注入/尊重显式参数/`--shared-repo`/`--no-lock`/`--no-seed`/`--status` 全部按语义工作；`./mvnw --version` 真实链路冒烟通过。
- `.gitignore` 已加 `/.m2-repo/` 与 `/.mvn/build.lock`。

## 使用方法

```bash
# 常规构建（排队 + worktree 仓库 + 自动播种）
ai-dev/tools/mvnq clean install -T 1C
ai-dev/tools/mvnq test -pl nop-xlang -am

# 查看当前 worktree 排队状态
ai-dev/tools/mvnq --status

# 临时用全局仓库（不注入 .m2-repo）
ai-dev/tools/mvnq --shared-repo dependency:tree

# 排队超时 30 分钟
ai-dev/tools/mvnq --lock-timeout=1800 clean install
```

与 mvnd 的关系：`maven.repo.local` 在 mvnd 中是 DISCRIMINATING 选项（`~/sources/mvnd/common/src/main/java/org/mvndaemon/mvnd/common/Environment.java:88`），不同 worktree 的仓库路径自动对应不同 daemon 池，`MVNQ_MAVEN=mvnd ai-dev/tools/mvnq ...` 即可组合使用，无冲突。

## 被否决的备选方案

- **在 mvnd 客户端内实现排队**（`DefaultClient` 在 `connector.connect()` 前抢锁，约 50–80 行）：可行、体验最优且可上游化，但需长期维护 fork；先用包装器验证语义，之后再议上游 PR（见 Open Questions）。
- **仅当 goals 含 install 时注入 worktree 仓库**（混合模式）：普通 `test` 会从共享仓库解析到其他 worktree 遗留的旧 SNAPSHOT，冲突更隐蔽——否决。纯隔离（所有构建都用 worktree 仓库）语义清晰：本 worktree 构建的构件只会被本 worktree 解析。
- **共享仓库 + 全面禁用 install（reactor-only）**：最省空间，但部分流程确需 install；且 `-pl X` 不带 `-am` 时会静默解析共享仓库的陈旧构件而非快速失败。
- **mkdir/标记文件锁**：进程死亡后残留，需人工清理——否决。
- **全局仓库挂 `file://` remote 供 worktree 读取**：resolver 会把构件复制进 worktree 本地仓库，不省空间——否决。

## Open Questions

- [ ] Linux `cp -al` 硬链接模式下，resolver/installer 对 `maven-metadata-*.xml`、`_remote.repositories` 的改写若走"原地截断"而非"临时文件+改名"，会波及全局仓库的同一 inode；macOS clonefile 无此问题。需在 Linux 上观察后决定是否默认禁用硬链接播种。
- [ ] 是否将 AGENTS.md 的构建命令统一路由到 `mvnq`（影响所有 agent 的默认行为，待用户拍板后再改）。
- [ ] 是否向 mvnd 上游提"构建排队"PR（挂载点：`DefaultClient` 在 `connector.connect()` 之前抢 per-project `FileChannel` 锁）。

## References

- 工具：`ai-dev/tools/mvnq`、`ai-dev/tools/MvnqLock.java`
- 前置调研：[2026-09-20c-mvnd-daemon-performance-research.md](2026-09-20c-mvnd-daemon-performance-research.md)
- 源码锚点：
  - `~/sources/mvnd/client/src/main/java/org/mvndaemon/mvnd/client/DaemonConnector.java`（无排队证据）
  - `~/sources/mvnd/common/src/main/java/org/mvndaemon/mvnd/common/Environment.java`（`MAVEN_REPO_LOCAL` DISCRIMINATING）
  - `~/sources/maven/impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/BaseParser.java`（`${session.rootDirectory}` 插值）
  - `~/sources/maven/impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/mvn/MavenParser.java`（`.mvn/maven.config` 从 rootDirectory 读取）
  - `~/sources/maven/impl/maven-impl/src/main/java/org/apache/maven/impl/DefaultLocalRepositoryManager.java`（LRM SPI，路线 C 伏笔）
