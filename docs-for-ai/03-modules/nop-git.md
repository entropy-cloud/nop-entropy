# nop-git 模块（Git 工具面）

> 本页为 nop-git 的使用知识 owner doc；设计裁定文档（vision/架构基线/CLI 兼容/策略引擎/worktree）位于 AI 开发记录区 design 目录（规范性）。

## 一、模块拓扑

```
nop-tools/nop-git/
├── nop-git-parent   # 聚合 pom（release=21 运行基线；注意 artifact 名为 nop-git-parent，与 legacy nop-utils/nop-git 的 GAV 区分）
├── nop-git-bom      # JGit 7.7.1 / snakeyaml / jetty 版本唯一声明点
├── nop-git-core     # API 门面 + JGit 适配层 + 策略引擎 + 审计（零仓库内部依赖 → JDK 21 基线真实可达）
├── nop-git-cli      # git 兼容 CLI（可 PATH 覆盖部署）
├── nop-git-server   # 可选 HTTP Git 服务层（Jetty ee11 嵌入）
└── nop-git-ioc      # 平台 IoC 集成面（平台 Java 基线，仅此模块依赖 nop-core/nop-ioc）
```

依赖方向：cli/server/ioc → core → JGit；单向，禁止反向。架构支柱：`io.nop.git` 包以外禁止 import `org.eclipse.jgit`（`ArchitecturePillarTest` 钉死）。

## 二、CLI 部署（AI 工具当系统 Git 用）

1. `mvn -pl nop-tools/nop-git/nop-git-cli package` 产出 `nop-git-cli.jar`（shade 自包含）。
2. 把 `nop-git-cli/bin/git` 放入 PATH 优先目录（同目录或 `../target/` 自动定位 jar；`NOP_GIT_HOME`/`NOP_GIT_JAVA` 可覆盖）。
3. 行为契约（design 02）：无 ANSI 色；退出码 0/1/128/129；未实现子命令 `git: 'xxx' is not a nop-git command.`；`--version` 输出配置的伪装版本（默认 2.45.0，须为真实存在的 git 版本）。
4. 已支持子命令：P0/P1（status/diff/add/commit/log/branch/rev-parse/config）+ P2（checkout/switch/reset/clean/restore/rm/mv/stash）+ P3（init -b/remote/push/fetch/pull/clone/merge/rebase/cherry-pick/worktree）。P0/P1 输出与原生 git 逐字节对齐（live-parity 46 例 + golden 41 例钉死）。

## 三、Java API（IGitRepositoryService）

```java
try (IGitRepositoryService svc = IGitRepositoryService.open(workDir, policyEngine)) {
    svc.add(".");
    NopGitCommit c = svc.commit("msg", null, null); // 身份走 git config 链
}
```

- 签名全自有类型（`NopGitStatus/Commit/DiffEntry/Ref/Worktree`），JGit 不外泄。
- `open/init` 均有带 `IGitPolicyEngine` 的重载；每个公开方法先裁决后执行（design 03 §六双拦截点）。
- worktree 一等支持：`listWorktrees/addWorktree/removeWorktree/pruneWorktrees`——JGit 只读，add/remove 按官方元数据自建（与真实 git 双向逐字节兼容，见 design 05）。
- deny 抛 `NopGitException("policy denied: ...")`；CLI 层对应 `fatal: policy denied:` + exit 1。

## 四、策略配置

- 位置：`<repo>/.nop-git/policy.yaml` → `~/.nop-git/policy.yaml` → 内置默认；两层都缺省时引擎**非激活**（零拦截零审计）。
- 语义（design 03）：五维（操作/选项/分支/路径/远程）+ scopes；deny 优先；`observe` 只记录（审计带 hypotheticalEffect）/`enforce` 真实拦截；review 三通道（`NOP_GIT_REVIEW_APPROVED=1`/交互确认/超时降级 deny，prompt 走 stderr）。
- 审计：`.nop-git/audit.log`（JSON Lines，单行含 outcome）。
- 选项归一化在评估前（`-f`→`--force` 等，`-d` 按命令定表）。

## 五、HTTP 服务（可选）

```java
NopGitHttpServer server = new NopGitHttpServer(GitServerConfig.of(0, repoRoot));
int port = server.start();
// http://127.0.0.1:<port>/<name>.git 智能协议端点；push 已启用（ReceivePackFactory）
// 服务端分支保护：ProtectedBranchPreReceiveHook（main/master/release/* 直推/force/删除拒绝）
```

浏览器 GET 返回 404 属正常（只服务智能协议）。

## 六、IoC 集成

`nop-git-ioc` 的 beans 资源文件（git-ioc.beans.xml）暴露 `gitIocFactory`（配置键 `nop.git.default-branch` / `nop.git.policy-path`）；平台服务经工厂获得策略守卫的门面实例。仅此模块在平台 Java 基线（25）；裸 core/cli 保持 JDK 21。

## 七、测试体系（改动时的质量门）

- `CliParityTest`：46 例与真实 git 逐字节对拍（dual-fixture，确定性 pinned ident）。
- `GoldenFileTest`：41 例 golden（无 git 环境兜底）。
- `Wave4CliTest`/`CliPolicyTest`/`AiToolSmokeTest`/`GitPolicyEngineTest`/`NopGitHttpServerIntegrationTest`。
- 构建验证走 nop-ib（`ib test -pl :nop-git-core -pl :nop-git-cli -pl :nop-git-server -pl :nop-git-ioc`）。

## 八、与 legacy nop-utils/nop-git 的关系

`nop-utils/nop-git`（`IGitRepository`，单分支模型文件管理）原地保留，与本模块无依赖关系；处置裁定见 AI 开发分析区 2026-11 的 nop-git-ai-shell 集成评估（时效性文档，结论可能被推翻）。
