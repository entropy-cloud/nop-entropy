package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.ResourceDocument;


/**
 * 初步解析完毕后，对文档进行识别页眉页脚等后处理
 */
public interface IResourceDocumentProcessor {

    public void process( ResourceDocument doc , ResourceParseConfig config);
}