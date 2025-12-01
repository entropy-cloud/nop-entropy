package io.nop.converter.utils;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.IDocumentConverterManager;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.List;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_DOCX;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_JAR;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_PDF;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_PPTX;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XLSX;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_ZIP;

public class DocConvertHelper {
    public static boolean defaultBinaryOnly(String fileType) {
        return FILE_TYPE_PDF.equals(fileType) ||
                FILE_TYPE_DOCX.equals(fileType) ||
                FILE_TYPE_XLSX.equals(fileType) ||
                FILE_TYPE_PPTX.equals(fileType) ||
                FILE_TYPE_ZIP.equals(fileType) ||
                FILE_TYPE_JAR.equals(fileType);
    }

    public static void mergeAndConvertResources(List<IResource> fromResources, IResource toResource) {
        IDocumentConverterManager manager = DocumentConverterManager.instance();
        if (fromResources == null || fromResources.isEmpty())
            return;

        DocumentConvertOptions options = DocumentConvertOptions.create().allowChained();

        if (fromResources.size() == 1) {
            manager.convertResource(fromResources.get(0), toResource, options);
            return;
        }

        String fileType = StringHelper.fileType(fromResources.get(0).getPath());

        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(fileType);

        IXDefinition xdef = SchemaLoader.loadXDefinition(config.getXdefPath());

        XNode node = null;
        for (IResource resource : fromResources) {
            String resType = StringHelper.fileType(resource.getPath());
            IDocumentObject obj = manager.getDocumentObjectBuilder(resType).buildFromResource(resType, resource);
            if (node == null) {
                node = obj.getNode(options);
            } else {
                XNode resNode = obj.getNode(options);
                XDslKeys keys = XDslKeys.of(resNode);
                new DeltaMerger(keys).merge(node, resNode, xdef, false);
            }
        }

        String xdslFileType = config.getXdslFileType();
        if (xdslFileType == null)
            xdslFileType = config.getPrimaryLoaderFileType();

        IResource resultRes = new InMemoryTextResource("/text/unnamed." + xdslFileType, node.xml());
        manager.convertResource(resultRes, toResource, options);
    }
}
