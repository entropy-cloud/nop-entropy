# 使用本地部署的DeepSeek 8B模型实现长文档的翻译

    随着DeepSeek的横空出世，AI大模型得以初步实现技术平民化，在本机部署8B左右的模型即可实现一些有用的功能。本文简单介绍了实用deepseek大模型实现技术文档的翻译过程，以及翻译过程中遇到的一些问题。

## 模型选择
使用ollama在本地笔记本上运行AI大模型，尝试了如下选择：
1. `qwen2.5:7b`
这个模型的问题是经常将输入的中文进行少量改写后按照中文输出，并没有实现翻译，而且经常在输出时增加额外的markdown块标记。

2. `deepseek-r1:7b`
这个模型的问题与`qwen2.5`类似，也是在翻译时经常输出中文，而没有执行翻译，而且无法理解进一步修正的指令。如果将温度调高，比如调整为1以上，经常会回复答非所问的数学方面的回答。

3. `deepseek-r1:8b`
比前两个模型要好，同样的问题和高温度设置，`deepseek-r1:7b`开始胡言乱语，`deepseek-r1:8b`还可以保证基本正常。

## 环境准备
我的笔记本型号是ThinkPad X1 Carbon 2024，它具有Intel Arc系列显卡，可以提供GPU加速功能。

为了让ollama支持Intel的GPU，可以从 [https://github.com/francisol/ollatel/releases](https://github.com/francisol/ollatel/releases) 下载ollatel的安装包，直接执行即可。

ollatel提供了简易的Ollama控制界面。

![](images/ollatel.png)

调试时的可视化聊天界面可以使用[ChatBox](https://chatboxai.app/zh)。

在命令行中通过`ollama pull deepseek-r1:8b`下载deepseek模型。

### 修改输入和输出的最大token数
ollama缺省的输入上下文长度是2048，对于比较长的输入提示来说会出现数据被截断的问题。
可以在调用的时候传递`num_ctx`参数，也可以创建一个名为`deepseek-8b-8k.txt`的文件，其中增加num_ctx参数的配置

```
FROM deepseek-r1:8b

PARAMETER num_ctx 8192
PARAMETER num_predict -1
```

然后执行 `ollama create deepseek-r1:8b-8k -f deepseek-8b-8k.txt`来创建一个名为`deepseek-r1:8b-8k`的新的模型。

扩大上下文长度后会导致运行时内存消耗变大。



