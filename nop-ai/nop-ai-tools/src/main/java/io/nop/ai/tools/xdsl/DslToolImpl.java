package io.nop.ai.tools.xdsl;

import io.nop.ai.tools.utils.AiToolsHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.io.IOException;

import static io.nop.commons.CommonErrors.ERR_FILE_INVALID_PATH;

/**
 * DSL schema tool scoped to a base directory (project dir).
 *
 * <p>Sunk down from nop-ai-coder (M5-P1 round-1 audit finding 3, decision A):
 * implementation moved to nop-ai-tools together with {@link IDslTool} so the
 * concrete tool module no longer depends on the nop-ai-coder heavyweight
 * chain.
 *
 * <p><b>plan 2429/WI6:</b> the legacy {@code IFileOperator} dependency was
 * removed. Path resolution replicates the sandbox semantics of
 * {@code LocalFileOperator.getResource}: paths resolve against the base dir
 * (including "/..." forms, which {@link File#File(File, String)} treats as
 * absolute children), {@code ..}/{@code :} and canonical containment outside
 * the base dir fail loud with {@code ERR_FILE_INVALID_PATH}; a missing
 * absolute path falls back to the virtual filesystem (pre-existing behavior).
 */
public class DslToolImpl implements IDslTool {
    private final File baseDir;

    public DslToolImpl(File baseDir) {
        this.baseDir = FileHelper.getAbsoluteFile(baseDir);
    }

    public static DslToolImpl createForDir(File dir) {
        return new DslToolImpl(dir);
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
        // plan 2557 C1-005 (ruling C): local FileResource writes go through the
        // atomic temp+rename template; the virtual-resource branch (VFS resource,
        // non-local) keeps the original in-place write — the atomic template only
        // applies to real local files.
        if (resource instanceof FileResource) {
            AiToolsHelper.atomicWriteText(resource.toFile(), result);
        } else {
            ResourceHelper.writeText(resource, result, null);
        }
    }

    protected IResource getResource(String filePath) {
        IResource resource = new FileResource(resolveFile(filePath));
        if (!resource.exists()) {
            if (filePath.startsWith("/")) {
                resource = VirtualFileSystem.instance().getResource(filePath);
            }
        }
        return resource;
    }

    private File resolveFile(String path) {
        if (path.contains("..") || path.contains(":")) {
            throw new NopException(ERR_FILE_INVALID_PATH)
                    .param("path", path);
        }
        File resolved = path.isEmpty() ? baseDir : new File(baseDir, path);
        try {
            String resolvedCanonical = resolved.getCanonicalPath();
            String baseCanonical = baseDir.getCanonicalPath();
            if (!resolvedCanonical.startsWith(baseCanonical)) {
                throw new NopException(ERR_FILE_INVALID_PATH)
                        .param("path", path);
            }
        } catch (IOException e) {
            throw new NopException(ERR_FILE_INVALID_PATH, e)
                    .param("path", path);
        }
        return resolved;
    }
}
