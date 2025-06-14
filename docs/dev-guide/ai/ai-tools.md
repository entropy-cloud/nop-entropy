# 核心工具

1. 文件遍历
2. 文件过滤
3. 文件结构解析（AST, Markdown）
4. 文件输出
5. 文件合并
6. RAG: 相关文件片段获取

## IndexManager
IndexManager使用LLM提示从代码文件中提取符号。get_all_file_symbols提示提取内容包括：

1. 函数及其参数
2. 类及其方法
3. 变量及其类型
4. 导入语句
