import os
from marker.converters.pdf import PdfConverter
from marker.models import create_model_dict
from marker.output import text_from_rendered

def pdf_to_markdown(pdf_path: str, output_dir: str, use_llm: bool = False, format_lines: bool = False):
    """
    将 PDF 文件转换为 Markdown 保存到指定目录。

    :param pdf_path: PDF 文件路径
    :param output_dir: Markdown 输出目录
    :param use_llm: 是否启用 LLM （可提升复杂文档准确率）
    :param format_lines: 是否规范化行格式
    """
    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)

    # 配置转换参数
    config = {
        "output_format": "markdown",
        "use_llm": use_llm,
        "format_lines": format_lines,
        # 你还可以加如需: "force_ocr": True
    }

    # 创建转换器
    converter = PdfConverter(
        artifact_dict=create_model_dict(),
        config=config
    )

    # 执行转换
    rendered = converter(pdf_path)
    text, _, images = text_from_rendered(rendered)
    
    # 构造输出文件名
    base = os.path.splitext(os.path.basename(pdf_path))[0]
    md_path = os.path.join(output_dir, base + ".md")

    # 保存 Markdown
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"Markdown 文件已保存到: {md_path}")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: python script.py <pdf_file> <output_folder>")
        sys.exit(1)
    pdf_file = sys.argv[1]
    output_folder = sys.argv[2]
    pdf_to_markdown(pdf_file, output_folder)