# nop-git Worktree 支持设计

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. linked worktree 的**创建（add）与删除（remove）是一等能力**：`IGitRepositoryService` 提供 `listWorktrees` / `addWorktree` / `removeWorktree` API，CLI 提供 `git worktree add / list / remove` 子命令（P2 级，非可选扩展）。
2. **JGit 7.x 只支持读取 linked worktree，不支持 add/remove/move**（上游 issue #264 仍 open）。因此 add/remove 在 `JGitRepositoryService` 适配层内**自建实现**：按 git 官方 linked-worktree 管理元数据格式直接读写 `$GIT_COMMON_DIR/worktrees/` 与工作目录的 `.git` 指针文件；list 复用 JGit 的读支持并与自建元数据合并。
3. 元数据写入格式必须与真实 git 逐字节兼容（真实 git 客户端能识别 nop-git 创建的 worktree，反之亦然）；用"真实 git 创建 → nop-git 读取"与"nop-git 创建 → 真实 git 读取"双向对照测试钉死。

## 二、linked worktree 元数据契约（git 官方格式）

主仓库 common dir 下每个 worktree 一个管理目录：

```
<common-dir>/worktrees/<id>/     # id = 目录名 sanitized（冲突时 -1、-2 后缀）
├── gitdir      # 单行：反向指针，指向工作目录的 .git 文件绝对路径
├── commondir   # 单行：相对/绝对路径回指 common dir（通常 "../.."）
├── HEAD        # 单行：ref 名或 detached commit SHA
├── index       # 该 worktree 独立的暂存区
├── ORIG_HEAD   # 可选
└── locked      # 可选：存在即锁定（remove 拒绝，需 --force）
```

worktree 工作目录侧：

```
<worktree-path>/.git             # 纯文件（非目录），单行：gitdir: <admin-dir绝对路径>
```

add 语义：解析目标 commit/branch（`-b` 先建分支；缺省检出 HEAD 新分支或在 detached 模式直接检出）→ 写 admin 目录（HEAD/index 用空 index 初始化）→ 创建工作目录 + `.git` 指针文件 → 按 reflog/checkout 语义物化工作区文件（复用 JGit checkout 到指定目录的能力）。

remove 语义：校验目标在 `worktrees/` 注册表内 → 拒绝删除主 worktree 与 locked（除非 force）→ 删除工作目录内容 → 删除 admin 目录 → 清理关联 branch（`--delete-branch` 时）。prune（清理失效指针）随 remove 附带实现。

## 三、关键约束

- **逐字节兼容双向契约**（见结论 3）；`git worktree list` 的 porcelain 输出格式对齐原生。
- **多 worktree 并发写安全**：同一仓库的多个 worktree 各有独立 index/HEAD，但共享 refs——写操作（add/branch/commit 落在共享 refs 上）由调用方串行化，与 Repository 生命周期约束一致。
- **策略引擎覆盖**：`worktree add/remove` 作为操作纳入 `operations` 配置维度；`remove` 默认进 `review`（删除工作目录不可逆），`add` 默认 allow。
- 自建实现收敛在适配层（`JGitRepositoryService` 及其内部 helper），不向外暴露元数据文件格式细节；架构支柱测试不变（包外仍禁 import JGit，且 worktree 自建代码不额外引入 JGit 以外依赖）。

## 四、拒绝了什么

- **等 JGit 上游实现 #264 再做 worktree**：客户场景（AI 并行多任务在多 worktree 工作）是本模块核心价值之一，上游无时间表；自建元数据读写面窄、格式稳定（多年未变），风险可控。
- **把 worktree 元数据格式抽象为可替换内部存储**：格式即兼容性契约，没有"替换"语义；抽象层是纯开销。
