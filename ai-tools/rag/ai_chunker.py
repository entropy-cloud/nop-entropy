import requests
import json
import re
from typing import List, Dict
from pathlib import Path
from datetime import datetime


class RoughChunker:
    def __init__(self, max_size: int = 8000):
        self.max_size = max_size
    
    def chunk(self, content: str) -> List[str]:
        chunks = []
        current_chunk = ""
        in_code_block = False
        
        lines = content.split('\n')
        i = 0
        
        while i < len(lines):
            line = lines[i]
            
            if line.startswith('```'):
                in_code_block = not in_code_block
                current_chunk += line + '\n'
                i += 1
                continue
            
            if line.startswith('## '):
                if current_chunk and len(current_chunk) > 1000:
                    chunks.append(current_chunk.strip())
                    current_chunk = line + '\n'
                else:
                    current_chunk += line + '\n'
            elif line.startswith('### '):
                if len(current_chunk) > 6000:
                    chunks.append(current_chunk.strip())
                    current_chunk = line + '\n'
                else:
                    current_chunk += line + '\n'
            else:
                current_chunk += line + '\n'
            
            if len(current_chunk) >= self.max_size:
                chunks.append(current_chunk.strip())
                current_chunk = ""
            
            i += 1
        
        if current_chunk:
            chunks.append(current_chunk.strip())
        
        return chunks


class AIAnalyzer:
    def __init__(self, model: str = "glm-4.7-flash", base_url: str = "http://localhost:11434", timeout: int = 600, debug_dir: str = None):
        self.model = model
        self.base_url = base_url
        self.api_url = f"{base_url}/api/generate"
        self.timeout = timeout
        self.debug_dir = debug_dir
        self.call_count = 0
        
        if self.debug_dir:
            self.debug_path = Path(debug_dir)
            self.debug_path.mkdir(parents=True, exist_ok=True)
            self.prompt_file = self.debug_path / f"ai_prompts_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
            self.response_file = self.debug_path / f"ai_responses_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
        else:
            self.prompt_file = None
            self.response_file = None
    
    def analyze_chunk(self, chunk: str, doc_type: str = "unknown") -> Dict:
        self.call_count += 1
        call_id = self.call_count
        
        prompt = self._build_prompt(chunk, doc_type)
        
        if self.prompt_file:
            with open(self.prompt_file, 'a', encoding='utf-8') as f:
                f.write(f"\n{'='*80}\n")
                f.write(f"调用 #{call_id} - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                f.write(f"文档类型: {doc_type}\n")
                f.write(f"内容长度: {len(chunk)} 字符\n")
                f.write(f"{'='*80}\n\n")
                f.write(prompt)
                f.write(f"\n{'='*80}\n\n")
        
        try:
            response = requests.post(
                self.api_url,
                json={
                    "model": self.model,
                    "prompt": prompt,
                    "stream": False,
                    "temperature": 0.3,
                    "max_tokens": 8192
                },
                timeout=self.timeout
            )
            
            if response.status_code != 200:
                raise Exception(f"AI分析失败: {response.text}")
            
            result = response.json()
            analysis = result.get("response", "")
            
            if self.response_file:
                with open(self.response_file, 'a', encoding='utf-8') as f:
                    f.write(f"\n{'='*80}\n")
                    f.write(f"调用 #{call_id} - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                    f.write(f"状态码: {response.status_code}\n")
                    f.write(f"{'='*80}\n\n")
                    f.write(analysis)
                    f.write(f"\n{'='*80}\n\n")
            
            return self._parse_analysis(analysis)
        except Exception as e:
            print(f"  ❌ AI分析失败: {e}")
            
            if self.response_file:
                with open(self.response_file, 'a', encoding='utf-8') as f:
                    f.write(f"\n{'='*80}\n")
                    f.write(f"调用 #{call_id} - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
                    f.write(f"错误: {e}\n")
                    f.write(f"{'='*80}\n\n")
            
            return {
                "split_points": [],
                "chunks": [],
                "analysis": "解析失败"
            }
    
    def _build_prompt(self, chunk: str, doc_type: str) -> str:
        lines = chunk.split('\n')
        numbered_lines = []
        for i, line in enumerate(lines, 1):
            numbered_lines.append(f"{i}: {line}")
        numbered_chunk = '\n'.join(numbered_lines)
        
        return f"""你是一个专业的文档切分专家。你的任务是将给定的文档内容切分成多个语义完整的chunks。

## 切分要求

1. **代码块完整性**：确保每个代码块完整，不被切断
2. **语义单元完整**：保持方法、类、配置文件的完整性
3. **上下文保留**：保留足够的上下文信息，便于理解
4. **大小适中**：单个chunk在3000字符以内

## 文档类型

{doc_type}

## 切分策略

- 教程类文档：按步骤切分
- 示例类文档：按代码块 + 方法边界切分
- API参考文档：按概念切分
- 实战项目文档：按模块 + 代码块切分

## 待切分内容

注意：每行前面已经标注了行号（格式：行号: 内容），请在输出切分建议时使用这些行号。

```
{numbered_chunk}
```

## 输出格式

请以JSON格式输出切分建议，格式如下：

```json
{{
  
  "chunks": [
    {{
      "start_line": 1,
      "end_line": 10,
    }}
  ],
  "analysis": "切分说明..."
}}
```

请确保输出的是有效的JSON格式，并且line_number使用上面标注的行号。"""
    
    def _parse_analysis(self, analysis: str) -> Dict:
        try:
            json_start = analysis.find('{')
            json_end = analysis.rfind('}') + 1
            
            if json_start == -1 or json_end == 0:
                raise Exception("无法找到JSON格式的输出")
            
            json_str = analysis[json_start:json_end]
            return json.loads(json_str)
        except Exception as e:
            print(f"  ⚠️ 解析AI分析结果失败: {e}")
            print(f"  原始输出: {analysis[:200]}...")
            return {
                "split_points": [],
                "chunks": [],
                "analysis": "解析失败"
            }


class FineChunker:
    def __init__(self, overlap_size: int = 150):
        self.overlap_size = overlap_size
    
    def chunk(self, content: str, split_points: List[Dict]) -> List[str]:
        if not split_points:
            return [content]
        
        lines = content.split('\n')
        chunks = []
        start_line = 0
        
        for split_point in split_points:
            end_line = split_point['line_number']
            
            if end_line > start_line:
                chunk = '\n'.join(lines[start_line:end_line])
                chunks.append(chunk.strip())
                start_line = end_line
        
        if start_line < len(lines):
            chunk = '\n'.join(lines[start_line:])
            chunks.append(chunk.strip())
        
        chunks = self._add_overlap(chunks)
        chunks = self._enforce_size(chunks)
        
        return chunks
    
    def _add_overlap(self, chunks: List[str]) -> List[str]:
        if len(chunks) <= 1:
            return chunks
        
        result = [chunks[0]]
        
        for i in range(1, len(chunks)):
            overlap = self._select_overlap(chunks[i-1])
            result.append(overlap + '\n' + chunks[i])
        
        return result
    
    def _select_overlap(self, chunk: str) -> str:
        if len(chunk) <= self.overlap_size:
            return chunk
        
        lines = chunk.split('\n')
        overlap_lines = []
        overlap_length = 0
        
        for line in reversed(lines):
            if overlap_length + len(line) > self.overlap_size:
                break
            overlap_lines.insert(0, line)
            overlap_length += len(line)
        
        return '\n'.join(overlap_lines)
    
    def _enforce_size(self, chunks: List[str], min_size: int = 200, max_size: int = 3000) -> List[str]:
        result = []
        for chunk in chunks:
            if len(chunk) < min_size and result:
                result[-1] += '\n' + chunk
            elif len(chunk) > max_size:
                result.extend(self._split_large_chunk(chunk, max_size))
            else:
                result.append(chunk)
        return result
    
    def _split_large_chunk(self, chunk: str, max_size: int) -> List[str]:
        chunks = []
        current_chunk = ""
        
        for line in chunk.split('\n'):
            if len(current_chunk) + len(line) > max_size:
                if current_chunk:
                    chunks.append(current_chunk.strip())
                current_chunk = line + '\n'
            else:
                current_chunk += line + '\n'
        
        if current_chunk:
            chunks.append(current_chunk.strip())
        
        return chunks


class QualityValidator:
    def __init__(self):
        pass
    
    def validate(self, chunks: List[str]) -> Dict:
        metrics = {
            "total_chunks": len(chunks),
            "broken_code_blocks": 0,
            "total_code_blocks": 0,
            "sizes": [],
            "min_size": float('inf'),
            "max_size": 0,
            "avg_size": 0
        }
        
        for chunk in chunks:
            size = len(chunk)
            metrics["sizes"].append(size)
            metrics["min_size"] = min(metrics["min_size"], size)
            metrics["max_size"] = max(metrics["max_size"], size)
            
            code_blocks = chunk.count('```')
            metrics["total_code_blocks"] += code_blocks
            
            if code_blocks % 2 != 0:
                metrics["broken_code_blocks"] += 1
        
        if metrics["sizes"]:
            metrics["avg_size"] = sum(metrics["sizes"]) / len(metrics["sizes"])
        
        metrics["code_block_break_rate"] = (
            metrics["broken_code_blocks"] / metrics["total_chunks"] * 100
            if metrics["total_chunks"] > 0
            else 0
        )
        
        metrics["semantic_completeness"] = (
            100 - metrics["code_block_break_rate"]
        )
        
        return metrics
    
    def generate_report(self, metrics: Dict) -> str:
        report = f"""
## 切分质量报告

### 基本信息
- 总chunks数: {metrics['total_chunks']}
- 平均大小: {metrics['avg_size']:.0f} 字符
- 最小大小: {metrics['min_size']:.0f} 字符
- 最大大小: {metrics['max_size']:.0f} 字符

### 代码块完整性
- 总代码块数: {metrics['total_code_blocks']}
- 被切断的代码块: {metrics['broken_code_blocks']}
- 代码块切断率: {metrics['code_block_break_rate']:.1f}%

### 语义完整性
- 语义完整性: {metrics['semantic_completeness']:.1f}%

### 评价
"""
        if metrics['code_block_break_rate'] == 0:
            report += "✅ 优秀：所有代码块都完整\n"
        elif metrics['code_block_break_rate'] < 5:
            report += "✅ 良好：代码块切断率很低\n"
        elif metrics['code_block_break_rate'] < 10:
            report += "⚠️ 一般：代码块切断率较高\n"
        else:
            report += "❌ 较差：代码块切断率过高\n"
        
        return report


class AIChunker:
    def __init__(self, 
                 model: str = "glm-4.7-flash",
                 base_url: str = "http://localhost:11434",
                 rough_max_size: int = 8000,
                 overlap_size: int = 150,
                 timeout: int = 120,
                 debug_dir: str = None):
        self.rough_chunker = RoughChunker(max_size=rough_max_size)
        self.ai_analyzer = AIAnalyzer(model=model, base_url=base_url, timeout=timeout, debug_dir=debug_dir)
        self.fine_chunker = FineChunker(overlap_size=overlap_size)
        self.validator = QualityValidator()
    
    def chunk(self, content: str, doc_type: str = "unknown") -> List[str]:
        print(f"🔍 开始粗切分...")
        rough_chunks = self.rough_chunker.chunk(content)
        print(f"✅ 粗切分完成，生成 {len(rough_chunks)} 个chunks")
        
        final_chunks = []
        
        for i, rough_chunk in enumerate(rough_chunks, 1):
            print(f"🤖 AI分析 chunk {i}/{len(rough_chunks)}...")
            
            analysis = self.ai_analyzer.analyze_chunk(rough_chunk, doc_type)
            
            if analysis.get("chunks"):
                print(f"  ✅ AI建议切分为 {len(analysis['chunks'])} 个chunks")
                for chunk_data in analysis["chunks"]:
                    final_chunks.append(chunk_data["content"])
            else:
                print(f"  ⚠️ AI未提供切分建议，使用原chunk")
                final_chunks.append(rough_chunk)
        
        print(f"✅ 细切分完成，生成 {len(final_chunks)} 个chunks")
        
        print(f"🔍 质量验证...")
        metrics = self.validator.validate(final_chunks)
        report = self.validator.generate_report(metrics)
        print(report)
        
        return final_chunks
    
    def chunk_file(self, input_path: str, output_path: str, doc_type: str = "unknown"):
        with open(input_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        chunks = self.chunk(content, doc_type)
        
        output_file = Path(output_path)
        output_file.parent.mkdir(parents=True, exist_ok=True)
        
        with open(output_path, 'w', encoding='utf-8') as f:
            for i, chunk in enumerate(chunks, 1):
                chunk_data = {
                    "id": i,
                    "content": chunk,
                    "size": len(chunk)
                }
                f.write(json.dumps(chunk_data, ensure_ascii=False) + "\n")
        
        print(f"✅ 结果已保存到 {output_path}")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) < 3:
        print("用法: python ai_chunker.py <输入文件> <输出文件> [文档类型]")
        print("文档类型: tutorial, example, api_reference, project")
        sys.exit(1)
    
    input_path = sys.argv[1]
    output_path = sys.argv[2]
    doc_type = sys.argv[3] if len(sys.argv) > 3 else "unknown"
    
    chunker = AIChunker()
    chunker.chunk_file(input_path, output_path, doc_type)
