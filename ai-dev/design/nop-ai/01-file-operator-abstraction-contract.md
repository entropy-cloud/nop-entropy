# nop-ai 文件操作抽象边界契约（IFileOperator vs IToolFileSystem）

**日期**：2026-07-31
**范围**：`nop-ai-core`（`io.nop.ai.core.file`）、`nop-ai-toolkit`（`io.nop.ai.toolkit.fs`）、消费者 `FileToolBizModel` / `DslToolImpl` / `FileDiffApplier` / `LocalFileOperator` / `CliFileCommand`
**状态**：active（裁定已落地）
**相关裁定**：P2-MA3-05（1834-3 批次，保留+记录）、P2-MA1-012（本计划，正式裁定 + `forRemoval=true` + 契约落盘）

---

## 一、设计结论

1. **双抽象并存是最终裁定，不是待清理债务**：`IFileOperator`（nop-ai-core，legacy 资源操作抽象）与 `IToolFileSystem`（nop-ai-toolkit，沙箱化工具文件系统）继续并存，各有明确消费契约。
2. **`IFileOperator` 标注 `@Deprecated(forRemoval = true)`**：语义为"未来 major 版本移除"，不承诺近期迁移；所有现有消费者保留现状。
3. **新代码默认选择 `IToolFileSystem`**：toolkit executor 及沙箱化文件访问一律使用 `IToolFileSystem`；`IFileOperator` 仅允许在既有 legacy 面（`FileTool` BizModel、DSL 工具、diff 应用）内继续使用。
4. **迁移前置条件**（任一迁移发生前必须满足，见 §三）：两抽象方法面先收敛，方可执行忠实迁移。

## 二、背景与动机

nop-ai 存在两个文件操作抽象，方法面与语义均无 1:1 对应：

- `IFileOperator`（nop-ai-core，legacy）：**base-dir 作用域的资源访问**。返回 `IResource`；`readFileContent` 支持 `offset/limit`（FileContent 携带 offset/hasMore 元数据）；提供 `findFilesByAntPath` / `findFilesByFilter` / `findFilesByRegex` / `findFilesByGlob` 多套查找面；`grep` / `grepFiles` / `globGrep` 返回嵌套 `GrepResult`；`mergeFile` 走 XDef 感知的 Delta 合并；`applyDiff` 走 `FileDiffApplier`。
- `IToolFileSystem`（nop-ai-toolkit）：**沙箱化工具文件访问**。`normalizePath` / `isPathAllowed` 显式路径权限检查；`readText` 按 maxChars 截断；`readLines` 按行区间 + maxLineLength；返回 `TextResult` / `LineResult` / `FileInfo` / `SearchMatch` DTO；`glob` / `grep` 带 depth/max 上限。

消费者面（live）：`FileToolBizModel`（18 个 public/protected BizModel 操作）、`DslToolImpl`（构造注入）、`FileDiffApplier`（构造注入）、`LocalFileOperator`（唯一实现）、`CliFileCommand`（nop-cli，构造注入）。其中 `FileToolBizModel` 是暴露于 GraphQL 的 `@BizModel("FileTool")` 活动契约。

早期简单 `@Deprecated` 标注无法表达"保留但标记移除方向"的完整裁定，且 P2-MA3-05 记录的"后续收敛"缺乏正式落盘——本契约文档即为收敛裁定的最终记录。

## 三、核心设计（边界契约）

### 职责边界

| 维度 | `IFileOperator` | `IToolFileSystem` |
|------|----------------|------------------|
| 定位 | legacy 核心资源操作（BizModel / DSL 工具面） | toolkit 沙箱化 FS（executor 工具面） |
| 作用域模型 | base-dir 规范化 + 越界拒绝 | 路径权限白名单检查 |
| 读取语义 | FileContent offset/limit + hasMore | maxChars 截断 / 行区间 + maxLineLength |
| 查找面 | AntPath / Filter / Regex / Glob 多套 | glob + grep（depth/max 上限） |
| 结果形态 | `IResource`、`FileContent`、嵌套 `GrepResult` | `TextResult` / `LineResult` / `FileInfo` / `SearchMatch` |
| 特殊操作 | `mergeFile`（XDef Delta 合并）、`applyDiff` | 无对应 |
| 消费者 | FileToolBizModel、DslToolImpl、FileDiffApplier、CliFileCommand | toolkit 全部文件类 executor |

### 迁移前置条件（未来迁移必须先满足）

1. 方法面收敛：为 `IToolFileSystem` 补齐或等价化 `IFileOperator` 独有的面（offset/limit 读取、AntPath/Regex finder、mergeFile、applyDiff、GrepResult 等价物），或先移除 `IFileOperator` 无消费者面（如 `findFileByRegex` / `findFileByName` / `findFilesByRegex`，当前无直接消费者）。
2. 语义对齐：明确 base-dir 作用域模型与沙箱权限模型的映射关系（`LocalFileOperator` 的 canonical-path 检查 vs `LocalToolFileSystem` 的 `isPathAllowed`）。
3. 消费者迁移：`FileToolBizModel`（18 个 BizModel 操作）与 `DslToolImpl` / `FileDiffApplier` / `CliFileCommand` 全部切换到新抽象，并保持 GraphQL 契约不变。
4. 完成上述步骤后删除 `IFileOperator` 及 `FileContent`/`FileContents` 等附属类型（属 future major 版本工作）。

### 使用约束

- `IFileOperator` 的 `@Deprecated(forRemoval=true)` 是方向信号：禁止新增 `IFileOperator` 新实现或新消费者。
- 禁止把两个抽象互相委托包装（避免第三套抽象）。
- 工具 XML / 新 executor 一律消费 `IToolFileSystem`（经 `IToolExecuteContext.getFileSystem()`）。

## 四、拒绝了什么

- **拒绝"全量迁移到 IToolFileSystem 并删除 IFileOperator"**：两抽象方法面差异大（§二），忠实迁移需先收敛抽象；`FileToolBizModel` 18 个操作 + DSL/diff/CLI 消费者同时切换回归风险高、零行为收益，且 `mergeFile` 的 XDef Delta 合并语义在 `IToolFileSystem` 无对应。该选项作为 future major 候选，前置条件见 §三。
- **拒绝"删除 IFileOperator 无消费者面"（本批次）**：`findFileByRegex` / `findFilesByRegex` / `findFileByName` / `findFilesByFilter` 虽无直接调用方，但删除属抽象面收窄，与"保持双抽象现状"的裁定最小落地原则冲突，留待迁移批次一并处理。
- **拒绝"第三个统一抽象"**：新增 `IFileSystem` 之类的统一接口只会增加第三套语义，无消费者基础。

## 五、与已有设计的关系

- 上游：`P2-MA3-05` 裁定（1834-3 批次）记录于 `FileToolBizModel` / `DslToolImpl` javadoc——本契约文档为该裁定的正式收口。
- 相关：`docs-for-ai/` 无 file tool / FS 抽象章节（核验 0 引用），使用层面文档无需同步。
- 追踪：`ai-dev/audits/arm-index.md` §P2 修复追踪（结构类 P2 后续批次）P2-MA1-012 行。
