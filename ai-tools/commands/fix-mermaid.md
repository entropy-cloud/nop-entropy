负责修复Markdown 文档中所有 ```mermaid 代码块``` 的内容，使这些图在保守的 Mermaid 渲染环境中可以正常显示。

要求：

1. **只改 mermaid 代码块**
   - 只修改 ```mermaid ... ``` 内部内容。
   - 其他 Markdown 内容一律不改、不增删。

2. **flowchart 规则**

   目标风格示例：

   ```mermaid
   flowchart TD
     A[User sends request] --> B[Plugin entry]
     B --> C[Load config]
     C --> D[Merge user and project config]
     D --> E[Build hooks]
     E --> F[Handle chat message]
     E --> G[Handle tool call]
     E --> H[Handle event]
   ```

   具体约束：
   - 第一行用 `flowchart TD` 或 `flowchart LR`。
   - 节点 ID 只用字母数字，如 `A`, `B1`。
   - 标签文字只用字母、数字和空格（英文）。
   - **不要在标签文字中使用这些符号：**  
     `() <> , ? : = " ' / \` 以及其他类似标点，仅保留字母数字空格。
   - 条件节点用 `{}`，内部也只用字母数字空格；条件分支用 `|yes|`、`|no|` 这类简单单词。
   - 不在标签中写函数调用语法，改用自然语言短句，例如：
     - 把 `background_output(task_id, block?)` 改成 `User calls background output`。

3. **classDiagram 规则**

   目标风格示例：

   ```mermaid
   classDiagram
     class Session {
       +id : string
       +title : string
       +parentId : string
     }

     class Task {
       +id : string
       +sessionId : string
       +status : string
     }

     class Manager {
       +tasks : object
       +createTask() void
       +getTask() object
     }

     Manager "1" --> "*" Task : manages
     Task "*" --> "1" Session : belongs to
   ```

   具体约束：
   - 类名、字段名用字母数字。
   - 字段类型用简单词：`string`, `number`, `object`, `boolean`。
   - 方法签名保持简单，不用泛型或复杂类型。
   - 关系说明文字只用字母数字空格。

4. **sequenceDiagram 规则**

   目标风格示例：

   ```mermaid
   sequenceDiagram
     participant U as User
     participant R as Runtime
     participant P as Plugin
     participant M as Manager

     U->>R: User sends message
     R->>P: Runtime calls plugin
     P->>M: Plugin asks manager to start task
     M->>R: Manager creates child session
     R-->>U: Runtime returns ack
     M-->>P: Manager reports task started
     P-->>U: Plugin sends task info
   ```

   具体约束：
   - `participant` 名称只用字母数字空格。
   - 消息文本只用字母数字空格，**不要使用上面列出的那些符号**。
   - 函数调用改为一句自然语言描述行为。

5. **输出**

   - 返回完整的 Markdown 文档。
   - 所有 ```mermaid``` 代码块都按上述规则改写为可渲染版本。
   - 除 mermaid 代码块内容外，其余部分保持完全不变。
