package io.nop.pdf.extract;

import io.nop.pdf.extract.export.ResourceDocumentExporters;
import io.nop.pdf.extract.parser.ResourceDocumentHtmlParser;
import io.nop.pdf.extract.parser.ResourceDocumentParser;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.table.DefaultTableMerger;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

/**
 * 处理ResourceDocument的Facade帮助类
 */
public class ResourceDocumentTool {
    static ResourceDocumentTool _instance = new ResourceDocumentTool();

    public static ResourceDocumentTool getInstance() {
        return _instance;
    }

    List<IResourceDocumentProcessor> processors;

    public void setPostProcessors(List<IResourceDocumentProcessor> processors) {
        this.processors = processors;
    }

    public IResourceDocumentParser newParser(ResourceParseConfig config) {
        return new ResourceDocumentParser(config);
    }

    public String extractTextFromResource(IResource pdfFile, ResourceParseConfig config) {
        IResourceDocumentParser parser = this.newParser(config);
        if (processors != null) {
            for (IResourceDocumentProcessor processor : this.processors) {
                parser.addPostProcessor(processor);
            }
        }
        IResourceDocumentExporter exporter = ResourceDocumentExporters.getExporter(ResourceDocumentConstants.EXPORT_TYPE_TXT);
        ResourceDocument doc = parser.parseFromResource(pdfFile);
        StringWriter out = new StringWriter();
        try {
            exporter.exportToWriter(doc, out, "UTF-8");
        } catch (IOException e) {
            throw NopException.wrap(e);
        }
        return out.toString();
    }

    public String extractTextFromFile(File pdfFile) {
        return extractTextFromResource(new FileResource(pdfFile), new ResourceParseConfig());
    }

    public void exportToFileDir(ResourceDocument doc, File dir, String baseName,
                                Collection<String> exportTypes) {
        dir.mkdirs();
        IResource resource = new FileResource(dir);
        this.exportToResourceDir(doc, resource, baseName, exportTypes);
    }

    public void exportToResourceDir(ResourceDocument doc, IResource resource, String baseName,
                                    Collection<String> exportTypes) {
        //dir.mkdirs();
        //IResourceEx resource = new FileResource(dir.getAbsolutePath(), dir);
        for (String exportType : exportTypes) {
            IResourceDocumentExporter exporter = ResourceDocumentExporters
                    .getExporter(exportType);
            IResource exportResource = ResourceHelper.resolveRelativeResource(resource, baseName + "."
                    + exportType, false);
            exporter.exportToResource(doc, exportResource, "UTF-8");
        }
    }

    public void parseToFileDir(File pdfFile, File dir) {
        this.parseToFileDir(pdfFile, dir, new ResourceParseConfig());
    }

    public void parseToFileDir(File pdfFile, File dir, ResourceParseConfig config) {
        IResourceDocumentParser parser = this
                .newParser(config);
        ResourceDocument doc = parser.parseFromResource(new FileResource(
                pdfFile));
        String name = StringHelper.fileFullName(pdfFile.getName());
        exportToFileDir(doc, dir, name,
                ResourceDocumentConstants.EXPORT_TYPES);
    }

    public void parseToResourceDir(IResource pdfFile, IResource dir, ResourceParseConfig config) {
        IResourceDocumentParser parser = this
                .newParser(config);
        ResourceDocument doc = parser.parseFromResource(pdfFile);
        String name = StringHelper.fileFullName(pdfFile.getName());
        exportToResourceDir(doc, dir, name,
                ResourceDocumentConstants.EXPORT_TYPES);
    }

    public ResourceDocument parseFromHtml(IResource resource) {
        return new ResourceDocumentHtmlParser().parseFromResource(resource);
    }

    public TableBlock mergeTable(ResourceDocument doc, TableBlock tableA, TableBlock tableB) {
        return new DefaultTableMerger().merge(doc, tableA, tableB);
    }
}