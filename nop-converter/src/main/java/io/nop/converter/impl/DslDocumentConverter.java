package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdsl.DslModelHelper;

public class DslDocumentConverter implements ITextDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(doc.getFileType());

        String fileExt = StringHelper.fileExtFromFileType(toFileType);
        if (ResourceConstants.YAML_FILE_EXTS.contains(fileExt))
            return JsonTool.serializeToYaml(doc.getModelObject(options));
        if (JsonTool.isJsonOrYamlFileExt(fileExt)) {
            return JsonTool.serialize(doc.getModelObject(options), true);
        }

        String xdefPath = config.getXdefPath();
        if (xdefPath == null)
            throw new IllegalArgumentException("fileType no xdef:" + toFileType);

        return DslModelHelper.dslModelToXNode(xdefPath, doc.getModelObject(options)).xml();
    }
}