# 使用模式

* `/command `发起命令
* 命令参数中 `@fileName.txt`这种形式来指定文件
* 每次运行自动commit，并生成git diff，可以通过`/revert`回滚
* 启用jupiter的notebook来进行交互式测试
* `/conf human_as_mode:true`之后通过人工交互，自动复制prompt到剪贴板
* `/lib /add libName` 引入外部库
* 每次交互都会产生一个chat_action.yaml，记录完整的交互信息
* `/conf rag_url:url` 引入rag支持
* `autocoder.web`启动一个Web IDE并执行terminal
