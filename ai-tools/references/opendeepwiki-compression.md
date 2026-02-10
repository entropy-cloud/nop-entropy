### 1. 系统提示词 (System Prompt)

这是为执行摘要任务的 AI 设置的“角色”和“总体目标”。

**提取的提示词：**
```
You are a conversation summarization assistant for AI development conversations.
Your task is to analyze conversation history and create a concise, accurate summary.
Focus on extracting the most important information while maintaining context coherence.
```

**简短说明：**
这个提示词首先定义了AI的角色——一个**专为AI开发对话服务的摘要助手**。通过明确“AI development conversations”这个领域，它引导模型在压缩时优先关注代码、文件名、架构等技术细节，而不是普通的对话礼节或闲聊，从而提高了摘要的专业性和相关性。

---

### 2. 用户指令提示词 (User/Instruction Prompt)

这是在给定历史对话后，向AI发出的具体压缩指令和要求。

**提取的提示词：**
```
Please compress the above conversation history into a concise summary.

The summary should:
1. **Retain key information and context**:
   - File paths, class names, function names mentioned
   - Code snippets or implementations discussed
   - Initial task requirements and goals

2. **Record important decisions and outcomes**:
   - Technical decisions and architecture choices
   - Solutions to problems or bugs fixed
   - Files modified and changes made
   - Test results or build status

3. **Preserve the final task requirements and goals**:
   - What needs to be accomplished
   - Current implementation status
   - Any pending tasks or issues

4. **Use concise language**:
   - Not exceeding 1/3 of the original length
   - Use bullet points for clarity
   - Focus on technical details, not conversational fluff

**Important**: Provide ONLY the summary without any additional explanations, meta-commentary, or preamble.
Start directly with the summarized content.
```

**简短说明：**
这个提示词非常结构化，它通过以下几点来确保高质量的摘要输出：
*   **明确提取内容：** 分为“关键信息”、“重要决策”、“任务目标”三个维度，清晰地告知模型需要保留哪些内容。
*   **量化和格式约束：** 提出了“不超过原文1/3长度”和“使用项目符号”等具体要求，使输出格式统一且可控。
*   **严格的输出控制：** 明确禁止模型添加任何前言或解释（如“好的，这是摘要…”），确保输出结果干净，便于程序直接使用，节省了Token也降低了后续处理的复杂度。

---

### 附：摘要注入格式

这虽然不是直接给模型的提示词，但却是该策略的关键一环，用于将生成的摘要重新整合到对话历史中。

**格式示例：**
```xml
<conversation-history-summary>
[这是我们之前对话的上下文摘要]

{summary}
</conversation-history-summary>
```

**简短说明：**
将生成的摘要内容用 `<conversation-history-summary>` 这样的“伪XML”标签包裹起来，再作为一条新的系统消息注入到对话上下文中。这利用了现代大模型对标签的敏感性，帮助模型明确区分“这是历史摘要”和“这是当前用户的输入”，从而避免信息混淆。