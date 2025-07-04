package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.json.JsonTool;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON5;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YAML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YML;

public class JsonDocumentConverter implements ITextDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        Object bean = doc.getModelObject(options);
        if (FILE_TYPE_JSON.equals(toFileType) || FILE_TYPE_JSON5.equals(toFileType))
            return JsonTool.serialize(bean, true);

        if (FILE_TYPE_YAML.equals(toFileType) || FILE_TYPE_YML.equals(toFileType))
            return JsonTool.serializeToYaml(bean);

        if (FILE_TYPE_XML.equals(toFileType)) {
            return doc.getNode(options).xml();
        }

        throw new IllegalArgumentException("Unsupported file type: " + toFileType);
    }
}
