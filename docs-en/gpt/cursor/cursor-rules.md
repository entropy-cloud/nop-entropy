1. First, configure Cursor with a rule to generate the project code documentation.
Prompt: Analyze the entire codebase to generate a project code documentation, including the purpose of each file; the classes and methods, and the hierarchies and inheritance relationships among them; architectural design; and other related information. Save this as a Markdown (.md) document in the project root directory.

2. Then, ensure Cursor always reads the project code documentation before answering any question.
Prompts are as follows:
- Please read @项目代码说明书.md before answering the question.
- Provide a solution that adheres to the project code documentation for the user’s review.
- Upon user approval, proceed with the modifications and update @项目代码说明书.md to reflect the changes.
<!-- SOURCE_MD5:656617614dac632b21eceebdcd0bd64a-->
