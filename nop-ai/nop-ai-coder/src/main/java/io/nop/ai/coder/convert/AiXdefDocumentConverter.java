package io.nop.ai.coder.convert;

import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.commons.cache.MapCache;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.impl.ITextDocumentConverter;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.DslNodeLoader;

public class AiXdefDocumentConverter implements ITextDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        String fromFileType = doc.getFileType();

        if ("xdef".equals(fromFileType) && "ai-xdef.xml".equals(toFileType)) {
            XNode node = DslNodeLoader.INSTANCE.loadFromResource(doc.getResource()).getNode();
            return AiXDefHelper.transformForAi(node, false, new MapCache<>()).xml();
        }
        throw new IllegalArgumentException("Unsupported conversion:" + fromFileType + "->" + toFileType);
    }
}
