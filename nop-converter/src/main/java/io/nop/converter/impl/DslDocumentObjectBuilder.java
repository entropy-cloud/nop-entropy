package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.xdsl.DslModelParser;

import java.util.Map;

public class DslDocumentObjectBuilder implements IDocumentObjectBuilder {

    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new DslDocumentObject(fileType, resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        if (path == null)
            path = "/text/unnamed." + fileType;
        return buildFromResource(fileType, new InMemoryTextResource(path, text));
    }

    public static class DslDocumentObject extends ResourceDocumentObject {
        public DslDocumentObject(String fileType, IResource resource) {
            super(fileType, resource);
        }

        @Override
        public Object getModelObject() {
            String fileType = getFileType();
            ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(fileType);

            ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
            if (loaderConfig == null)
                throw new IllegalArgumentException("unsupported fileType: " + fileType);

            String fileExt = StringHelper.fileExtFromFileType(fileType);
            if (JsonTool.isJsonOrYamlFileExt(fileExt)) {
                return JsonTool.parseBeanFromResource(getResource(), Map.class);
            } else {
                return new DslModelParser().dynamic(true).parseFromResource(getResource());
            }
        }
    }
}