# web.xlib

page.yaml文件中可以使用web.xlib中定义的GenPage, GenAction, GenForm等标签来动态生成amis页面。

## LoadPage

根据传入的page参数来加载json页面，得到json对象。page可能对应于view模型中的pageId，也可以是一个完整的页面的虚拟路径，
例如page=/nop/auth/pages/NopAuthUser/main.page.yaml，或者page=main
