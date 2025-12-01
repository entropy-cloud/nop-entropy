package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;

import java.io.IOException;
import java.io.OutputStream;

public class SameTypeDocumentConverter implements IDocumentConverter {
    public static SameTypeDocumentConverter INSTANCE = new SameTypeDocumentConverter();

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        if (keepRaw(doc, options))
            return doc.getText(options);

        return doc.getText(options);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        if (keepRaw(doc, options)) {
            doc.saveToStream(out, options);
            return;
        }

        ComponentModelConfig.LoaderConfig config = getLoader(doc);
        if (this.supportDslNode(config)) {
            XNode node = doc.getNode(options);
            InMemoryTextResource target = new InMemoryTextResource("/text/temp." + toFileType, "");
            config.getDslNodeSaver().saveDslNodeToResource(target, node);
            target.writeToStream(out);
        } else {
            doc.saveToStream(out, options);
        }
    }

    @Override
    public void convertToResource(IDocumentObject doc, String toFileType, IResource resource, DocumentConvertOptions options) {
        if (keepRaw(doc, options)) {
            doc.saveToResource(resource, options);
            return;
        }

        ComponentModelConfig.LoaderConfig config = getLoader(doc);
        if (this.supportDslNode(config)) {
            XNode node = doc.getNode(options);
            config.getDslNodeSaver().saveDslNodeToResource(resource, node);
        } else {
            doc.saveToResource(resource, options);
        }
    }

    protected boolean keepRaw(IDocumentObject doc, DocumentConvertOptions options) {
        return doc.isBinaryOnly() || options.getDslNodeResolvePhase() == IResourceDslNodeLoader.ResolvePhase.raw;
    }

    protected ComponentModelConfig.LoaderConfig getLoader(IDocumentObject doc) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(doc.getFileType());
        if (config == null)
            return null;
        return config.getLoader(doc.getFileType());
    }

    protected boolean supportDslNode(ComponentModelConfig.LoaderConfig config) {
        if (config == null)
            return false;
        return config.getDslNodeLoader() != null && config.getDslNodeSaver() != null;
    }
}
