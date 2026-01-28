#!/usr/bin/env python3
"""
Optimal chunking strategy for Nop documentation
Ensures code block integrity and semantic coherence
"""
import re
import json
from pathlib import Path
from typing import List, Dict, Any, Tuple


def clean_text(text: str) -> str:
    """Clean text by normalizing whitespace"""
    if not text:
        return ""
    text = text.replace("\u3000", " ")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def split_by_structure(text: str, max_size: int = 1000) -> List[str]:
    """
    Split text by structure (headers, code blocks, paragraphs)
    Ensures code blocks are never broken
    """
    chunks = []
    
    # Strategy: Split by markdown headers (##) first
    # Then split large sections by code blocks and paragraphs
    
    # Find all header positions (##)
    header_pattern = re.compile(r'^##\s+', re.MULTILINE)
    header_positions = [(m.start(), m.group()) for m in header_pattern.finditer(text)]
    
    if not header_positions:
        # No headers, treat as single section
        sections = [text]
    else:
        # Split by headers
        sections = []
        prev_pos = 0
        
        for pos, header in header_positions:
            if pos > prev_pos:
                section = text[prev_pos:pos].strip()
                if section:
                    sections.append(section)
            prev_pos = pos
        
        # Don't forget the last section
        last_section = text[header_positions[-1][0]:].strip()
        if last_section:
            sections.append(last_section)
    
    # Now split each section if it's too large
    for section in sections:
        if len(section) <= max_size:
            chunks.append(section)
            continue
        
        # Section is too large, split it further
        # Strategy: Split by code blocks first
        section_chunks = split_large_section(section, max_size)
        chunks.extend(section_chunks)
    
    return chunks


def split_large_section(text: str, max_size: int) -> List[str]:
    """
    Split a large section by code blocks and paragraphs
    Ensures code blocks are never broken
    """
    chunks = []
    
    # Find all code blocks
    code_block_pattern = re.compile(r'```[\s\S]*?```', re.DOTALL)
    code_blocks = list(code_block_pattern.finditer(text))
    
    if not code_blocks:
        # No code blocks, split by paragraphs
        return split_by_paragraphs(text, max_size)
    
    # Split by code blocks
    prev_end = 0
    current_chunk = []
    current_size = 0
    
    for match in code_blocks:
        # Text before code block
        before_text = text[prev_end:match.start()].strip()
        code_block = match.group(0)
        
        # Add text before code block
        if before_text:
            if current_size + len(before_text) > max_size and current_chunk:
                # Save current chunk
                chunk_text = '\n'.join(current_chunk).strip()
                if chunk_text:
                    chunks.append(chunk_text)
                # Start new chunk
                current_chunk = [before_text]
                current_size = len(before_text)
            else:
                current_chunk.append(before_text)
                current_size += len(before_text)
        
        # Add code block (always keep it together)
        if current_size + len(code_block) > max_size and current_chunk:
            # Save current chunk
            chunk_text = '\n'.join(current_chunk).strip()
            if chunk_text:
                chunks.append(chunk_text)
            # Start new chunk with code block
            current_chunk = [code_block]
            current_size = len(code_block)
        else:
            current_chunk.append(code_block)
            current_size += len(code_block)
        
        prev_end = match.end()
    
    # Don't forget text after last code block
    after_text = text[prev_end:].strip()
    if after_text:
        current_chunk.append(after_text)
        current_size += len(after_text)
    
    # Don't forget the last chunk
    if current_chunk:
        chunk_text = '\n'.join(current_chunk).strip()
        if chunk_text:
            chunks.append(chunk_text)
    
    # If we still have very large chunks, split them by paragraphs
    final_chunks = []
    for chunk in chunks:
        if len(chunk) > max_size:
            # Split by paragraphs
            sub_chunks = split_by_paragraphs(chunk, max_size)
            final_chunks.extend(sub_chunks)
        else:
            final_chunks.append(chunk)
    
    return final_chunks


def split_by_paragraphs(text: str, max_size: int) -> List[str]:
    """Split text by paragraphs"""
    chunks = []
    paragraphs = re.split(r'\n{2,}', text)
    
    current_chunk = []
    current_size = 0
    
    for para in paragraphs:
        para = para.strip()
        if not para:
            continue
        
        para_size = len(para)
        
        if current_size + para_size > max_size and current_chunk:
            # Save current chunk
            chunk_text = '\n\n'.join(current_chunk).strip()
            if chunk_text:
                chunks.append(chunk_text)
            # Start new chunk
            current_chunk = [para]
            current_size = para_size
        else:
            current_chunk.append(para)
            current_size += para_size
    
    # Don't forget the last chunk
    if current_chunk:
        chunk_text = '\n\n'.join(current_chunk).strip()
        if chunk_text:
            chunks.append(chunk_text)
    
    return chunks


def chunk_documents_optimal(
    input_path: str,
    output_path: str,
    chunk_size: int = 1000,
    use_title: bool = True,
) -> int:
    """Optimal chunking strategy for Nop documentation"""
    
    # Load documents
    documents = []
    with open(input_path, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip():
                documents.append(json.loads(line))
    
    print(f"📝 Chunking {len(documents)} documents with optimal strategy...")
    
    # Chunk documents
    chunked_documents = []
    current_chunk_id = 0
    
    for doc in documents:
        doc_id = doc.get("id") or ""
        title = (doc.get("title") or "").strip()
        text = (doc.get("contents") or "").strip()
        
        if not text:
            continue
        
        # Split by structure
        text_chunks = split_by_structure(text, max_size=chunk_size)
        
        for text_chunk in text_chunks:
            if use_title:
                contents = f"Title: {title}\n\nContent: {text_chunk}"
            else:
                contents = text_chunk
            
            meta_chunk = {
                "id": current_chunk_id,
                "doc_id": doc_id,
                "title": title,
                "contents": contents.strip(),
            }
            chunked_documents.append(meta_chunk)
            current_chunk_id += 1
    
    # Save chunks
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_file, 'w', encoding='utf-8') as f:
        for chunk in chunked_documents:
            f.write(json.dumps(chunk, ensure_ascii=False) + "\n")
    
    # Statistics
    sizes = [len(c['contents']) for c in chunked_documents]
    print(f"✅ Created {len(chunked_documents)} chunks")
    print(f"📊 Average chunk size: {sum(sizes) // len(sizes):,} characters")
    print(f"📊 Min chunk size: {min(sizes):,} characters")
    print(f"📊 Max chunk size: {max(sizes):,} characters")
    print(f"📊 Median chunk size: {sorted(sizes)[len(sizes)//2]:,} characters")
    
    # Check code block integrity
    broken_code_blocks = 0
    for chunk in chunked_documents:
        code_blocks = chunk['contents'].count('```')
        if code_blocks % 2 != 0:
            broken_code_blocks += 1
    
    print(f"📊 Chunks with broken code blocks: {broken_code_blocks} ({broken_code_blocks/len(chunked_documents)*100:.1f}%)")
    
    return len(chunked_documents)


def main():
    """Main function"""
    print("🚀 Optimal Chunking Strategy for Nop Documentation\n")
    
    # Paths
    input_path = "nop/corpus/text.jsonl"
    output_path = "nop/corpus/chunks_optimal.jsonl"
    
    # Check if input exists
    if not Path(input_path).exists():
        print(f"❌ Input file not found: {input_path}")
        return
    
    # Chunk documents
    chunk_count = chunk_documents_optimal(
        input_path=input_path,
        output_path=output_path,
        chunk_size=1000,
        use_title=True,
    )
    
    print("\n" + "=" * 60)
    print("✅ Optimal chunking completed!")
    print("=" * 60)
    print(f"📁 Chunks saved to: {output_path}")
    print(f"📊 Total chunks: {chunk_count}")


if __name__ == "__main__":
    main()
