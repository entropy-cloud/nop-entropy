### 提示词格式规范

**1. 核心设计原则**

*   **清晰性优先**: 格式旨在实现人类可读性和机器可解析性的最佳平衡。
*   **混合结构**: 采用两种互补的格式：
  *   **YAML/Key-Value风格**: 用于定义结构清晰的参数和元数据。
  *   **XML风格标签**: 用于包裹大段、多行的非结构化或半结构化内容。
*   **层级分明**: 使用缩进明确表示数据和指令的层级关系。

**2. 通用格式化规则**

*   **缩进**: 使用 **两个空格** 进行缩进。禁止使用Tab字符。
*   **顶级元素**: 文件的顶级结构元素（如 `role`, `objective`, `input_context` 等）不进行缩进。它们是提示词的根节点。
*   **换行**: 每个独立的指令、参数或标签块都应占据新的一行。

**3. 结构化元素定义**

**A. 键值对 (Key-Value Pair)**

*   **语法**: 使用 `- Key: Value` 格式。
  *   以连字符和空格 (`- `) 开头。
  *   冒号 (`:`) 后面必须跟一个空格。
*   **用途**: 适用于定义单个、简短的数据点，如配置参数、URL、名称等。
*   **层级**: 子级的键值对需要相对于父级缩进两个空格。
*   **示例**:
    ```
    <analysis_parameters>
      - maxNodes: {{maxNodes}}
      - maxDepth: {{maxDepth}}
    </analysis_parameters>
    ```

**B. 内容块标签 (Content Block Tag)**

*   **语法**: 使用成对的XML风格标签 `<tag_name>...</tag_name>`。
*   **用途**: 专用于包裹多行、复杂或格式不定的内容块，例如文件树、代码片段、日志、长篇描述等。这可以确保模型准确识别内容的开始和结束边界。
*   **层级**: 标签本身遵循其父级的缩进规则。标签内部的内容应相对于标签再缩进两个空格。
*   **示例**:
    ```xml
    <input_context>
      - repositoryUrl: {{repositoryUrl}}
      <file_tree>
        {{fileTree}}
      </file_tree>
    </input_context>
    ```

**4. 命名约定 (Naming Conventions)**

*   **键名 (Keys)**: 所有在 `- Key: Value` 格式中使用的键名，应采用 **小驼峰命名法 (camelCase)**。
  *   示例: `repositoryUrl`, `branchName`。
*   **标签名 (Tags)**: 所有在 `<tag_name>` 格式中使用的标签名，应采用 **小写蛇形命名法 (snake_case)**。
  *   示例: `<role>`, `<objective>`, `<input_context>`, `<file_tree>`。

**5. 完整目标格式示例**

请将输入提示词严格转换为以下格式：

```markdown
<role>
You are an expert Code Architecture Analyst, functioning as a powerful knowledge extraction engine. Your primary function is to apply your deep analytical skills to understand a codebase's structure, patterns, and relationships. You must then distill all of your findings and insights *solely* into a structured, machine-readable **XML document**. You do not produce narrative, explanation, or any other form of human-readable output. Your "voice" is pure, structured data.
</role>

<objective>
To perform a deep analysis of the provided codebase and generate a single, comprehensive **XML document** that represents the system's architectural knowledge graph. This graph must detail components (nodes) and their relationships (edges).
</objective>

<input_context>

- repositoryUrl: {{repositoryUrl}}
- branchName: {{branchName}}
  <file_tree>
  {{fileTree}}
  </file_tree>
  </input_context>

<analysis_parameters>

- maxNodes: {{maxNodes}}
- maxDepth: {{maxDepth}}
  </analysis_parameters>

<output_spec>

  ```xml
  <!-- example of path-and-line: relative/path.ext[:line] -->
  <knowledge_graph>
    <nodes>
      <node id="stable-unique-id" type="string" label="human-friendly-name">
        <evidence>path-and-line</evidence>
      </node>
      <!-- ... more nodes -->
    </nodes>
    <edges>
      <edge from="stable-unique-id" to="stable-unique-id" label="human-friendly-name" type="string">
        <evidence>path-and-line</evidence>
      </edge>
      <!-- ... more edges -->
    </edges>
  </knowledge_graph>
  ```
</output_spec>
```
