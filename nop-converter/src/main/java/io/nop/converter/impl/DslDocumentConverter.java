package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class DslDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(doc.getFileType());

        String fileExt = StringHelper.fileExtFromFileType(toFileType);
        if (ResourceConstants.YAML_FILE_EXTS.contains(fileExt))
            return JsonTool.serializeToYaml(doc.getModelObject());
        if (JsonTool.isJsonOrYamlFileExt(fileExt)) {
            return JsonTool.serialize(doc.getModelObject(), true);
        }

        String xdefPath = config.getXdefPath();
        if (xdefPath == null)
            throw new IllegalArgumentException("fileType no xdef:" + toFileType);

        return DslModelHelper.dslModelToXNode(xdefPath, doc.getModelObject()).xml();
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        String text = convertToText(doc, toFileType);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}