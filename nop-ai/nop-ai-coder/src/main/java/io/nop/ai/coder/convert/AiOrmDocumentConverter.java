package io.nop.ai.coder.convert;

import io.nop.ai.coder.orm.AiOrmModel;
import io.nop.api.core.exceptions.NopException;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.impl.ITextDocumentConverter;

import static io.nop.ai.coder.AiCoderErrors.ARG_FROM;
import static io.nop.ai.coder.AiCoderErrors.ARG_TO;
import static io.nop.ai.coder.AiCoderErrors.ERR_AI_CODER_UNSUPPORTED_CONVERSION;

public class AiOrmDocumentConverter implements ITextDocumentConverter {

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
        throw new NopException(ERR_AI_CODER_UNSUPPORTED_CONVERSION)
                .param(ARG_FROM, fromFileType)
                .param(ARG_TO, toFileType);
    }
}
