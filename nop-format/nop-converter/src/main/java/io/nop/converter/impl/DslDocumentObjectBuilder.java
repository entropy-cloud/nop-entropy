package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;

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
            return newLoader(options).loadObjectFromResource(getResource());
        }

        @Override
        public String getXdefPath() {
            String fileType = getFileType();
            return getXdefPathFromFileType(fileType);
        }

        IResourceObjectLoader<Object> newLoader(DocumentConvertOptions options) {
            String fileType = getFileType();
            ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(fileType);

            ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(fileType);
            if (loaderConfig == null)
                throw new IllegalArgumentException("unsupported fileType: " + fileType);

            return loaderConfig.getLoader();
        }

        @Override
        public void saveToResource(IResource resource, DocumentConvertOptions options) {
            ResourceHelper.writeText(resource, getText(options));
        }

        @Override
        public void saveToStream(OutputStream out, DocumentConvertOptions options) throws IOException {
            out.write(getText(options).getBytes(StandardCharsets.UTF_8));
        }

//        @Override
//        public String getText(DocumentConvertOptions options) {
//            IResourceObjectLoader<Object> loader = newLoader(options);
//            if (loader instanceof IDslNodeTextSerializer) {
//                IDslNodeTextSerializer serializer = (IDslNodeTextSerializer) loader;
//                Object bean = loader.load(getResource());
//                return serializer.serializeToText(getFileType(), bean);
//            }
//            if (loader instanceof IDslTextSerializer) {
//                IDslTextSerializer serializer = (IDslTextSerializer) loader;
//                Object bean = loader.loadObjectFromResource(getResource());
//                return serializer.serializeToText(getFileType(), bean);
//            }
//            if (loader instanceof IResourceDslNodeLoader)
//                return ((IResourceDslNodeLoader) loader).loadDslNodeFromResource(getResource()).xml();
//            return super.getText(options);
//        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            IResourceObjectLoader<Object> loader = newLoader(options);
            if (loader instanceof IResourceDslNodeLoader)
                return ((IResourceDslNodeLoader) loader).loadDslNodeFromResource(getResource(), options.getDslNodeResolvePhase());
            throw new IllegalArgumentException("nop.err.converter.not-support-node");
        }
    }
}