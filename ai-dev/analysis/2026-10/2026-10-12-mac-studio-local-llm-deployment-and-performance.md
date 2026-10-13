# Mac Studio M4 Max 本地 LLM 部署方案选型与性能实测

> Status: resolved
> Date: 2026-10-12
> Scope: 本机（Mac Studio M4 Max / 40 核 GPU / 128GB 统一内存）本地 LLM 推理栈的模型选型、部署与性能基准（Qwen3.8-27B 编码 + StartLux-Decision-v1 判别）
> Conclusion: 编码选 oMLX + Qwen3.8-27B-oQ4e + MTP k=3（74.4 tok/s，为 LM Studio 同模型 6bit 的 3.6×，128K 满窗解码 30–34 tok/s）；判别选 StartLux-Decision-v1-27B-Q8_0（0.49s/次，输入长度线性决定延迟，等效预填充 240 tok/s）；ANE 在单 ANE 的 M4 Max 上仅解码侧有正收益

## Context

选择在本机跑开源编程模型作为编码助手，需要回答三个决策点：

1. **哪个模型** —— 能力最强且能在 128GB 统一内存里全速运行
2. **哪个运行时** —— LM Studio（GUI 友好）vs oMLX / MLX 系（可脚本化、可优化）
3. **怎么量化** —— 4bit / 6bit / 8bit，以及是否启用投机解码

约束条件：

- 磁盘：系统盘仅剩 104GB，外置盘 `/Volumes/data` 有 1TB
- 网络：`huggingface.co` 直连被阻断，只能走镜像
- 工具链：机器原只有 Command Line Tools，无 Metal 编译器

---

## Analysis

### 一、模型清单与身份核对

本机共部署两套服务，用的**具体模型**如下（均已核对 config.json / 文件实体）：

| 用途 | 模型 | 基座架构 | 量化 | 体积 | 权重路径 |
|---|---|---|---|---|---|
| 编码/对话 | `Jundot/Qwen3.8-27B-oQ4e-mtp` | Qwen3.8-27B（27B dense，原生多模态） | oQ4e（4-bit + 166 张量保 5-bit） | 16.6 GB | `/Volumes/data/omlx-models/` |
| 判别/决策 | `StartLux-Decision-v1-27B-Q8_0` | **Qwen3.5-27B**（27B，`qwen3_5` / `Qwen3_5ForConditionalGeneration`） | Q8_0（llama.cpp 标准） | 28.6 GB | `/Volumes/data/models/StartLux-Decision-v1-27B-Q8_0-GGUF/` |
| 判别/决策（轻量） | `StartLux-Decision-v1-0.8B-Q8_0` | Qwen3.5-0.8B | Q8_0 | 0.81 GB | `/Volumes/data/models/StartLux-Decision-v1-0.8B-Q8_0-GGUF/` |

两个 27B **底座同源**：`config.json` 均为 `qwen3_5` 架构、`hidden_size=5120`、
64 层中 `full_attention_interval=4`（即 16 层全注意力 + 48 层 Gated DeltaNet）。
StartLux 是 Qwen3.5-27B 的判别任务微调，不是 Qwen3.8。

**许可证差异（重要）**：

| 模型 | 权重许可证 | 商用 |
|---|---|---|
| Qwen3.8-27B | Apache-2.0 | ✅ 允许 |
| StartLux-Decision-v1 | **CC BY-NC 4.0** | ❌ **禁止**，商用需单独授权 |

StartLux 的推理代码（`startlux_decision/`）是 Apache-2.0，但**权重是 NC**。

### 二、StartLux 判别模型的本质与实测

**它不是普通 LLM**。`output_tokens` 恒为 0 —— 不生成自由文本，只从固定选项字母中
读出一个 logits 向量再校准成概率。关键点在 README 的这句：

> the option-letter readout and the per-type temperatures live in `gguf_server.py`,
> not in the weights. The file holds the text decoder only.

即**判别逻辑在 Python 决策层，不在权重里**。直接用 llama.cpp chat 接口问同一个模型
拿不到带概率的决策，必须走 `/v1/systemone` 协议。

协议 `decision_config.json` 关键参数：`max_options_per_pass=26`（单次最多 26 选项，
超出走 `wide_choice` 分组：25 个一组、保留 top-3、残差 0.001），
`temperature_by_type` 按题型分档（noul/choice/score 各不同）。

#### 延迟基准（27B Q8_0，llama.cpp build 11429，Metal 全卸载）

| 场景 | 输入 tokens | 延迟(min/med) |
|---|---:|---:|
| 1× noul（最简） | 81 | 0.49 / 0.49 s |
| 1× choice（3 选项） | 92 | 0.50 / 0.50 s |
| 1× choice（26 选项） | 323 | 1.34 / 1.34 s |
| 6× noul（多问题一次） | 498 | 2.14 / 2.14 s |
| 输入 ~562 tok | 562 | 2.38 s |
| 输入 ~1.2K | 1,202 | 4.87 s |
| 输入 ~3.3K | 3,282 | 13.32 s |
| 输入 ~5.3K | 5,304 | 21.30 s |
| 输入 ~13.2K | 13,158 | 55.06 s |

**等效预填充吞吐稳定在 196–249 tok/s**，即延迟几乎完全由输入长度线性决定，
与选项数、题型无关。**实用上限建议 4K 输入（13s）以内**。

#### 并发吞吐

| 并发 | parallel=4 吞吐 | parallel=1 吞吐 |
|---:|---:|---:|
| 1 | 1.02 req/s | 0.84 req/s |
| 4 | 1.13 req/s | 0.91 req/s |
| 8 | **1.13 req/s** | 0.90 req/s |

并发几乎无收益（8 并发与 4 并发持平）。原因很直接：`output_tokens=0`，
每次请求只有「预填充 + 单次读出」，没有可批处理的自回归解码阶段，
连续批处理无从发挥。`parallel=4` 相比 `parallel=1` 仅提升约 24%。

⚠️ **`--parallel` 会瓜分上下文**：`-c 16384 --parallel 4` 意味着每个 slot 只有
**4096** tokens（`n_slots=4, n_ctx_slot=4096`）。实测 5.3K 输入会触发
llama-server 的 Metal 命令缓冲区断言（`_MTLCommandBuffer waitUntilCompleted` 崩溃栈）。
若要处理长输入必须 `--parallel 1`。脚本已参数化：
`STARTLUX_PARALLEL` / `STARTLUX_CTX` / `STARTLUX_VARIANT`。

#### 准确率与置信度校准（20 例带标注测试集）

| 指标 | 27B Q8_0 | 0.8B Q8_0 |
|---|---:|---:|
| choice（5 选 1）准确率 | 10/10 = 100% | 10/10 = 100% |
| noul（二值判断）准确率 | 9/10 = 90% | 9/10 = 90% |
| 单请求延迟（1× noul） | 0.49 s | **0.03 s** |
| 并发吞吐（8 并发） | 1.13 req/s | **2.58 req/s** |
| noul 置信度分布 | 0.052 – 0.972（跨度大） | 0.294 – 0.627（**全部挤在 0.5 附近**） |
| choice 置信度 | 0.856 – 0.998 | 0.626 – 0.992 |

**两者准确率相同，但置信度分布差异是决定性的**：
27B 的概率拉得开（`0.052` 明确非紧急、`0.972` 明确紧急），可以直接设阈值或排序；
0.8B 全部挤在 0.5 上下（0.294–0.627），阈值稍有偏移就大面积误判。
若要利用概率做自动化路由（如「置信度 < 0.6 转人工」），必须用 27B。

0.8B 版本没有官方 README 与 JevBench 数据，上述对比为本次自建测试集结果，
样本量小（20 例），**仅供选型参考，不构成准确率基准**。

### 三、Qwen3.8-27B 模型选型：为什么是它

调研时点（2026-10）开源编程模型的能力天花板与本机可运行性的交叉结果：

| 模型 | 参数 | 能否本机跑 | 关键指标 | 结论 |
|---|---|---|---|---|
| **Qwen3.8-27B** | 27B dense | ✅ 4bit ≈17GB | SWE-bench Pro 61.7 / Terminal-Bench 2.1 73.0 / LiveCodeBench v6 90.3 | **选定** |
| Qwen3.8-Flash-Next | 125B-A6B + 51B n-gram | ❌ 需 75–112GB 总内存 | SWE-bench Pro 62.5 | 内存不够 |
| GLM-5.3-Flash | 320B-A18B | ❌ 最低 109GB@2bit | Coding Index 71.5 | 内存不够 |
| GLM-5.3 | 743B | ❌ | Coding Index 74.8 | 内存不够 |
| Kimi K3 | 2.8T | ❌ | Coding Index 76.2（开源第一） | 内存不够 |

Qwen3.8-27B 在"能全速本地跑"这个约束下就是开源最强。

**架构优势**：64 层中仅 16 层是全注意力，48 层是 Gated DeltaNet（常数递归状态）。
KV cache 因此极小 —— 131,072 上下文仅需约 30GB 总内存（实测加载峰值 29.73 GiB），
占 128GB 的 23%。这是长上下文可行的前提。StartLux 底座同款架构，但 `-c` 只开到 16K。

### 四、运行时选型：LM Studio vs oMLX

四个候选引擎的横向对比：

| 运行时 | Qwen3.8-27B 短上下文解码 | 投机解码 | 跨请求 KV 缓存 | 结论 |
|---|---:|---|---|---|
| LM Studio 0.4.26 (MLX) | 20.7 tok/s | ❌ 有 bug | ❌ 仅内存 | 兜底 |
| **oMLX 0.7.1** | **74.4 tok/s** | ✅ 原生 MTP | ✅ 分页 SSD | **选定** |
| vllm-metal 0.3.0 | 13.2 tok/s | ❌ | — | 淘汰 |
| mlx-vlm + MTP sidecar | 13.2 tok/s | ✅ | ❌ | 淘汰 |

关键差异不是速度本身，而是 **LM Studio 的 MLX 引擎无法对 batched 模型启用投机解码**
（`SpeculativeDecodingNotSupportedError`，0.4.21 仍复现）。Qwen3.8-27B 自带 MTP 头，
在 LM Studio 里拿不到这个 2× 加速，等于白白浪费模型能力。

### 五、三项加速的叠加效果与边界

oMLX 方案开了三项加速，实测各自的贡献：

| 加速 | 机制 | 实测贡献 |
|---|---|---|
| **原生 MTP k=3** | 模型自带 Multi-Token Prediction 头，投机解码 k=3 | **主要来源**。代码任务接受率 99.6%（tok/cycle 3.76） |
| oQ4e 量化 | 4-bit 主体 + 166 个敏感张量保 5-bit | 16.6GB，比 6bit 小 27% 且更快 |
| ANE prefill | 64 个 MLP 层的一半卸载到 Neural Engine | 42K 上下文解码 50.6 → 56.0 tok/s |

**MTP 接受率实测**（`tok/cycle` 决定加速比）：

| 任务类型 | 接受率 | tok/cycle |
|---|---:|---:|
| 代码生成 | 75–99.6% | 2.91–3.76 |
| 散文 | 75–80% | 2.78–2.97 |

**ANE 的真实边界**：社区流传的"预填充 3.3× 加速"数据来自 **M3 Ultra 双 ANE**。
M4 Max 只有单颗 ANE，且 `fraction=0.5` 只覆盖一半 MLP 层，attention 和 GDN 层
（Qwen3.8 有 48/64 层是 GDN）仍在 GPU 上，GPU↔ANE 搬数据抵消了大部分收益。
实测预填充 163–177 tok/s，与不开 ANE 基本持平。

### 六、Qwen3.8-27B 性能实测（本机，单流，温度 0）

**上下文-速度曲线**：

| 上下文 | 预填充 | TTFT | 解码 |
|---|---:|---:|---:|
| ~35 tok | 32 tok/s | 0.4 s | **74.4 tok/s** |
| ~31K | 261 tok/s | 119 s | 56.0 tok/s |
| ~56K | 205 tok/s | 133 s | 44.0 tok/s |
| ~92K | 166 tok/s | 231 s | 37.7 tok/s |
| **128,647 tok（满窗口）** | **163–177 tok/s** | **725–790 s** | **30–34 tok/s** |

解码从 74 → 30 tok/s 的衰减与上下文长度基本成线性（注意力扫描成本），
用 92K/128K = 1.39 与 37.7/30.0 = 1.26 互相印证。

**128K 满窗口配对实测**（重启服务 + 清空 SSD 缓存 + 停 StartLux）：

| 场景 | 缓存命中 | TTFT | 解码 |
|---|---:|---:|---:|
| 冷启动 | 0 | 788 s | 33.7 tok/s |
| 立即复测（同问句） | 128,642 | **3.0 s** | 32.4 tok/s |
| 换问句（同前缀） | 126,976 | 15.7 s | 30.0 tok/s |

分页 SSD KV 缓存让重复前缀的 TTFT 从 788s 降到 3.0s，**相差 260 倍**。
这比解码速度本身更关键 —— 编码 agent 每个 session 会反复复用文件前缀。

### 七、oMLX 前缀缓存的实现机制（源码级）

这一节回答两个问题：**缓存怎么实现的**，以及**改上下文窗口长度会不会让缓存失效**。
结论是后者 —— **不会**。

#### 实现：256-token 分块 + 链式哈希 + 两级存储

实现分布在 `omlx/cache/`（约 19,600 行），核心是 `prefix_cache.py`（5,590 行）+
`paged_ssd_cache.py`（5,414 行），设计上参考 vLLM 的 PagedAttention 分块方案。

**块哈希**（`paged_cache.py:84` `compute_block_hash`）：

```python
hasher = hashlib.sha256()
if model_name:  hasher.update(model_name.encode())      # ① 模型隔离
if parent_hash: hasher.update(parent_hash)             # ② 父块哈希（链式）
else:           hasher.update(b"omlx-root")            # ③ 固定种子
hasher.update(bytes(str(tuple(token_ids)), "utf-8"))   # ④ 本块 token 内容
if extra_keys:  hasher.update(bytes(str(extra_keys), "utf-8"))  # ⑤ 多模态/LoRA
```

第 ② 项是链式的关键：第 N 块的哈希依赖第 1..N-1 块，因此只要前缀有任何一个 token 变化，
该位置之后的所有块哈希全部改变，自动失效。`block_size = 256` tokens，
索引结构是 `_prefix_index: dict[hash → (prefix_len, block_ids, num_blocks)]`。

**两级存储**：
- **hot tier**（内存，LRU）—— 热块常驻
- **SSD tier**（safetensors 格式，分页落盘）—— 冷块持久化，跨请求、跨进程重启保留

调度器日志里的 `store_cache_main_*` / `store_cache_worker_sync` 就是写盘路径，
且是**异步**的（这一点后面会解释一个实测现象）。

#### 失效边界：签名里有什么、没什么

SSD 层的兼容性签名 `cache_signature_for()`（`paged_ssd_cache.py:340`）：

```python
cache_signature_for(
    model_name, num_layers, block_size, layer_cache_types,
    turboquant_kv_bits, cachelist_subtypes, gdn_sidecar_state_dtype,
)
```

**会让缓存失效的**：

| 变更项 | 原因 |
|---|---|
| 换模型 | `model_name` 进哈希，做模型隔离 |
| `block_size` 变更 | 进签名，分块边界变了链就断了 |
| KV 量化位数（TurboQuant） | 进签名，payload 布局不同 |
| 层缓存类型 / GDN sidecar dtype | 进签名 |
| 前缀中任一 token 改变 | 链式哈希自然断裂 |

**不会让缓存失效的**：

| 变更项 | 原因 |
|---|---|
| **`context_window` 大小** | **既不进块哈希，也不进签名** |
| 服务重启 | SSD 层持久化，重启后照常命中 |
| 推理参数（温度、reasoning_effort、max_tokens） | 不参与缓存标识 |
| 换提问（只改前缀之后的 suffix） | 链式哈希只锁定前缀 |

#### 实测：改上下文窗口 + 重启，缓存依然命中

场景 A —— 先在 `context_window=131072` 下跑热缓存，再改成 `32768` 并**重启服务**，
发完全相同的请求：

| 步骤 | prompt | 命中 | 命中率 | TTFT |
|---|---:|---:|---:|---:|
| A1（改窗口后首次） | 13,071 | 13,064 | **99.9%** | 0.8 s |
| A2（再次） | 13,071 | 13,064 | **99.9%** | 0.3 s |

未命中的 7 tokens 是尾块不足一个整块（13,071 不是 256 的整数倍）。

#### 实测：不同长度请求共享同一前缀

场景 B —— 同会话内先跑 128K，再跑它的前 18K，再交替复测：

| 步骤 | 请求 | prompt | 命中 | 命中率 | TTFT |
|---|---|---:|---:|---:|---:|
| B1 | 128K（冷） | 128,616 | 0 | 0% | 796.0 s |
| B2 | 18K（是 B1 的前缀） | 13,071 | 12,288 | **94.0%** | 4.9 s |
| B3 | 128K（复测） | 128,616 | 128,609 | **100.0%** | 1.1 s |
| B4 | 18K（复测） | 13,071 | 13,064 | **99.9%** | 0.3 s |

**长短请求完全共享缓存**。B3 的 TTFT 从 796 s 掉到 1.1 s，B4 从 4.9 s 掉到 0.3 s。

**B2 只有 94% 的原因**：写盘是异步的（`store_cache_worker_sync`）。B2 紧跟在 B1 之后发出，
B1 末尾约 3 个块（776 tokens）尚未落盘。等 B3 完成后回看，B4 就到了 99.9%。
**所以「刚跑完长请求立刻发短请求」会有轻微损失，等几秒即可恢复。**

#### 对编码 agent 的实际意义

| 场景 | 收益 |
|---|---|
| 同一 session 反复编辑同一批文件 | 改 suffix 不影响前缀，全程命中 |
| 换提问方式 | 直接复用，开销接近 0 |
| 切换 `context_window` 配置 | 无需重跑预填充 |
| 重启服务 | 无需重跑预填充（SSD 持久化） |

这也是为什么前面强调「SSD 缓存是刚需，收益（260×）远大于解码速度本身」——
对 agent 场景而言，**预填充成本被缓存消掉了，解码速度才是主要瓶颈**。

### 八、排查：「context_window 设很大时，短提示词也很慢」

这是实际使用中反复出现的现象。逐层排查后确认：**`context_window` 设置本身零影响**，
慢的原因另有三条，且都与「用户提示词长度」无关。

#### 排查一：`context_window` 对短提示词无任何影响

固定同一个 20-token 提示词，只改 `context_window` 并重启服务：

| context_window | TTFT | 解码 |
|---:|---:|---:|
| 4,096 | 0.18 s | 48.4 tok/s |
| 8,192 | 0.17 s | 48.2 tok/s |
| 16,384 | 0.17 s | 48.1 tok/s |
| 32,768 | 0.17 s | 47.9 tok/s |
| 65,536 | 0.17 s | 48.5 tok/s |
| 131,072 | 0.17 s | 47.8 tok/s |
| 262,144 | 0.17 s | 47.7 tok/s |

**完全一致。** 原因：oMLX 用分页缓存（`allocate_blocks_for_tokens`，`paged_cache.py:1434`）
按实际 token 数增长块，**不做全窗口预分配**。

> ⚠️ 排查中一度观测到 32,768 档慢至 TTFT 0.77s / 15 tok/s，加长重启间隔后无法复现。
> 这是**上一实例内存未释放导致的瞬态假象**，不是配置效应。教训：对比不同配置时
> 必须留足重启间隔并取中位数，单次测量不可信。

#### 排查二：「用户提示词短」≠「实际 prompt 短」  ← 本节由此展开为排查三、四

编码 agent 发出的请求里，用户可见的那句话可能只有几个字，但实际 prompt 是：

```
system prompt + 工具定义 + 全部对话历史 + 工具返回的文件内容 + 用户本轮消息
```

实测（用户消息固定为 7 个字符 `"改一下这个函数"`，只改对话历史）：

| 对话历史 | 实际 prompt | 缓存命中 | TTFT（冷） | TTFT（热） |
|---|---:|---:|---:|---:|
| 2K 字符 | 1,382 | — | 5.5 s | 5.5 s |
| 20K 字符 | 13,473 | 94% | 47.0 s | **0.5 s** |
| 100K 字符 | 67,444 | 99.9% | 260.7 s | **1.5 s** |

**用户的输入始终是 7 个字符，实际 prompt 却可达 6.7 万 token。**

#### 排查三（核心）：缓存命中率高也救不了——预填充吞吐随上下文退化

模拟 agent 反复读取同一批文件（每轮新增约 40K token）：

| 总 prompt | 缓存命中 | 需新算 token | TTFT | **实际 prefill 吞吐** |
|---:|---:|---:|---:|---:|
| 40,532 | 0 | 40,532 | 161.0 s | **251.8 tok/s** |
| 81,082 | 50.0% | 40,557 | 225.6 s | **179.8 tok/s** |
| 121,483 | 66.7% | 40,408 | 286.8 s | **140.9 tok/s** |
| 162,040 | 75.0% | 40,564 | 350.4 s | **115.8 tok/s** |
| 202,496 | 80.0% | 40,463 | 418.3 s | **96.7 tok/s** |
| 243,047 | 83.3% | 40,558 | 500.2 s | **81.1 tok/s** |

**每轮需要新算的 token 数恒定在 40.5K，但吞吐从 251.8 掉到 81.1 tok/s（3.1×）**，
TTFT 因此从 161 s 涨到 500 s。

这是注意力机制的固有二次成本：预填充阶段每个新 token 都要 attend **全部已有 token**，
总计算量约为 `新 token 数 × 已有上下文长度`。已有上下文越长，同样的增量越贵。

**不是 I/O 问题**：同期 `prefix_cache_reconstruct` 仅 13.1 s，占 TTFT 500 s 的 2.6%，
SSD 缓存重建远不是瓶颈。

#### 排查四：链式缓存会失效，但 opencode 实际几乎不触发

链式哈希决定了「历史中部一旦变化，该点之后全部作废」。但这是**机制**，不是**实际发生的事**。
两者必须分开验证——本节先证明机制，再用本机 18,290 条真实消息验证实际触发情况。

##### 机制验证（人工构造插入）

41K 上下文中插入 800 字符（≈550 token，占 1.3%），只改插入位置：

| 操作 | prompt | 命中 | 命中率 |
|---|---:|---:|---:|
| 基线（重复发送） | 40,641 | 40,634 | **100.0%** |
| 插入位置 1/11（最靠前） | 41,191 | 0 | **0.0%** |
| 插入位置 5/11（中部） | 41,191 | 12,288 | **29.8%** |
| 插入位置 11/11（最末尾） | 41,191 | 40,634 | **98.6%** |
| 纯末尾追加 | 40,649 | 40,634 | **100.0%** |

插入越靠前损失越大——这是正确性保证，不是缺陷。

##### 实际验证：opencode 不在历史中部插入

对本机 opencode 数据库（`~/.local/share/opencode/opencode.db`，18,290 条 assistant 消息）
中记录的 `tokens.cache.read` 序列做窗口分析：

| 现象 | 次数 |
|---|---:|
| 缓存增长 | 17,582 |
| 持平 | 90 |
| 缓存下降 | 350 |

初看有 350 次下降，但按 `(session_id, agent)` 分区后进一步核查，**全部 350 次都发生在
agent 切换的边界上**：

| 分区口径 | 样本数 | 缓存下降 |
|---|---:|---:|
| 按 `(session_id, agent)` 分区 | 18,290 | 350 |
| **按 `session_id` 分区、只看同一 agent 内** | **18,000** | **0** |

**同一 agent 内部，18,000 个样本中缓存下降次数为 0。**

原因：`build` / `explore` / `general` 等 agent 交替出现时，各自维护独立消息链。
上一条是 `build`（`cache.read=503,518`）、下一条是 `explore`（`cache.read=128`），
这不是「缓存被破坏」，而是**换了一条链**。按 agent 分区后伪信号消失。

**结论：opencode 的 tool 结果一律追加到历史末尾，读写交替不会破坏前缀缓存。**

> ⚠️ 方法论修正：本文早期版本曾据人工插入实验推断「agent 编辑文件会导致命中率从 83% 掉到
> 64.8%」。**该推断错误**——真实机制下编辑结果是追加而非插入。上述 18,290 条实证推翻了它。
> 人工构造实验只能证明机制存在，不能证明实际发生。

##### agent 场景下真正会失效的情况

| 场景 | 是否失效 | 说明 |
|---|---|---|
| 读写文件交替 | ✅ **不失效** | 结果追加到末尾，18,000 样本实证 |
| 连续同类任务 | ✅ 不失效 | 同上 |
| **启动 subagent** | ❌ 失效 | 独立消息链，全量冷启动 |
| `/compact` | ❌ 失效 | 压缩重建消息链 |
| resume / 分支 | ❌ 失效 | 从历史点分出新链 |
| 手动编辑历史消息 | ❌ 失效 | 位置靠前时损失最大（见上表） |

**subagent 是最主要来源**：每个子代理起步就是全新上下文，父链再长也帮不上。

#### 结论与对策

| 现象 | 真实原因 | 对策 |
|---|---|---|
| 窗口设很大但短提示词也慢 | **与 context_window 无关**，是实际 prompt 大 | 见下 |
| 缓存命中 80%+ 仍然慢 | 新算的 40K token 在长上下文中吞吐仅 81 tok/s | 控制会话总长度 |
| 缓存突然清零 | subagent / compact / resume 换链 | 预期行为，非故障 |

**实操建议**：

1. **定期 `/compact` 或新开会话** —— 对话历史是 prompt 的主要膨胀源
2. **不要在同一 session 里反复塞入大文件全文** —— 分批读、按需引用
3. **subagent 的冷启动不可避免** —— 上下文继承在 agent 架构上就不存在，别指望缓存兜底
4. **`context_window` 按需设置即可** —— 设很大不会拖慢短请求，也不占额外内存
5. **读写交替可以放心用** —— 这是常见误解，实测不损失缓存

### 九、踩坑：解码速度会掉到 1/4

首次测 128K 时解码只有 7.45 tok/s，比干净值低 4 倍。排查确认是资源争抢，
两种触发条件都已复现：

| 条件 | 解码 |
|---|---:|
| 干净（独占机器） | 30–34 tok/s |
| 同时跑 StartLux 判别模型（占 ~20–29GB） | 8.4 tok/s |
| 服务端累积大量请求后（KV 碎片化 / memory guard 压力） | 7.8–8.9 tok/s |

**结论：跑 128K 长上下文必须独占机器。** 重启 oMLX 或停掉其他模型即可恢复。

### 十、工具链障碍：Metal 编译器不在 CLT 里

编译 oMLX 的 5 个自定义内核需要 `metal`（.metal → .air → .metallib 三段编译），
而 **Apple Command Line Tools 结构上不含 Metal 编译器**：

```
$ xcode-select -p
/Library/Developer/CommandLineTools
$ ls /Library/Developer/CommandLineTools/usr/bin/ | grep metal
（空）
```

`sudo xcode-select --install` 只会提示 "already installed"，必须装完整 Xcode。
Xcode 27 还把 Metal 编译器拆成独立组件，需额外下载：

```bash
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -downloadComponent MetalToolchain
```

装完 5 个内核编译全部成功，产出 6 个 metallib（含经典版与 NAX 版）。
ANE 可用性验证：`qwen35_ane_available() -> True`。

顺带修正一个认知：社区推荐的预编译 ANE 内核包 ABI 锁定
`omlx 0.6.1 / MLX 0.32.0 / nanobind 2.13.0`，与本机的 0.7.1 / 0.32.3 / 3.1.0 均不匹配，
无法直接复用，只能源码编译。

### 十一、网络：HF 直连阻断

`huggingface.co` 与 `github.com` 在本机均直连超时。可用路径：

- 模型下载：`https://hf-mirror.com` 镜像 + `HF_HUB_DISABLE_XET=1`
  （Xet CAS 后端与镜像不兼容，会返回 401）
- Git 依赖：先 `git clone` 到本地再 checkout 指定 commit，不要让 pip 自己去 clone

---

## Conclusion

**最终方案**：oMLX 0.7.1 + `Jundot/Qwen3.8-27B-oQ4e-mtp` + MTP k=3 + ANE prefill（`dual_ane=false`），
统一脚本托管在 `/Volumes/data/llm-servers/`。

**选择理由**：

- 短上下文代码生成 74.4 tok/s，是 LM Studio 同模型 6bit 的 **3.6×**
- 唯一能启用模型自带 MTP 投机解码的开源运行时
- 分页 SSD KV 缓存把 agent 场景最痛的重复前缀 TTFT 打到 3 秒级
- 提供 Anthropic 兼容端点，可直接接 Claude Code

**被否决的方案**：

| 方案 | 否决理由 |
|---|---|
| LM Studio 作为主运行时 | MLX 引擎无法对 batched 模型启用 MTP（[issue #1519](https://github.com/lmstudio-ai/lmstudio-bug-tracker/issues/1519)），浪费模型 2× 能力。**保留为兜底** |
| vllm-metal | 实测 13.2 tok/s，比 oMLX 慢 5.6×，且无 MTP 无 ANE |
| 6bit / 8bit 量化 | 6bit 实测 20.7 tok/s（LM Studio）；oMLX 上 4bit oQ4e 更快且体积小 27% |
| drowzeys 预编译 ANE 内核 | ABI 锁定 omlx 0.6.1 / MLX 0.32.0 / nanobind 2.13.0，本机版本均不匹配 |
| Dual-ANE | 需 M3 Ultra 双物理 ANE，M4 Max 单颗 |

**实践建议**：

1. 交互式编码用 32K 以内（56+ tok/s，首字 2 分钟内）
2. 128K 仅用于一次性长文档分析（首字需 13 分钟）
3. SSD 缓存是刚需，收益（260×）远大于解码速度本身
4. 跑长上下文前先 `startlux.sh stop`，避免内存挤占
5. **判别场景默认用 27B**：0.8B 快 16 倍但概率全部挤在 0.5 附近，无法做阈值/排序；
   仅当不需要概率、只要布尔标签且输入极短时才考虑 0.8B
6. **StartLux 长输入必须 `STARTLUX_PARALLEL=1`**：`-c` 是所有 slot 共享的，
   `parallel=4` 会把每 slot 压到 4096，超限会触发 Metal 断言崩溃
7. **StartLux 权重是 CC BY-NC 4.0，不能商用**。若要商用需向 StartLux Labs 单独授权，
   或改用 Apache-2.0 的 Qwen3.8-27B 自行微调判别头

**后续工作**：部署细节与启停脚本见 `/Volumes/data/llm-servers/README.md`（项目外）。

---

## Open Questions

- [ ] oMLX 升级到 1.0 stable 后，`qwen35_prefill` 的自定义内核循环导入问题是否已修？
      若已修则不必再手工编译（README 中提到 0.6.3rc2 存在此问题）
- [ ] 5 个自定义内核中只有 `qwen35_prefill` 与当前模型相关，
      `bonsai` / `glm_moe_dsa` / `minimax_m3` 对应的模型尚在本机可运行范围内待验证
- [ ] 128K 下解码能否通过 KV cache 量化（TurboQuant 4-bit）进一步提速，
      官方 benchmark 显示 128K 显存 34.8GB，但本机内存充裕，未验证其速度收益
- [ ] StartLux 自建 20 例测试集样本量偏小，且未覆盖 score 题型与 wide_choice（>26 选项）
      分组路径。需要更大标注集才能给出可信的准确率对比
- [ ] 判别模型延迟由输入长度线性决定（240 tok/s 等效预填充），低于 Qwen3.8-27B 的
      163–177 tok/s 但绝对值接近。是否存在比 27B Q8_0 更快的量化（如 Q4_K_M，16.55GB）
      在保持概率校准的前提下提速，值得实测
- [ ] 前缀缓存的失效边界只验证了「改 context_window」与「换 suffix」两条。
      `turboquant_kv_bits` 变更、`block_size` 变更、跨模型共享 SSD 目录等路径未实测，
      这些理论上会失效但未在本机复现
- [ ] SSD 缓存目录上限 200GB，本机实测 128K 单条即产生约 14GB。
      多会话累积后 LRU 淘汰策略的实际命中率衰减曲线未测量
- [ ] 预填充吞吐随上下文退化（243K 时仅 81 tok/s，是 40K 时的 1/3）是注意力二次成本的
      固有结果，但 `specprefill_enabled`（oMLX 用 Qwen3.5-0.8B 做草稿的预填充投机）
      是否能缓解长上下文的增量预填充成本，本机未验证。该配置在本机 0.6.x 基准中
      曾与 MTP 组合使用，但收益未单独归因

---

## References

### 源码锚点（本机 `/Volumes/data/llm-servers/src/omlx/`）

| 位置 | 内容 |
|---|---|
| `omlx/cache/prefix_cache.py:84` 附近 `compute_block_hash` | 块哈希链式构造（模型隔离 + 父哈希 + token + extra_keys） |
| `omlx/cache/prefix_cache.py:237` `BlockAwarePrefixCache` | 块级前缀共享，`block_size=256` |
| `omlx/cache/prefix_cache.py:302` `_prefix_index` | `hash → (prefix_len, block_ids, num_blocks)` 索引 |
| `omlx/cache/paged_ssd_cache.py:340` `cache_signature_for` | SSD 缓存兼容性签名（**不含 context_window**） |
| `omlx/cache/paged_ssd_cache.py:4571` `invalidate_stale_layer_signature` | 层签名失配时的索引清理 |
| `omlx/cache/`（约 19,600 行） | 前缀缓存 + 分页 SSD 缓存完整实现 |

### 外部资料

- 社区实测配方（M4 Max 128GB 同款机器）：<https://github.com/Weschera/Qwen3.8-27B-oMLX-MTP-Mac>
- oMLX 引擎：<https://omlx.ai> · <https://github.com/jundot/omlx>
- 编码模型权重：`Jundot/Qwen3.8-27B-oQ4e-mtp`（Apache-2.0，Qwen3.8-27B 的 stock 转换）
- 判别模型权重：`startlux-models/StartLux-Decision-v1-27B-Q8_0-GGUF`（**CC BY-NC 4.0**，
  基座 Qwen3.5-27B，架构 `qwen3_5`）
- LM Studio MTP bug：<https://github.com/lmstudio-ai/lmstudio-bug-tracker/issues/1519>
- 运行时版本：oMLX 0.7.1.dev1 / MLX 0.32.3 / nanobind 3.1.0 /
  llama.cpp 0.6.0 (build 11429) / Xcode 27.0
- 部署文档（项目外）：`/Volumes/data/llm-servers/README.md`