# nop-git 架构支柱与策略绕过专项审计（plan 2581 / item 31）

Date: 2026-11-21（独立 fresh-session 审计员）
Status: closed
Scope: nop-tools/nop-git（core/cli/server/ioc）、docs-for-ai/03-modules/nop-git.md、ai-dev/design/nop-git/{00,03}-*.md
Conclusion: **PASS（无 Blocker）**——1 Major（支柱测试扫描面 + vision 收敛声明与 server 实况不一致）+ 3 Minor（测试未钉死项）。均不构成可利用的策略绕过。

## 摘要

双拦截点（CLI guard + 门面 guarded）未发现可绕过实例；策略归一化表（design 03 §三）在 `GitRequests.normalizeFlag` 全量实现；架构支柱字面规则（`io.nop.git` 包外禁 import jgit）在 live 源码成立。缺口集中在**测试钉死面**与**设计文档声明与 server 模块实况的偏差**。

## 一、架构支柱复核

字面规则（design 00 §五 line 48：`io.nop.git` 包以外禁止 import `org.eclipse.jgit`）live 成立：

- nop-git-cli/nop-git-ioc 生产码：0 jgit 引用（grep 复核）。
- nop-git-server 生产码 import jgit（`NopGitHttpServer.java:5-10` 等），但包为 `io.nop.git.server`，字面在 `io.nop.git` 内 → 合规。
- 仓库其余模块仅 legacy `nop-utils/nop-git`（`io/nop/git/impl/`，包内）引用 jgit → 字面合规。
- 无反射/ServiceLoader/Class.forName/字符串加载 jgit 的绕过实例（`grep Class.forName|ServiceLoader|getResource` 于 nop-git 全部 src/main = 0 命中）。

发现：

- **[Major/P1] ArchitecturePillarTest 扫描面不含 server/ioc/legacy**：`ArchitecturePillarTest.java:31-33` 只扫 `nop-git-core` + `nop-git-cli`。其 javadoc 声称"非 core 模块不得触碰 JGit"，但 `nop-git-server` 生产码实际直用 JGit（`NopGitHttpServer.java:5-10,91-106`），测试既未覆盖也未被设计豁免记录。同时 vision 00 §五 line 47 声明"全部 JGit 调用收敛在 `JGitRepositoryService` 一个适配类内"，server 违反该收敛声明。修复方向：(a) design 00/01 显式记录 server 传输层豁免（协议端点必须触达 jgit transport），(b) ArchitecturePillarTest 把 server/ioc 纳入扫描（server 按 `io.nop.git` 包内白名单放行），(c) docs-for-ai/03-modules/nop-git.md §一补 server 豁免说明。
- [Minor/P3] 规则用字符串 `content.contains("org.eclipse.jgit")` 匹配（`ArchitecturePillarTest.java:54`），足以拦 import 与全限定名引用，无实质绕过面；仅提示无注释豁免机制。

## 二、策略归一化/绕过面清单（对照 design 03 §三 + GitRequests.java）

| 项 | 实现 | 测试钉死 |
|---|---|---|
| `-f`/`--force` | GitRequests.java:184 | GitPolicyEngineTest.java:115-118 ✓ |
| `--force-with-lease[=x]` 折叠 | :179-181 | :119-121 ✓ |
| `-fd` 组合展开 | :170-176 | :122-125（clean → --force --directories）✓ |
| `-D`（branch → --delete --force） | :185-189 | :139-141 ✓（非 branch 上下文透传未钉死，Minor） |
| `-d` 按命令查表 | :190-195 | 仅经 `-fd` 间接钉死；裸 `-d`（push→--delete / clean→--directories）无直接用例，Minor |
| 路径 `..` 归一化 | GitRequests.canonicalize :147-159（`base.resolve(path).normalize()` 杀遍历） | **无用例钉死**（`pathLevelWithTraversalBypass` 名含 traversal 但无 `../` 断言），Minor |
| basename glob（无斜杠模式） | DefaultGitPolicyEngine.java:383-390 | 无直接用例，Minor |
| scopes `gitdir:` 语义 | DefaultGitPolicyEngine.java:363-364 | GitPolicyEngineTest.java:186-197 ✓（命中 deny + 未命中 fall-through） |
| deny 优先 / review 累积先于 allow 白名单 | 评估序 design 03 §三 | GitPolicyEngineTest.java:126-129, layered strict merge :200-221 ✓ |

结论：归一化实现与 design 03 §三表逐项一致，**无实现级绕过缺口**；缺口均为测试钉死面（上表 Minor 4 项，建议一个后续小 plan 补 3-4 个用例）。

## 三、双拦截点复核

- **CLI guard**：`GitCompatCli.java:131-140` 在命令分发单 choke point 调 `policyGuard`（:47-76），deny → `fatal: policy denied:` + exit 1，observe 放行（:63），review 未批准 exit 1。CLI 生产码无任何直连 JGit 调用（grep 0 命中）→ 拼任意 shell 字符串均被解析评估，不可绕过。退出码 0/1/128/129 契约见 `GitCompatCli.java:29-32`。
- **门面 guarded**：`IGitRepositoryService.java:25-37` 单参 `open(File)` 经 `JGitRepositoryService.open` 自动 `DefaultGitPolicyEngine.load`（`JGitRepositoryService.java:93-95`）→ 即便无 policy 仓库引擎 inactive 属 design 03 钉死语义（非激活=零拦截零审计）。`init` 各重载同样自动装载（:114）。构造函数 `private`（:88）→ Java 调用方无法 `new` 绕过；所有公开方法经门面公共包装先裁决。`cloneRepository` 亦装载引擎（:441）。**未发现 API 级绕过漏洞**。
- 审计 outcome 单行回填（CLI 分发处/门面包装）有断言（GitPolicyEngineTest.java:315）。

## 四、发现清单

| ID | 严重度 | 发现 | 证据 | 建议 |
|---|---|---|---|---|
| GIT-PB-1 | Major(P1) | 支柱测试不扫 server/ioc/legacy；vision"单一适配类收敛 jgit"与 server 直用 jgit 不一致且无豁免记录 | ArchitecturePillarTest.java:31-33；NopGitHttpServer.java:5-10；00-vision.md:47 | design 00 补豁免裁定 + 测试扩扫描面 |
| GIT-PB-2 | Minor(P3) | `../` 路径遍历归一化无用例钉死 | GitRequests.java:147-159 | 加 traversal 用例 |
| GIT-PB-3 | Minor(P3) | 裸 `-d`（push/clean 上下文）与 `-D` 非 branch 透传无用例 | GitRequests.java:185-195 | 加 2 用例 |
| GIT-PB-4 | Minor(P3) | basename glob 分支无用例 | DefaultGitPolicyEngine.java:384-388 | 加 1 用例 |

## 五、结论

PASS——策略防火墙在 CLI 与 API 两条路径均无发现可实际利用的绕过；GIT-PB-1 属覆盖/文档一致性缺口（修复方向已给），GIT-PB-2..4 为测试补强项。建议作为 plan 2581 Non-Blocking Follow-ups 登记。
