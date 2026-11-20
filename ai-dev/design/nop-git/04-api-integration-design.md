# nop-git API 集成契约

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. 平台内 Java 集成的唯一入口是 `IGitRepositoryService`（`io.nop.git.api` 包）：语义化方法门面 + 策略前置裁决 + 全自有类型签名，**不向调用方暴露任何 JGit 类型**。
2. 依赖注入遵循 Nop IoC 约定：`IGitPolicyEngine` 与 `IGitRepositoryService` 为 beans.xml 显式声明的 bean；`@Inject` 字段用 `protected`/包可见（NopIoC 不支持 private 字段注入）。
3. HTTP Git 服务层（`nop-git-server`）是可选扩展：`GitServlet` 暴露智能协议端点 + `PreReceiveHook` 服务端分支保护，仅在客户需要标准 Git 客户端经 HTTP 访问仓库时部署。

## 二、API 面

```java
public interface IGitRepositoryService extends AutoCloseable {
    // 生命周期
    static IGitRepositoryService open(File workDir, IGitPolicyEngine policy);
    static IGitRepositoryService init(File workDir, IGitPolicyEngine policy);

    // 查询（只读，策略引擎仍裁决——observe/enforce 语义一致）
    NopGitStatus status();
    List<NopGitDiffEntry> diff(DiffOptions options);   // --cached / HEAD / --stat 语义
    List<NopGitCommit> log(LogOptions options);        // maxCount、oneline 所需字段
    String currentBranch();                             // 空仓库返回 null
    String resolveRevision(String rev);                 // rev-parse 语义，失败返回 null
    List<NopGitRef> branchList();

    // 变更
    void add(String filepattern);
    NopGitCommit commit(String message, String authorName, String authorEmail);
    void checkout(String branch, boolean create);
    void reset(ResetMode mode, String rev);             // mode 枚举与 git --soft/--mixed/--hard 对齐
    void clean(CleanOptions options);

    // 远程（P3，需网络）
    void push(PushOptions options);
    FetchResult fetch(FetchOptions options);
}
```

契约要点：

- **每个公开方法内部先经 `IGitPolicyEngine` 裁决**（architecture baseline §三硬约束 1）；deny 抛 `NopException`（`nop-git` ErrorCode），消息含命中规则原因。
- 返回类型 `NopGitStatus` / `NopGitCommit` / `NopGitDiffEntry` / `NopGitRef` 为自有 record/POJO，字段覆盖 CLI 格式化所需全部信息（CLI 的 `GitOutputFormatter` 消费同一批类型——CLI 与 API 共享一条数据路径，格式化只在出口分叉）。
- `AutoCloseable`：实例持有底层 Repository，必须关闭；每次 open 一个实例，不共享。
- 身份（user.name/email）缺省从 git config 链读取，未配置时 commit 报原生风格错误。

## 三、Nop IoC 集成

- `nop-git-core` 提供 `_vfs/nop/git/beans` 下的 beans.xml：`IGitPolicyEngine`（单例，启动加载三层配置）、`IGitRepositoryService` 工厂。所有 bean 显式声明（平台无注解扫描）。
- 配置值注入用 `@InjectValue`；策略文件路径、伪装版本号等可被平台 config 覆盖。
- AI Agent 工具层（nop-ai-shell 等）推荐将 git 类工具的后端从拼 shell 切换为注入 `IGitRepositoryService`，自动获得策略保护与审计。

## 四、HTTP Git 服务层（可选）

- 技术选型：JGit `GitServlet` + `RepositoryResolver<HttpServletRequest>`，解析时 `incrementOpen()` 管理生命周期；浏览器直接访问返回 404 是正常行为（只处理智能协议路径）。
- **服务端必须独立部署推送保护**：CLI/API 层策略引擎保护的是本地 AI 操作；HTTP 端点的完整性由 `PreReceiveHook` 保护——对 push 的 ref 更新做保护分支直推/强推/删除校验，两者互补不互替。
- 部署形态：独立可执行（内嵌 HTTP 容器）或挂入已有 Nop 服务；仓库注册表由配置声明仓库根目录映射。

## 五、拒绝了什么

- **API 签名直接暴露 JGit 的 `Git`/`RevCommit`/`Status`**：把 JGit 变成公共 API 就锁死了底层实现替换权，也违反自完备裁定的收敛原则。
- **API 层不做策略裁决、只信 CLI 层**：Java 调用方注入即绕过，见 architecture baseline §六。
- **HTTP 层复用 CLI 层策略引擎替代 PreReceiveHook**：网络对端是任意 git 客户端，ref 更新发生在服务端接收路径上，只有 PreReceiveHook 处在该路径。
