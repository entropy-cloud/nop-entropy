# Using the Local Deployment of DeepSeek 8B Model for Long Document Translation

With the release of DeepSeek, AI models have taken their first steps towards democratization. By deploying an 8B model locally, we can achieve some useful functions.

This document briefly introduces the practical deepseek model for translating technical documents and some issues encountered during the translation process.


## Model Selection
Using ollama on a local notebook to run AI models, we explored the following options:

1. `qwen2.5:7b`
   - This model often takes Chinese text and slightly rewrites it before outputting in Chinese without performing actual translation.
   - It frequently adds extra markdown blocks in the output.

2. `deepseek-r1:7b`
   - Similar to `qwen2.5`, this model often outputs Chinese without translation.
   - It sometimes fails to understand further corrections and may respond with irrelevant answers if the temperature is set too high (e.g., above 1).

3. `deepseek-r1:8b`
   - Better than the previous two models, it shares similar issues but generally performs slightly better.


## Environment Preparation
My notebook is a ThinkPad X1 Carbon 2024, equipped with an Intel Arc series GPU for GPU acceleration.

To enable ollama to support GPU, download the latest release from [https://github.com/francisol/ollatel/releases](https://github.com/francisol/ollatel/releases).

The ollamel interface is straightforward, as shown in the attached image ([images/ollamel.png](images/ollamel.png)).

For debugging, use [ChatBox](https://chatboxai.app/zh) for a visual chat interface.

To download the model, run `ollama pull deepseek-r1:8b` in the command line.


### Context Size Adjustment
By default, ollama uses a context length of 2048. For longer prompts, this may cause truncation issues.
- Pass `num_ctx` parameter when calling to avoid this.
- Create a file named `deepseek-8b-8k.txt` with `num_ctx` configurations.

```markdown
FROM deepseek-r1:8b

PARAMETER num_ctx 8192
PARAMETER num_predict -1
```

Execute:
```bash
ollama create deepseek-r1:8b-8k -f deepseek-8b-8k.txt
```

Adjusting the context size increases runtime memory consumption.

