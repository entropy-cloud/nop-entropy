package io.nop.converter.impl;

import io.nop.api.core.util.IComponentModel;
import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.initialize.DslJsonResourceLoader;
import io.nop.xlang.initialize.DslXmlResourceLoader;
import io.nop.xlang.xdsl.IDslResourceObjectLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

    @Override
    public String getXdefPath(String fileType) {
        return getXdefPathFromFileType(fileType);
    }

    static String getXdefPathFromFileType(String fileType) {
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(fileType);

        ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
        if (loaderConfig == null)
            throw new IllegalArgumentException("unsupported fileType: " + fileType);
        return loaderConfig.getXdefPath();
    }

    public static class DslDocumentObject extends ResourceDocumentObject {
        public DslDocumentObject(String fileType, IResource resource) {
            super(fileType, resource);
        }

        @Override
        public Object getModelObject(DocumentConvertOptions options) {
            return newLoader(options).parseFromResource(getResource());
        }

        @Override
        public String getXdefPath() {
            String fileType = getFileType();
            return getXdefPathFromFileType(fileType);
        }

        IDslResourceObjectLoader<IComponentModel> newLoader(DocumentConvertOptions options) {
            String fileType = getFileType();
            ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(fileType);

            ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
            if (loaderConfig == null)
                throw new IllegalArgumentException("unsupported fileType: " + fileType);

            String fileExt = StringHelper.fileExtFromFileType(fileType);
            if (JsonTool.isJsonOrYamlFileExt(fileExt)) {
                return new DslJsonResourceLoader(loaderConfig.getXdefPath(), config.getResolveInDir()).dynamic(true);
            } else {
                return new DslXmlResourceLoader(loaderConfig.getXdefPath(), config.getResolveInDir()).dynamic(true);
            }
        }

        @Override
        public void saveToResource(IResource resource, DocumentConvertOptions options) {
            ResourceHelper.writeText(resource, getText(options));
        }

        @Override
        public void saveToStream(OutputStream out, DocumentConvertOptions options) throws IOException {
            out.write(getText(options).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getText(DocumentConvertOptions options) {
            IDslResourceObjectLoader<IComponentModel> loader = newLoader(options);
            if (loader instanceof DslJsonResourceLoader) {
                Object bean = loader.parseFromResource(getResource());
                String fileExt = getFileExt();
                if (CoreConstants.YAML_FILE_EXTS.contains(fileExt))
                    return JsonTool.serializeToYaml(bean);
                return JsonTool.serialize(bean, true);
            }
            return loader.parseNodeFromResource(getResource()).xml();
        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            return newLoader(options).parseNodeFromResource(getResource());
        }
    }
}