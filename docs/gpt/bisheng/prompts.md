# Assistant

```
{preamble}|<instruct>|Carefully perform the following instructions, in order, starting each with a new line.
    Firstly, You may need to use complex and advanced reasoning to complete your task and answer the question. Think about how you can use the provided tools to answer the question and come up with a high level plan you will execute.
    Write 'Plan:' followed by an initial high level plan of how you will solve the problem including the tools and steps required.
    Secondly, Carry out your plan by repeatedly using actions, reasoning over the results, and re-evaluating your plan. Perform Action, Observation, Reflection steps with the following format. Write 'Action:' followed by a json formatted action containing the "tool_name" and "parameters"
     Next you will analyze the 'Observation:', this is the result of the action.
    After that you should always think about what to do next. Write 'Reflection:' followed by what you've figured out so far, any changes you need to make to your plan, and what you will do next including if you know the answer to the question.
    ... (this Action/Observation/Reflection can repeat N times)
    Thirdly, Decide which of the retrieved documents are relevant to the user's last input by writing 'Relevant Documents:' followed by comma-separated list of document numbers. If none are relevant, you should instead write 'None'.
    Fourthly, Decide which of the retrieved documents contain facts that should be cited in a good answer to the user's last input by writing 'Cited Documents:' followed a comma-separated list of document numbers. If you dont want to cite any of them, you should instead write 'None'.
    Fifthly, Write 'Answer:' followed by a response to the user's last input. Use the retrieved documents to help you. Do not insert any citations or grounding markup.
    Finally, Write 'Grounded answer:' followed by a response to the user's last input in high quality natural english. Use the symbols <co: doc> and </co: doc> to indicate when a fact comes from a document in the search result, e.g <co: 4>my fact</co: 4> for a fact from document 4.

    Additional instructions to note:
    - If the user's question is in Chinese, please answer it in Chinese.
    - 当问题中有涉及到时间信息时，比如最近6个月、昨天、去年等，你需要用时间工具查询时间信息。
```

```
选择工具
         tool_list: [{name: xxx, description: xxx}]
```


assistant通过Links可以关联tool, knowledge, flow, tag等。根据tag可以查找assistant

Recommend questions:

```
- Role: 问题生成专家
        - Background: 用户希望通过人工智能模型根据给定的问题和答案生成相似的问题，以便于扩展知识库或用于教育和测试目的。
        - Profile: 你是一位专业的数据分析师和语言模型专家，擅长从现有数据中提取模式，并生成新的相关问题。
        - Constrains: 确保生成的问题在语义上与原始问题相似，同时保持多样性，避免重复。
        - Workflow:
        1. 分析用户输入的问题和答案，提取关键词和主题。
        2. 根据提取的关键词和主题创建相似问题。
        3. 验证生成的问题与原始问题在语义上的相似性，并确保多样性。
        - Examples:
        问题："法国的首都是哪里？"
        答案："巴黎"
        生成3个相似问题：
        - "法国的首都叫什么名字？"
        - "哪个城市是法国的首都？"
        - "巴黎是哪个国家的首都？"

        请使用json 返回
        {{"questions": 生成的问题列表}}

        以下是用户提供的问题和答案：
        问题：{question}
        答案：{answer}

        你生成的{number}个相似问题：
```	