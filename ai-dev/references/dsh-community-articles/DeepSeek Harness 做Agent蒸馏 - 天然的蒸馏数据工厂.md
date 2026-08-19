# DeepSeek Harness做Agent蒸馏——我翻了源码，发现这架构简直是天然的蒸馏数据工厂

> 来源: https://mp.weixin.qq.com/s/YCJe84mPd51AuUMrT4Cgcw
> 作者: 唐成
> 抓取: 2026-08-19（curl 原始 HTML + stdlib 转换；图片未保留；标题/作者经页面 og 元数据核验与链接一致）

DeepSeek前天开源了自家的Agent Harness（DSH），41K star，全网都在讨论它的插件架构。

但我关注到一个小众角度：这东西用来做Agent模型蒸馏，简直天选。

不是吹。我翻完了它的架构文档、事件系统、持久化日志设计，发现DSH的核心设计刚好踩中了Agent蒸馏的每一个痛点。今天拆给你看。

先说Agent蒸馏到底难在哪

常规模型蒸馏大家都熟：大模型当老师，小模型当学生，用大模型的输出训练小模型。NLP领域玩了很多年了。

但Agent蒸馏完全是另一个物种。

Agent模型需要的不只是"输入→输出"的文本对，它需要完整的推理轨迹：

看到用户消息→思考要调什么工具

→工具调用的参数是什么

→拿到工具返回后怎么决策

→下一步干什么

→多轮循环直到任务完成。

这就是ReAct（Reasoning + Acting）决策链路。

问题来了：你怎么拿到这种完整轨迹数据？

用LangChain跑批量任务？

它只存对话消息列表，工具调用的中间步骤——比如模型第2轮reasoning时为什么选择了工具A而不是工具B——这种中间状态根本不留存。

自己写日志埋点？

每个Agent框架的内部结构都不一样，埋点代码侵入性大，换个框架就废了。

手工标注？一条带工具调用的多轮轨迹，标注成本大概是纯文本QA的10-15倍。

蒸馏Agent模型，80%的工程量不在训练，而在数据生产。

DSH刚好能解决这个问题。

DSH的事件流：天然蒸馏轨迹

DSH对会话数据的处理方式，用一个设计决策就把其他框架甩开了：

Append-only事件流日志。

所有交互——用户消息、模型推理、工具调用、工具返回、流式token、步骤生命周期——全是不可篡改的追加事件。

看一下DSH持久化日志里记录的事件类型：

turn/start → 一个对话轮次开始
step/start → 一次模型请求+工具执行开始
user/message → 用户输入
assistant/chunk → 流式token（原始逐字输出）
assistant/message → 模型完整回复（含usage/token消耗）
tool/call → 模型请求工具调用（含原始参数JSON）
tool/result → 工具返回结果
step/end → 步骤结束
turn/end → 轮次结束

发现没有？

这就是一条完整的ReAct轨迹。

turn/start 

→ user/message（用户问题）

→ assistant/message（模型思考）

→ tool/call（决策：调用工具）

→ tool/result（工具返回）

→ assistant/message（基于返回值的下一步推理）

→ ... 

→ turn/end

一条JSONL日志，就是一份现成的SFT训练样本。每个样本自带完整的Agent行为轨迹，不需要额外埋点。

对比一下普通框架：

LangChain的memory组件只存 HumanMessage 和 AIMessage。

模型第2轮为什么调了工具B？工具B返回了什么？

——不好意思，默认不留存，你得自己写callback handler去捞。

DSH的日志里，连assistant/chunk（流式token）都完整留存，连token消耗量都记在assistant/message的usage字段里。这意味着你不仅能复刻模型的最终决策，还能复刻它的输出风格和推理节奏。

怎么用DSH批量生产蒸馏数据

光说不练假把式。我拆一下实操路径。

▪ 1. Headless模式跑批量任务

DSH支持headless模式——无UI、无人工交互，纯命令行跑任务：

# 设置教师模型的API Key
export DEEPSEEK_API_KEY=your_key_here
 
# 批量跑任务（每个任务一个独立session，完整日志留存）
dsh --profile headless "读取data/orders.csv，统计每个区域的订单总量，画一个柱状图保存到output/"

跑完后，这个任务的完整ReAct轨迹就存在了session日志里。模型怎么读文件的、怎么写SQL统计的、怎么调画图工具的——全有。

批量任务就更直接：

# 批量跑10个不同场景的任务
for task in tasks/*.txt; do
 dsh --profile headless "$(cat $task)"
done

每个任务产生一个独立的session日志，互不干扰。

▪ 2. 导出事件流为蒸馏数据集

DSH的session日志是JSONL格式，每个事件一行JSON。写个转换脚本把它变成SFT训练格式：

import json
 
def export_distill_dataset(session_log_path, output_path):
 """把DSH session日志转成蒸馏训练集"""
 with open(session_log_path) as f:
 events = [json.loads(line) for line in f]
 
 samples = []
 current_messages = []
 
 for event in events:
 etype = event["type"]
 data = event["data"]
 
 if etype == "user/message":
 current_messages.append({
 "role": "user", 
 "content": data["message"]["content"]
 })
 elif etype == "assistant/message":
 current_messages.append({
 "role": "assistant",
 "content": data["message"]["content"],
 "model": data.get("model", ""),
 "usage": data.get("usage", {})
 })
 elif etype == "tool/call":
 current_messages.append({
 "role": "tool_call",
 "name": data["name"],
 "arguments": json.loads(data["arguments"])
 })
 elif etype == "tool/result":
 current_messages.append({
 "role": "tool_result",
 "content": data["message"]["content"]
 })
 elif etype == "turn/end":
 if len(current_messages) >= 3:
 samples.append({
 "messages": current_messages,
 "metadata": {
 "turn_count": sum(
 1 for m in current_messages 
 if m["role"] == "assistant"
 )
 }
 })
 current_messages = []
 
 with open(output_path, "w") as f:
 for sample in samples:
 f.write(json.dumps(sample, ensure_ascii=False) + "\n")
 
 print(f"导出 {len(samples)} 条蒸馏样本")
 
export_distill_dataset(
 "~/.dsh/sessions/task-001.jsonl",
 "distill_dataset.jsonl"
)

导出后直接丢给DeepSpeed做SFT训练就行。DSH只管数据生产，训练这活儿不是它干的。

▪ 3. Session Fork：低成本扩充样本多样性

这个是我觉得最巧妙的设计。

DSH的session fork能力允许你从任意事件节点分叉，创建一条新的推理路径：

#从session的第5个事件处fork，换一个prompt继续
#产生同一任务的第二条不同推理轨迹
forked = ctx.sessions.fork(
 source=original_session,
 boundary=5, #从第5个事件处分叉
 childSessionId="task-001-variant-b"
)

同一个任务，fork后换个工具组合或换条推理路径，低成本产出多样化的教师轨迹。

蒸馏最怕什么？数据单一。同一个任务只有一条轨迹，小模型学不到泛化能力。用fork可以从任意决策点分叉，生成"如果模型当时选了另一个工具会怎样"的对照轨迹。

这个能力是普通框架基本没有的——它们的session是不可分割的整体，要么全部重来，要么没有。

▪ 4. pre-step钩子：数据质量守门员

DSH在模型推理前有个agent/pre-step事件钩子，能拦截不合格任务。

蒸馏最怕脏样本：敏感数据混进去、工具调用失败但轨迹照样保留、模型走了死路。

在pre-step里加一层过滤：

# cordis.patch.yml
plugins:
 - id: distill-guard
 config:
 on_pre_step:
 # 过滤包含敏感信息的输入
 block_patterns:
 - "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b" #信用卡号
 - "\\b1[3-9]\\d{9}\\b" # 手机号
 # 拦截已知会导致工具调用失败的路径
 reject_if_contains:
 tool: "bash"
 args_pattern: "rm -rf"

产出的数据天然干净，省掉大量后处理成本。

别急着All in，这三个坑你得知道

说完优点，坑也得说，不然坑的就是你。

坑一：DSH不是训练框架。

它只负责数据采集和轨迹生成。产出的JSONL还得自己导出去做SFT训练。DeepSpeed、Transformers、LLaMA-Factory，该用什么还用什么。DSH替代不了训练侧的任何工具。

坑二：刚开源一天，API随时变。

DSH官方README写得很明确——"developer preview"，"THERE WILL BE COMPATIBILITY-BREAKING CHANGES"。session日志格式版本号还是0，没有任何兼容性承诺。你要是基于现在的API搭蒸馏流水线，做好版本锁定的准备，别一个git pull数据全废了。

底层Cordis框架更小众，文档全英文，社区还很小。出了问题大概率得自己翻源码。

坑三：纯对话蒸馏别用它，杀鸡用牛刀。

如果你的蒸馏场景不涉及工具调用——比如就是把GPT-5的文案能力蒸馏到小模型——那DSH的能力完全过剩。用LangChain跑批量问答，导出为JSONL就完事了，简单10倍。

DSH的价值在Agent行为蒸馏这个特定场景：带工具调用、多步骤规划、纠错能力的完整ReAct链路。脱离这个场景，它的复杂度就是纯粹的负担。

一句话总结

DSH做Agent蒸馏的核心价值：它能产出完整、可审计、可批量扩充的Agent全链路推理轨迹，这是普通对话框架给不了的。

但它只是数据生产工具，不是训练框架。昨天才开源，API随时breaking change。纯对话蒸馏别上它。

Agent蒸馏的瓶颈从来不在训练，在数据。DSH刚好捅到了这个点上。

你做Agent蒸馏时，数据生产花了你多少时间？评论区聊聊。

💡 一句话带走：

DSH的事件流日志是天选蒸馏数据源，但它只管生产不管训练。

⚠️ 踩坑提醒：

DSH的session日志格式还在v0（无兼容性承诺），上生产前务必锁定版本号。

GitHub：https://github.com/deepseek-ai/deepseek-harness

手撕 GPT#09：验证 loss 从 340 降到 6.5，模型却只会说“对对对”——小模型蒸馏的“不可能三角”

花费1.03元微调豆包大模型解决了业务问题，分享一下经验

DSH 之所以能拿到完整 Agent 轨迹，核心是它把 Agent 运行的全部过程做成一条只追加的事件流，并且强制：只要是模型看到的内容，就必须写入日志，不是事后回调捞日志，而是日志本身就是运行时的真相源，天然产出完整的思考‑工具调用‑反馈闭环轨迹，拿来做 Agent 蒸馏数据集就非常顺手。

- 蒸馏有什么用？

把昂贵大 Agent 的整套干活行为，复刻给小模型，降本、提速、私有化部署。

- 到底蒸馏什么？

不是蒸馏答案文本，蒸馏「怎么规划、选什么工具、拿到结果怎么继续推理」这套 Agent 行为。

- 为什么 DSH 拿得到，别的框架拿不到？

DSH 运行时原生产出完整不可篡改事件流，强制模型所见即日志所见；普通框架很多中间状态只在内存，拿不到完整闭环。

  

  

预览时标签不可点

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

  

微信扫一扫
关注该公众号

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 

 知道了 

 

 

   

 

 

  微信扫一扫
使用小程序 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 

 

 

  

 

 

 取消 允许 

 

 

 

 × 分析 

 

 

 

 

  

  

  

 

 

 

 

微信扫一扫可打开此内容，
使用完整服务

 

 

 

 

 

 

 

 

  ： ， ， ， ， ， ， ， ， ， ， ， ， 。   视频 小程序 赞 ，轻点两下取消赞 在看 ，轻点两下取消在看 分享 留言 收藏 听过
