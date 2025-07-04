package io.nop.ai.coder.convert;

import io.nop.ai.coder.orm.AiOrmModel;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AiOrmDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        String fromFileType = doc.getFileType();

        if ("orm.xml".equals(fromFileType) && "ai-orm.xml".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromOrmNode(doc.getNode(options));
            return model.getOrmNodeForAi().xml();
        } else if ("ai-orm.xml".equals(fromFileType) && "orm.xml".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromAiResult(doc.getNode(options), null);
            return model.getOrmNode().xml();
        } else if ("orm.xml".equals(fromFileType) && "orm.java".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromOrmNode(doc.getNode(options));
            return model.getOrmModelJava();
        } else if ("orm.xml".equals(fromFileType) && "orm.xml".equals(toFileType)) {
            return doc.getNode(options).xml();
        } else if ("ai-orm.xml".equals(fromFileType) && "ai-orm.xml".equals(toFileType)) {
            return doc.getNode(options).xml();
        }
        throw new IllegalArgumentException("Unsupported conversion:" + fromFileType + "->" + toFileType);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        String text = convertToText(doc, toFileType, options);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
