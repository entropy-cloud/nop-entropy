# Core Tools

1. File traversal
2. File filtering
3. File structure parsing (AST, Markdown)
4. File output
5. File merging
6. RAG: retrieval of relevant file fragments

## IndexManager
IndexManager uses LLM prompts to extract symbols from code files. The get_all_file_symbols prompt extracts the following:

1. Functions and their parameters
2. Classes and their methods
3. Variables and their types
4. Import statements

<!-- SOURCE_MD5:faba86ecf8c2835788986b941a2b9185-->
