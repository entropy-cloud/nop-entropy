# nop-ai-code-analyzer 模块职责边界（P3-MA1-014 裁定）

**日期**：2026-08-01
**范围**：`nop-ai/nop-ai-skills/nop-ai-code-analyzer`（5 个包：code / git / maven / project / stats）
**状态**：active（裁定已落地）
**相关裁定**：P3-MA1-014（MA1.3 审计，第九批承接）、`ai-dev/plans/2026-08-01-0206-1-arm-p2-skills-code-analyzer-structure.md` Phase 3

---

## 一、设计结论

**裁定 = 保持现状 + 边界文档化（不拆模块）**。

1. nop-ai-code-analyzer 保持单一模块，5 个包继续共处，不做 `nop-ai-maven-analyzer` 拆分。
2. **maven 包是模块内部子域**：10 个文件，无任何模块外消费者；外部代码禁止 import `io.nop.ai.code_analyzer.maven.*`（未来若有真实需求，触发正式模块拆分，见 §四 迁移条件）。
3. **git 包是跨模块公共面**：`GitIgnoreFile` 被 `nop-runner/nop-cli-core`（经 nop-ai-coder 传递依赖）直接使用，属公开契约，变更按公共 API 对待。
4. **nop-shell 依赖保留 compile scope**：`MavenProject.java` 唯一使用（`ShellRunner`），保持声明为直接依赖。
5. **stats 包是独立工具子域**：`FileLanguageStats` 系列（拆分后 6 文件）无生产消费者，能力保留 + 直接调用测试（见 P2-MA1-005 裁定）。

## 二、背景与动机（live 使用面）

| 包 | 文件数 | 模块内消费者 | 模块外消费者 |
|----|--------|-------------|-------------|
| `code/` | 7 | parser/generator/splitter 内部 | nop-ai-coder（`JavaFileSplitter`，task XML 运行时 XLang 实例化） |
| `git/` | 1 | `project/GitProject` | **nop-cli-core `CliFileCommand`**（经 nop-ai-coder 传递依赖） |
| `maven/` | 10 | `code/`（JavaParserBuilder / JarResolverCollection / JavaCodeFileInfoGenerator）、`maven/` 内部、测试 | 无 |
| `project/` | 1 | `maven/MavenProject` | 无 |
| `stats/` | 6 | 无生产消费者（直接调用测试为消费者） | 无 |

- nop-shell 依赖仅 maven 包的 MavenProject.java 中 `ShellRunner` 使用（全模块 grep 唯一命中）。
- 依赖链：`code → maven → project → git`；`stats` 独立。外部消费面只触及 `code` 与 `git`。

## 三、为什么拒绝拆分（P3-MA1-014 裁定理由）

**拒绝"拆分 nop-ai-maven-analyzer + nop-ai-code-analyzer-core"（audit 原建议）**：

1. **环依赖**：`code → maven`（JavaParserBuilder 需 `MavenModule` 做 jar 解析、JarResolverCollection 需 4 个 maven 类型）而 `maven → project → git`。若 maven 拆出，`nop-ai-maven-analyzer → core`（project/git）与 `core → nop-ai-maven-analyzer`（MavenModule 等）形成双向环，Maven 无法构建。
2. **拆分会自行溶解**：破环必须把 7/10 个 maven 文件（MavenModule/MavenDependency/MavenDependencyNode/MavenRepository 等）迁移进 core，剩余"maven-analyzer"仅约 3 个文件（MavenProject/MavenModuleStructure/MavenPomParser），其仍依赖 project/git——模块拆分失去意义。
3. **风险收益比**：P3 严重度 + 审计信心"中"；拆分 = 新增模块 + nop-ai-coder/nop-cli-core pom 链调整 + 跨模块 API 面扰动，收益仅为隔离一个单文件使用的 nop-shell 传递依赖（该依赖为小体积平台模块，两真实消费者均已接受）。
4. **git 包归属**：真正的跨模块公共面是 git（nop-cli-core 契约），其天然属于 core 侧；maven 包无外部消费者，无拆分压力。

**拒绝"nop-shell 改 optional/provided scope"**：`MavenProject` 运行时需 `ShellRunner`，改为 optional 后使用方必须显式声明才能用 MavenProject——改变模块契约且引入"编译过、运行时炸"风险，与模块现状（唯一使用点已显式声明）相比无收益。

## 四、使用约束

- **maven 包 = 内部子域**：新代码（含本模块外）禁止 import `io.nop.ai.code_analyzer.maven.*`；已有内部消费者（code 包）保持不变。
- **git 包 = 公共面**：`GitIgnoreFile` 的任何签名/语义变更须按公共 API 流程（owner doc + 回归测试），nop-cli-core `CliFileCommand` 为持续消费者。
- **stats 包 = 独立工具面**：`FileLanguageStats` 为无消费者保留能力的裁定对象，新增消费点前保持直接调用测试为消费者证据。
- **迁移触发条件**（未来出现任一条件时正式拆分）：①第二个模块外消费者需要 maven 包；②nop-shell 使用点超过 1 个文件；③code 包对 maven 类型的依赖收敛（如 MavenModule 提取为接口后下沉）。

## 五、与已有设计的关系

- 上游：`P3-MA1-014` 审计记录（`ai-dev/audits/2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`）。
- 相关：P2-MA1-005 裁定（stats 包拆分）见 `ai-dev/audits/arm-index.md` §P2 追踪·第九批；P3-MA1-015 裁定（deepwiki 依赖）同批。
- 追踪：`ai-dev/audits/arm-index.md` §P2 追踪（第九批追踪小节）P3-MA1-014 行。
