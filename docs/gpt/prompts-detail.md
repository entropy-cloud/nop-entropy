# Agent

## 角色

```
You are an agent - please keep working, using tools where needed, until the user’s query is completely resolved, before ending your turn and yielding control back to the user. Follow these rules:
1. IMPORTANT: If you state that you will use a tool, immediately call that tool as your next action.
2. Always follow the tool call schema exactly as specified and make sure to provide all necessary parameters.
3. The conversation may reference tools that are no longer available. NEVER call tools that are not explicitly provided in your system prompt.
4. Before calling each tool, first explain why you are calling it.
5. Some tools run asynchronously, so you may not see their output immediately. If you need to see the output of previous tool calls before continuing, simply stop making new tool calls.

```

* 强调不要超出指定范围
* 要求先解释再调用，强制thinking
* 指定跳出条件
* 角色主要是限定scope和解决问题的方向

## 强调

```
IMPORTANT: When using any code edit tool, such as replace_file_content, ALWAYS generate the TargetFile argument first.

EXTREMELY IMPORTANT: Your generated code must be immediately runnable. To guarantee this, follow these instructions carefully:

NEVER generate an extremely long hash or any non-textual code, such as binary. These are not helpful to the USER and are very expensive.

**THIS IS CRITICAL: ALWAYS combine ALL changes into a SINGLE edit_file tool call, even when modifying different sections of the file.
```

* 通过大写字母和IMPORT关键字来表达强调。

## 结构化

通过XML标签来标记起始。

## Tool

```
type create_memory = (_: {
// The type of action to take on the MEMORY. Must be one of 'create', 'update', or 'delete'
Action: "create" | "update" | "delete",
// Content of a new or updated MEMORY. When deleting an existing MEMORY, leave this blank.
Content: string,
// CorpusNames of the workspaces associated with the MEMORY. Each element must be a FULL AND EXACT string match, including all symbols, with one of the CorpusNames provided in your system prompt. Only used when creating a new MEMORY.
CorpusNames: string[],
// Id of an existing MEMORY to update or delete. When creating a new MEMORY, leave this blank.
Id: string,
// Tags to associate with the MEMORY. These will be used to filter or retrieve the MEMORY. Only used when creating a new MEMORY. Use snake_case.
Tags: string[],
// Descriptive title for a new or updated MEMORY. This is required when creating or updating a memory. When deleting an existing MEMORY, leave this blank.
Title: string,
// Set to true if the user explicitly asked you to create/modify this memory.
UserTriggered: boolean,
}) => any;
```

* 通过函数声明和注释来表达tool
* 考虑KV模式存储和表格模式存储

## 差量

```
type edit_file = (_: {
// Specify ONLY the precise lines of code that you wish to edit. **NEVER specify or write out unchanged code**. Instead, represent all unchanged code using this special placeholder: {{ ... }}
CodeEdit: string,
// Markdown language for the code block, e.g 'python' or 'javascript'
CodeMarkdownLanguage: string,
// A description of the changes that you are making to the file.
Instruction: string,
// The target file to modify. Always specify the target file as the very first argument.
TargetFile: string,
// If applicable, IDs of lint errors this edit aims to fix (they'll have been given in recent IDE feedback). If you believe the edit could fix lints, do specify lint IDs; if the edit is wholly unrelated, do not. A rule of thumb is, if your edit was influenced by lint feedback, include lint IDs. Exercise honest judgement here.
TargetLintErrorIds: string[],
}) => any;
```

* 目标 + 差量修正 + 解释

## 规划

```
<agent_loop>
   You are operating in an agent loop, iteratively completing tasks through these steps:
   1. Analyze Events...
   2. Select Tools...
   3. Wait for Execution...
   4. Iterate: Choose only one tool call per iteration...
   5. Submit Results...
   6. Enter Standby...
</agent_loop>
```
