package io.nop.ai.mcp.server;

import io.nop.ai.api.mcp.McpConstants;
import io.nop.ai.coder.simplifier.JsonSimplifier;
import io.nop.ai.coder.simplifier.XNodeSimplifier;
import io.nop.ai.core.api.xdef.AiXDefHelper;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.DocumentConverterManager;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.XDslCleaner;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xmeta.SchemaLoader;

import java.io.File;
import java.io.IOException;

import static io.nop.ai.mcp.server.McpServerErrors.ARG_FILE_TYPE;
import static io.nop.ai.mcp.server.McpServerErrors.ARG_PATH;
import static io.nop.ai.mcp.server.McpServerErrors.ERR_MCP_MERGE_NOT_SUPPORTED;
import static io.nop.ai.mcp.server.McpServerErrors.ERR_MCP_NO_XDEF_FOR_FILE_TYPE;
import static io.nop.ai.mcp.server.McpServerErrors.ERR_MCP_PATH_ESCAPE;

@BizModel(McpConstants.BIZ_OBJ_AI_TOOL)
public class AiFileTool {
    private String baseDir;

    /**
     * 沙箱根目录。默认值 "." 相对进程工作目录解析；getResource 保证所有落盘路径都包含在 baseDir 内。
     */
    @InjectValue("@cfg:ai.mcp.base-dir|.")
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    @Description("@18n:ai.get-nop-file-xdef|加载Nop文件的XDef元模型\n")
    @BizQuery
    @Auth(permissions = "AiTool:read")
    public String loadNopFileXDef(@Name("fileType") String fileType) {
        IDocumentObjectBuilder builder = DocumentConverterManager.instance().requireDocumentObjectBuilder(fileType);
        String xdefPath = builder.getXdefPath(fileType);
        if (StringHelper.isEmpty(xdefPath)) {
            throw new NopException(ERR_MCP_NO_XDEF_FOR_FILE_TYPE).param(ARG_FILE_TYPE, fileType);
        }
        return AiXDefHelper.loadXDefForAi(xdefPath).xml();
    }

    @Description("@18n:ai.load-nop-file|加载Nop文件\n")
    @BizQuery
    @Auth(permissions = "AiTool:read")
    public String loadNopFile(@Name("path") String path,
                              @Optional @Name("toFileType") String toFileType,
                              @Optional @Name("filter") String filter) {
        IResource resource = getResource(path);
        if ("txt".equals(toFileType) || "raw".equals(toFileType) || "text".equals(toFileType)) {
            String text = resource.readText();
            return "<FILE_CONTENT>" + text + "</FILE_CONTENT>";
        }

        DocumentConvertOptions options = DocumentConvertOptions.create();

        String fromFileType = StringHelper.fileType(path);
        String fromFileExt = StringHelper.fileExtFromFileType(fromFileType);

        if (StringHelper.isEmpty(toFileType)) {
            IDocumentObject doc = DocumentConverterManager.instance().requireDocumentObjectBuilder(fromFileType).buildFromResource(fromFileType, resource);

            if (JsonTool.isYamlFileExt(fromFileExt)) {
                return JsonTool.serializeToYaml(filterJson(doc.getModelObject(options), filter));
            } else if (JsonTool.isJsonFileExt(toFileType)) {
                return JsonTool.stringify(filterJson(doc.getModelObject(options), filter));
            } else {
                String xdefPath = doc.getXdefPath();
                if (xdefPath != null || fromFileExt.equals("xml"))
                    return filterNode(doc.getNode(options), filter).fullXml(false, false);
                return doc.getText(options);
            }
        }

        InMemoryTextResource out = new InMemoryTextResource(resource.getStdPath(), "");
        DocumentConverterManager.instance().convertResource(resource, out, fromFileType, toFileType, options);
        return out.getText();
    }

    Object filterJson(Object json, String filter) {
        if (StringHelper.isEmpty(filter))
            return json;

        return new JsonSimplifier(ConvertHelper.toCsvSet(filter)).simplify(json);
    }

    XNode filterNode(XNode node, String filter) {
        if (StringHelper.isEmpty(filter))
            return node;
        return new XNodeSimplifier(ConvertHelper.toCsvSet(filter)).simplify(node);
    }

    @Description("@18n:ai.save-nop-file|保存Nop文件\n")
    @BizMutation
    @Auth(permissions = "AiTool:write")
    public String saveNopFile(@Name("path") String path,
                              @Optional @Name("fromFileType") String fromFileType,
                              @Optional @Name("content") String content,
                              @Optional @Name("merge") Boolean merge) {
        IResource resource = getResource(path);

        DocumentConvertOptions options = DocumentConvertOptions.create();

        String toFileType = StringHelper.fileType(path);

        if (StringHelper.isEmpty(fromFileType)) {
            fromFileType = toFileType;
        }

        if (fromFileType.equals(toFileType)) {
            if (Boolean.TRUE.equals(merge)) {
                IDocumentObjectBuilder builder = DocumentConverterManager.instance().requireDocumentObjectBuilder(fromFileType);
                String xdefPath = builder.getXdefPath(fromFileType);
                if (xdefPath == null) {
                    throw new NopException(ERR_MCP_MERGE_NOT_SUPPORTED).param(ARG_FILE_TYPE, fromFileType);
                }

                IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
                XNode node = XNodeParser.instance().parseFromText(null, content);
                new XDslCleaner().clean(node, xdef);

                if (resource.exists()) {
                    XNode oldNode = XNodeParser.instance().parseFromResource(resource);
                    XDslKeys keys = XDslKeys.of(oldNode);
                    new DeltaMerger(keys).merge(oldNode, node, xdef, false);
                    ResourceHelper.writeText(resource, oldNode.xml());
                } else {
                    ResourceHelper.writeText(resource, node.xml());
                }
            } else {
                ResourceHelper.writeText(resource, content);
            }
        } else {
            String fromPath = "/text/input." + fromFileType;
            IResource inputResource = new InMemoryTextResource(fromPath, content);
            DocumentConverterManager.instance().convertResource(inputResource, resource, fromFileType, toFileType, options);
        }
        return "SUCCESS";
    }

    /**
     * 解析 path 对应的落盘文件并做 fail-closed 沙箱校验：解析后的真实路径（canonical）必须落在 baseDir 内。
     * 绝对路径与 {@code ..} 逃逸一律抛 ERR_MCP_PATH_ESCAPE；解析失败同样 fail-closed。
     * 仅对 baseDir 落盘路径调用，VFS 回退分支不经过本方法。
     */
    private void ensureWithinBaseDir(File file, String path) {
        try {
            File base = new File(baseDir).getCanonicalFile();
            File target = file.getCanonicalFile();
            if (!isInsideBaseDir(target, base))
                throw new NopException(ERR_MCP_PATH_ESCAPE).param(ARG_PATH, path);
        } catch (IOException e) {
            throw new NopException(ERR_MCP_PATH_ESCAPE).param(ARG_PATH, path);
        }
    }

    /**
     * 校验尚不存在的新建文件路径（saveNopFile 场景）：目标不存在时先 canonical 解析父目录再拼接文件名，
     * 避免新建路径被误判为不存在而绕过校验；越界路径（含将写入 baseDir 之外的新文件）一律 fail-closed。
     */
    private void ensureNewFileWithinBaseDir(File file, String path) {
        try {
            File base = new File(baseDir).getCanonicalFile();
            File parent = file.getParentFile();
            File canonicalParent = parent == null ? base : parent.getCanonicalFile();
            File target = new File(canonicalParent, file.getName());
            if (!isInsideBaseDir(target, base))
                throw new NopException(ERR_MCP_PATH_ESCAPE).param(ARG_PATH, path);
        } catch (IOException e) {
            throw new NopException(ERR_MCP_PATH_ESCAPE).param(ARG_PATH, path);
        }
    }

    private static boolean isInsideBaseDir(File target, File base) {
        String targetPath = target.getPath();
        String basePath = base.getPath();
        return targetPath.equals(basePath) || targetPath.startsWith(basePath + File.separator);
    }

    IResource getResource(String path) {
        if (path.startsWith("/")) {
            // 绝对路径仅允许命中 VFS 资源；未命中一律 fail-closed 拒绝。
            // Unix 下 new File(baseDir, path) 会把绝对路径拼接进 baseDir 内部路径，Windows 下盘符路径是
            // 真正的绝对路径——两种形态都不属于沙箱相对路径契约，且绝对路径可能指向 baseDir 之外。
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists())
                return resource;
            throw new NopException(ERR_MCP_PATH_ESCAPE).param(ARG_PATH, path);
        }

        File file = new File(baseDir, path);
        if (file.exists()) {
            ensureWithinBaseDir(file, path);
            return toFileResource(path, file);
        }

        // 非存在路径：越界（../ 逃逸、Windows 盘符绝对路径）一律拒绝；沙箱内放行以便 saveNopFile 新建文件
        ensureNewFileWithinBaseDir(file, path);
        return toFileResource(path, file);
    }

    private IResource toFileResource(String path, File file) {
        path = StringHelper.normalizePath(path);
        if (!path.startsWith("/") && path.indexOf(':') < 0)
            path = "/" + path;
        return new FileResource(path, file);
    }
}
