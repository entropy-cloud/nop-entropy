package io.nop.ai.coder.convert;

import io.nop.ai.coder.orm.AiOrmModel;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AiOrmDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        String fromFileType = doc.getFileType();

        if ("orm.xml".equals(fromFileType) && "ai-orm.xml".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromOrmNode(doc.getNode());
            return model.getOrmNodeForAi().xml();
        } else if ("ai-orm.xml".equals(fromFileType) && "orm.xml".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromAiResult(doc.getNode(), null);
            return model.getOrmNode().xml();
        } else if ("orm.xml".equals(fromFileType) && "orm.java".equals(toFileType)) {
            AiOrmModel model = AiOrmModel.buildFromOrmNode(doc.getNode());
            return model.getOrmModelJava();
        }
        throw new IllegalArgumentException("Unsupported conversion:" + fromFileType + "->" + toFileType);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        String text = convertToText(doc, toFileType);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
