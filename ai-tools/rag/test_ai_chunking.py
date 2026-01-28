#!/usr/bin/env python3
"""
AI切分测试脚本 - 简化版
只测试几个关键文件，快速验证AI切分效果
"""

import json
import time
from pathlib import Path
from datetime import datetime
from ai_chunker import AIChunker


class AIChunkingTest:
    def __init__(self, input_dir: str, output_dir: str, debug_dir: str = None):
        self.input_dir = Path(input_dir)
        self.output_dir = Path(output_dir)
        self.debug_dir = debug_dir
        self.chunker = AIChunker(debug_dir=debug_dir)
        
        self.results = {
            "files": [],
            "summary": {}
        }
    
    def detect_doc_type(self, file_path: Path) -> str:
        path_str = str(file_path).lower()
        
        if "tutorial" in path_str or "quickstart" in path_str or "step" in path_str:
            return "tutorial"
        elif "example" in path_str:
            return "example"
        elif "guide" in path_str or "reference" in path_str or "api" in path_str:
            return "api_reference"
        elif "project" in path_str or "auth" in path_str or "sys" in path_str:
            return "project"
        else:
            return "unknown"
    
    def process_file(self, file_path: Path) -> dict:
        print(f"\n{'='*60}")
        print(f"处理文件: {file_path.relative_to(self.input_dir)}")
        print(f"{'='*60}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        doc_type = self.detect_doc_type(file_path)
        print(f"文档类型: {doc_type}")
        print(f"文档大小: {len(content)} 字符")
        
        start_time = time.time()
        chunks = self.chunker.chunk(content, doc_type)
        elapsed_time = time.time() - start_time
        
        print(f"\n{'-'*60}")
        print("切分统计")
        print(f"{'-'*60}")
        print(f"生成chunks数: {len(chunks)}")
        print(f"处理时间: {elapsed_time:.2f} 秒")
        
        broken_count = 0
        sizes = []
        
        for i, chunk in enumerate(chunks, 1):
            code_blocks = chunk.count('```')
            is_broken = code_blocks % 2 != 0
            
            if is_broken:
                broken_count += 1
            
            sizes.append(len(chunk))
        
        print(f"平均大小: {sum(sizes) / len(sizes):.0f} 字符")
        print(f"最大大小: {max(sizes):.0f} 字符")
        print(f"最小大小: {min(sizes):.0f} 字符")
        print(f"被切断的chunks: {broken_count}")
        print(f"代码块切断率: {(broken_count / len(chunks) * 100) if chunks else 0:.1f}%")
        
        result = {
            "file_path": str(file_path.relative_to(self.input_dir)),
            "doc_type": doc_type,
            "original_size": len(content),
            "chunk_count": len(chunks),
            "time": elapsed_time,
            "chunks": []
        }
        
        for i, chunk in enumerate(chunks, 1):
            chunk_info = {
                "id": i,
                "size": len(chunk),
                "code_blocks": chunk.count('```'),
                "is_broken": chunk.count('```') % 2 != 0
            }
            result["chunks"].append(chunk_info)
        
        return result
    
    def test_sample_files(self, max_files: int = 5):
        print("="*60)
        print("AI切分测试 - docs-for-ai")
        print("="*60)
        print(f"输入目录: {self.input_dir}")
        print(f"输出目录: {self.output_dir}")
        print(f"测试文件数: {max_files}")
        print(f"开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        
        md_files = list(self.input_dir.rglob("*.md"))
        print(f"\n找到 {len(md_files)} 个markdown文件")
        
        sample_files = md_files[:max_files]
        
        for i, md_file in enumerate(sample_files, 1):
            print(f"\n进度: {i}/{len(sample_files)}")
            
            try:
                result = self.process_file(md_file)
                self.results["files"].append(result)
            except Exception as e:
                print(f"❌ 处理失败: {e}")
                import traceback
                traceback.print_exc()
                continue
        
        if self.results["files"]:
            self.results["summary"] = self._generate_summary()
            self._save_results()
            self._print_summary()
    
    def _generate_summary(self) -> dict:
        total_files = len(self.results["files"])
        total_chunks = sum(r["chunk_count"] for r in self.results["files"])
        total_broken = sum(sum(1 for c in r["chunks"] if c["is_broken"]) for r in self.results["files"])
        total_time = sum(r["time"] for r in self.results["files"])
        
        all_sizes = [c["size"] for r in self.results["files"] for c in r["chunks"]]
        
        return {
            "total_files": total_files,
            "total_chunks": total_chunks,
            "avg_chunks_per_file": total_chunks / total_files if total_files > 0 else 0,
            "total_broken": total_broken,
            "broken_rate": (total_broken / total_chunks * 100) if total_chunks > 0 else 0,
            "total_time": total_time,
            "avg_time": total_time / total_files if total_files > 0 else 0,
            "avg_size": sum(all_sizes) / len(all_sizes) if all_sizes else 0,
            "max_size": max(all_sizes) if all_sizes else 0,
            "min_size": min(all_sizes) if all_sizes else 0
        }
    
    def _save_results(self):
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
        results_file = self.output_dir / f"ai_chunking_test_results_{timestamp}.json"
        with open(results_file, 'w', encoding='utf-8') as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        
        print(f"\n✅ 结果已保存到: {results_file}")
        
        summary_file = self.output_dir / f"ai_chunking_test_summary_{timestamp}.md"
        with open(summary_file, 'w', encoding='utf-8') as f:
            f.write(self._generate_markdown_report())
        
        print(f"✅ 报告已保存到: {summary_file}")
    
    def _print_summary(self):
        summary = self.results["summary"]
        
        print("\n" + "="*60)
        print("总体统计")
        print("="*60)
        print(f"总文件数: {summary['total_files']}")
        print(f"总chunks数: {summary['total_chunks']}")
        print(f"平均chunks数: {summary['avg_chunks_per_file']:.1f}")
        
        print("\n" + "-"*60)
        print("代码块完整性")
        print("-"*60)
        print(f"被切断的chunks: {summary['total_broken']}")
        print(f"代码块切断率: {summary['broken_rate']:.1f}%")
        print(f"语义完整性: {100 - summary['broken_rate']:.1f}%")
        
        print("\n" + "-"*60)
        print("Chunk大小")
        print("-"*60)
        print(f"平均大小: {summary['avg_size']:.0f} 字符")
        print(f"最大大小: {summary['max_size']:.0f} 字符")
        print(f"最小大小: {summary['min_size']:.0f} 字符")
        
        print("\n" + "-"*60)
        print("处理时间")
        print("-"*60)
        print(f"总时间: {summary['total_time']:.1f} 秒")
        print(f"平均时间: {summary['avg_time']:.2f} 秒")
    
    def _generate_markdown_report(self) -> str:
        summary = self.results["summary"]
        
        report = f"""# AI切分测试报告

## 基本信息

- **测试时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **输入目录**: {self.input_dir}
- **输出目录**: {self.output_dir}
- **总文件数**: {summary['total_files']}

## 总体统计

### Chunks数量

| 指标 | 数值 |
|------|------|
| 总chunks数 | {summary['total_chunks']} |
| 平均chunks数 | {summary['avg_chunks_per_file']:.1f} |

### 代码块完整性

| 指标 | 数值 |
|------|------|
| 被切断的chunks | {summary['total_broken']} |
| 代码块切断率 | {summary['broken_rate']:.1f}% |
| 语义完整性 | {100 - summary['broken_rate']:.1f}% |

### Chunk大小

| 指标 | 数值 |
|------|------|
| 平均大小 | {summary['avg_size']:.0f} 字符 |
| 最大大小 | {summary['max_size']:.0f} 字符 |
| 最小大小 | {summary['min_size']:.0f} 字符 |

### 处理时间

| 指标 | 数值 |
|------|------|
| 总时间 | {summary['total_time']:.1f} 秒 |
| 平均时间 | {summary['avg_time']:.2f} 秒 |

## 详细结果

"""
        
        for result in self.results["files"]:
            broken_count = sum(1 for c in result["chunks"] if c["is_broken"])
            sizes = [c["size"] for c in result["chunks"]]
            
            report += f"### {result['file_path']}\n\n"
            report += f"- **文档类型**: {result['doc_type']}\n"
            report += f"- **原始大小**: {result['original_size']} 字符\n"
            report += f"- **Chunks数**: {result['chunk_count']}\n"
            report += f"- **处理时间**: {result['time']:.2f} 秒\n"
            report += f"- **被切断的chunks**: {broken_count}\n"
            report += f"- **代码块切断率**: {(broken_count / result['chunk_count'] * 100) if result['chunk_count'] > 0 else 0:.1f}%\n"
            report += f"- **平均大小**: {sum(sizes) / len(sizes):.0f} 字符\n"
            report += f"- **最大大小**: {max(sizes):.0f} 字符\n"
            report += f"- **最小大小**: {min(sizes):.0f} 字符\n\n"
        
        report += "\n## 结论\n\n"
        
        if summary['broken_rate'] == 0:
            report += "✅ **AI切分完美**：所有代码块都完整，没有被切断。\n\n"
        elif summary['broken_rate'] < 5:
            report += "✅ **AI切分优秀**：代码块切断率很低，质量显著优于规则切分。\n\n"
        elif summary['broken_rate'] < 10:
            report += "⚠️ **AI切分良好**：代码块切断率较低，但仍有改进空间。\n\n"
        else:
            report += "❌ **AI切分一般**：代码块切断率较高，需要优化Prompt或模型。\n\n"
        
        report += f"### 与规则切分对比\n\n"
        report += f"规则切分的代码块切断率为 9.2%，AI切分的代码块切断率为 {summary['broken_rate']:.1f}%。\n"
        
        if summary['broken_rate'] < 9.2:
            improvement = 9.2 - summary['broken_rate']
            report += f"✅ AI切分比规则切分改进了 {improvement:.1f}%。\n"
        else:
            report += f"⚠️ AI切分比规则切分差了 {summary['broken_rate'] - 9.2:.1f}%。\n"
        
        return report


if __name__ == "__main__":
    import sys
    
    input_dir = "/Users/abc/nop/nop-entropy/docs-for-ai"
    output_dir = "/Users/abc/ai/UltraRAG/nop/corpus/benchmark"
    debug_dir = "/Users/abc/ai/UltraRAG/nop/corpus/benchmark/debug"
    max_files = 1000
    
    if len(sys.argv) > 1:
        input_dir = sys.argv[1]
    if len(sys.argv) > 2:
        output_dir = sys.argv[2]
    if len(sys.argv) > 3:
        max_files = int(sys.argv[3])
    if len(sys.argv) > 4:
        debug_dir = sys.argv[4]
    
    test = AIChunkingTest(input_dir, output_dir, debug_dir)
    test.test_sample_files(max_files)
