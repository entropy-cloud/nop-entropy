# PandasAI 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 PandasAI 的 AI 数据分析模式，为 nop-metadata 的 AI 集成提供参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

PandasAI 是一个 AI 驱动的数据分析工具，支持自然语言查询数据。它将自然语言转换为 SQL/Pandas 代码，执行查询并返回结果。本文档分析其 AI 与元数据集成模式，为 nop-metadata 提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                    PandasAI                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ SmartDataframe│  │ Agent       │  │ LLM       │ │
│  │ (智能数据框) │  │ (代理)      │  │ (大模型)  │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Code Generator (代码生成)                 │ │
│  │  SQL, Pandas, Matplotlib                       │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   Data Sources   │ │  Metadata │ │  LLM APIs    │
│  (Pandas/SQL/    │ │  Store    │ │  (OpenAI/    │
│   Database)      │ │           │ │   Gemini)    │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 2. 核心概念

- **SmartDataframe**: 增强的 DataFrame，支持自然语言查询
- **Agent**: AI 代理，协调查询执行
- **LLM**: 大语言模型，生成查询代码
- **Code Generator**: 代码生成器，将自然语言转换为代码

## 核心设计模式

### 模式 1：SmartDataframe 元数据

**关键文件:**
- `pandasai/smart_dataframe.py`

**SmartDataframe 结构:**

```python
class SmartDataFrame:
    """智能数据框"""
    
    def __init__(self, df, config=None):
        self.df = df
        self.config = config or Config()
        
        # 元数据
        self.metadata = self._extract_metadata()
        
        # AI 代理
        self.agent = Agent(
            data=df,
            metadata=self.metadata,
            config=self.config
        )
    
    def _extract_metadata(self):
        """提取数据元数据"""
        return {
            "columns": [
                {
                    "name": col,
                    "dtype": str(self.df[col].dtype),
                    "sample_values": self.df[col].head(5).tolist(),
                    "null_count": int(self.df[col].isnull().sum()),
                    "unique_count": int(self.df[col].nunique())
                }
                for col in self.df.columns
            ],
            "shape": self.df.shape,
            "memory_usage": self.df.memory_usage(deep=True).sum()
        }
    
    def chat(self, query):
        """自然语言查询"""
        return self.agent.chat(query)
```

**设计优势:**
- **自动元数据提取**: 从 DataFrame 自动提取元数据
- **丰富元数据**: 列类型、样本值、空值计数、唯一值计数
- **AI 代理集成**: 元数据直接传递给 AI 代理

---

### 模式 2：Agent 代理模式

**关键文件:**
- `pandasai/agent/`

**Agent 结构:**

```python
class Agent:
    """AI 代理"""
    
    def __init__(self, data, metadata, config):
        self.data = data
        self.metadata = metadata
        self.config = config
        
        # LLM
        self.llm = self._init_llm()
        
        # 代码生成器
        self.code_generator = CodeGenerator(
            metadata=metadata,
            llm=self.llm
        )
        
        # 执行器
        self.executor = CodeExecutor(data=data)
    
    def chat(self, query):
        """自然语言查询"""
        # 1. 生成代码
        code = self.code_generator.generate(query)
        
        # 2. 执行代码
        result = self.executor.execute(code)
        
        # 3. 返回结果
        return result
    
    def _init_llm(self):
        """初始化 LLM"""
        if self.config.llm_provider == "openai":
            return OpenAILLM(api_key=self.config.api_key)
        elif self.config.llm_provider == "gemini":
            return GeminiLLM(api_key=self.config.api_key)
        else:
            return MockLLM()
```

**Agent 工作流程:**

```python
class Agent:
    
    def chat(self, query):
        # 1. 构建提示词
        prompt = self._build_prompt(query)
        
        # 2. 调用 LLM 生成代码
        response = self.llm.generate(prompt)
        
        # 3. 解析代码
        code = self._parse_code(response)
        
        # 4. 验证代码
        if not self._validate_code(code):
            return "无法生成有效代码"
        
        # 5. 执行代码
        try:
            result = self.executor.execute(code)
            return result
        except Exception as e:
            return f"执行错误: {str(e)}"
    
    def _build_prompt(self, query):
        """构建提示词"""
        return f"""
你是一个数据分析专家。根据以下数据元数据，生成 Python 代码来回答用户问题。

数据元数据:
{json.dumps(self.metadata, indent=2)}

用户问题: {query}

请生成 Python 代码来回答这个问题。代码应该使用 pandas 库。
"""
```

**设计优势:**
- **元数据驱动**: 元数据直接传递给 LLM
- **代码生成**: 自动生成查询代码
- **错误处理**: 完整的错误处理机制

---

### 模式 3：Code Generator 代码生成

**关键文件:**
- `pandasai/agent/code_generator.py`

**代码生成流程:**

```python
class CodeGenerator:
    """代码生成器"""
    
    def generate(self, query):
        """生成查询代码"""
        
        # 1. 构建提示词
        prompt = self._build_prompt(query)
        
        # 2. 调用 LLM
        response = self.llm.generate(prompt)
        
        # 3. 解析代码
        code = self._parse_code(response)
        
        # 4. 后处理
        code = self._post_process(code)
        
        return code
    
    def _build_prompt(self, query):
        """构建提示词"""
        return f"""
你是一个数据分析专家。根据以下数据元数据，生成 Python 代码来回答用户问题。

数据元数据:
- 数据形状: {self.metadata['shape']}
- 列信息:
{self._format_columns()}

用户问题: {query}

要求:
1. 使用 pandas 库
2. 代码应该返回可视化结果或数据
3. 代码应该处理可能的错误
"""
    
    def _format_columns(self):
        """格式化列信息"""
        lines = []
        for col in self.metadata['columns']:
            lines.append(f"  - {col['name']}: {col['dtype']}")
            lines.append(f"    样本值: {col['sample_values']}")
            lines.append(f"    空值数: {col['null_count']}")
            lines.append(f"    唯一值数: {col['unique_count']}")
        return '\n'.join(lines)
```

**生成的代码示例:**

```python
# LLM 生成的代码
import pandas as pd
import matplotlib.pyplot as plt

# 按状态统计订单数量
status_counts = df['status'].value_counts()

# 绘制饼图
plt.figure(figsize=(8, 6))
status_counts.plot(kind='pie', autopct='%1.1f%%')
plt.title('订单状态分布')
plt.ylabel('')

# 返回结果
plt.tight_layout()
plt.savefig('status_distribution.png')
plt.show()
```

**设计优势:**
- **元数据增强**: 元数据直接注入提示词
- **代码验证**: 验证生成的代码
- **后处理**: 自动添加数据源引用

---

### 模式 4：Code Executor 代码执行

**关键文件:**
- `pandasai/agent/code_executor.py`

**代码执行器:**

```python
class CodeExecutor:
    """代码执行器"""
    
    def __init__(self, data):
        self.data = data
        self.timeout = 30  # 超时时间（秒）
    
    def execute(self, code):
        """执行代码"""
        
        # 1. 安全检查
        if not self._is_safe(code):
            raise SecurityError("不安全的代码")
        
        # 2. 准备执行环境
        context = self._prepare_context()
        
        # 3. 执行代码
        try:
            exec(code, context)
        except Exception as e:
            raise ExecutionError(f"执行错误: {str(e)}")
        
        # 4. 提取结果
        result = self._extract_result(context)
        
        return result
    
    def _prepare_context(self):
        """准备执行环境"""
        return {
            'pd': pd,
            'plt': plt,
            'df': self.data,
            'np': np
        }
    
    def _is_safe(self, code):
        """安全检查"""
        dangerous_patterns = [
            'import os',
            'import sys',
            'exec(',
            'eval(',
            '__import__',
            'open(',
            'write('
        ]
        
        for pattern in dangerous_patterns:
            if pattern in code:
                return False
        
        return True
    
    def _extract_result(self, context):
        """提取执行结果"""
        # 检查是否有图表
        if 'plt' in context and plt.get_fignums():
            return PlotResult(plt.gcf())
        
        # 检查是否有 DataFrame
        if 'result' in context and isinstance(context['result'], pd.DataFrame):
            return DataFrameResult(context['result'])
        
        # 检查是否有其他结果
        if 'result' in context:
            return Result(context['result'])
        
        return None
```

**执行结果类型:**

```python
class Result:
    """执行结果基类"""
    pass

class DataFrameResult(Result):
    """DataFrame 结果"""
    def __init__(self, df):
        self.df = df
    
    def to_html(self):
        return self.df.to_html()
    
    def to_markdown(self):
        return self.df.to_markdown()

class PlotResult(Result):
    """图表结果"""
    def __init__(self, figure):
        self.figure = figure
    
    def to_image(self):
        buf = io.BytesIO()
        self.figure.savefig(buf, format='png')
        buf.seek(0)
        return buf
```

**设计优势:**
- **安全执行**: 代码安全检查
- **超时控制**: 执行超时限制
- **多类型结果**: 支持 DataFrame、图表等结果

---

### 模式 5：LLM 集成

**关键文件:**
- `pandasai/llm/`

**LLM 接口:**

```python
class BaseLLM:
    """LLM 基类"""
    
    def generate(self, prompt):
        raise NotImplementedError

class OpenAILLM(BaseLLM):
    """OpenAI LLM"""
    
    def __init__(self, api_key, model="gpt-4"):
        self.client = openai.OpenAI(api_key=api_key)
        self.model = model
    
    def generate(self, prompt):
        response = self.client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": "你是一个数据分析专家。"},
                {"role": "user", "content": prompt}
            ],
            temperature=0
        )
        return response.choices[0].message.content

class GeminiLLM(BaseLLM):
    """Gemini LLM"""
    
    def __init__(self, api_key):
        self.model = genai.GenerativeModel('gemini-pro')
        genai.configure(api_key=api_key)
    
    def generate(self, prompt):
        response = self.model.generate_content(prompt)
        return response.text
```

**LLM 配置:**

```python
config = Config(
    llm_provider="openai",  # 或 "gemini", "mock"
    api_key="your-api-key",
    model="gpt-4",
    temperature=0,
    max_tokens=1000
)
```

**设计优势:**
- **多 LLM 支持**: 支持多种 LLM 提供商
- **配置灵活**: 可配置模型参数
- **Mock 模式**: 支持测试和开发

---

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **SmartDataframe 元数据**: 自动提取数据元数据
2. **Agent 代理模式**: AI 代理协调查询执行
3. **元数据驱动提示词**: 元数据直接注入 LLM 提示词
4. **代码生成模式**: 自然语言到代码的转换
5. **安全执行模式**: 代码安全检查和超时控制

### 与 nop-metadata 的对比

| 能力 | PandasAI | nop-metadata |
|------|---------|-------------|
| 元数据提取 | DataFrame 自动提取 | ORM 模型导入 |
| AI 查询 | 自然语言 → 代码 → 执行 | 扩展支持 |
| LLM 集成 | OpenAI/Gemini | 扩展支持 |
| 代码执行 | 安全执行环境 | 扩展支持 |

### nop-metadata 可复用的模式

1. **元数据增强**: 将 MetaEntity/MetaField 信息注入 LLM 提示词
2. **Agent 模式**: 创建 AI 代理查询元数据
3. **自然语言查询**: 支持自然语言查询元数据
4. **代码生成**: 自然语言到 SQL/EQL 的转换

## Open Questions

- [ ] nop-metadata 是否需要支持自然语言查询元数据？
- [ ] LLM 集成是否需要作为核心功能？
- [ ] 代码执行是否需要安全沙箱？

## References

- [PandasAI GitHub](https://github.com/sinaptik-ai/pandas-ai)
- [PandasAI 文档](https://docs.pandas-ai.com/)
- 源码: `pandasai/smart_dataframe.py`
- 源码: `pandasai/agent/`
