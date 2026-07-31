package io.nop.ai.coder.xdsl;

import io.nop.ai.core.file.IFileOperator;
import io.nop.ai.core.file.LocalFileOperator;
import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import java.io.File;

/**
 * DSL schema tool backed by the legacy {@link IFileOperator}.
 * <p>
 * <b>P2-MA3-05 ruling — retained with documentation (no migration this batch):</b>
 * migration target is {@code io.nop.ai.toolkit.fs.IToolFileSystem}; a faithful
 * migration requires first reconciling the two file-system abstractions
 * (P2-MA1-012, later batch). Retained intentionally, do not "fix" by inlining.
 */
public class DslToolImpl implements IDslTool {
    private final IFileOperator fileOperator;

    public DslToolImpl(IFileOperator fileOperator) {
        this.fileOperator = fileOperator;
    }

    public static DslToolImpl createForDir(File dir) {
        return new DslToolImpl(new LocalFileOperator(dir));
    }

    @Override
    public String loadDslSchema(String schemaPath) {
        String fileType = StringHelper.fileType(schemaPath);
        IResource resource = VirtualFileSystem.instance().getResource(schemaPath);
        return DocumentConverterManager.instance().buildFromResource(fileType, resource).getText(null);
    }

    @Override
    public String loadDslSchemaForFileType(String fileType) {
        String schemaPath = DocumentConverterManager.instance().getXdefPath(fileType);
        return loadDslSchema(schemaPath);
    }

    @Override
    public String loadDslFile(String filePath, String toFileType) {
        String fileType = StringHelper.fileType(filePath);
        IResource resource = getResource(filePath);
        IDocumentObject obj = DocumentConverterManager.instance().buildFromResource(fileType, resource);

        if (fileType.equals(obj.getFileType()))
            return obj.getText(null);

        DocumentConvertOptions options = DocumentConvertOptions.create().allowChained();
        String text = obj.getText(options);
        String result = DocumentConverterManager.instance().convertText(filePath, text, obj.getFileType(), toFileType, options);
        return result;
    }

    @Override
    public void saveDslFile(String filePath, String fromFileType, String content) {
        String fileType = StringHelper.fileType(filePath);
        IResource resource = getResource(filePath);

        DocumentConvertOptions options = DocumentConvertOptions.create().allowChained();
        String result = DocumentConverterManager.instance().convertText(filePath, content, fromFileType, fileType, options);
        ResourceHelper.writeText(resource, result, null);
    }

    protected IResource getResource(String filePath) {
        IResource resource = fileOperator.getResource(filePath);
        if (!resource.exists()) {
            if (filePath.startsWith("/")) {
                resource = VirtualFileSystem.instance().getResource(filePath);
            }
        }
        return resource;
    }
}