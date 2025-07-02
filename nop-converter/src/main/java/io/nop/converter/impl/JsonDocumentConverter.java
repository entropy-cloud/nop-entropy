package io.nop.converter.impl;

import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.json.JsonTool;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON5;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YAML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YML;

public class JsonDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        Object bean = doc.getModelObject();
        if (FILE_TYPE_JSON.equals(toFileType) || FILE_TYPE_JSON5.equals(toFileType))
            return JsonTool.serialize(bean, true);

        if (FILE_TYPE_YAML.equals(toFileType) || FILE_TYPE_YML.equals(toFileType))
            return JsonTool.serializeToYaml(bean);

        throw new IllegalArgumentException("Unsupported file type: " + toFileType);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        String text = convertToText(doc, toFileType);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
