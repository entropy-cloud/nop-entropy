package io.nop.ai.mcp.server;

import io.nop.ai.api.mcp.McpConstants;
import io.nop.ai.coder.simplifier.JsonSimplifier;
import io.nop.ai.coder.simplifier.XNodeSimplifier;
import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
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

import static io.nop.ai.mcp.server.McpServerErrors.ARG_PATH;
import static io.nop.ai.mcp.server.McpServerErrors.ERR_MCP_FILE_NOT_FOUND;

@BizModel(McpConstants.BIZ_OBJ_AI_TOOL)
public class AiFileTool {
    private String baseDir;

    @InjectValue("@cfg:ai.mcp.base-dir|.")
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    @Description("@18n:ai.get-nop-file-xdef|加载Nop文件的XDef元模型\n")
    public String loadNopFileXDef(@Name("fileType") String fileType) {
        IDocumentObjectBuilder builder = DocumentConverterManager.instance().requireDocumentObjectBuilder(fileType);
        String xdefPath = builder.getXdefPath(fileType);
        if (StringHelper.isEmpty(xdefPath)) {
            return "ERROR: no xdef for fileType " + fileType;
        }
        return AiXDefHelper.loadXDefForAi(xdefPath).xml();
    }

    @Description("@18n:ai.load-nop-file|加载Nop文件\n")
    @BizQuery
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
                if (xdefPath != null) {
                    return "ERROR: fileType " + fromFileType + " is not supported for merge";
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

    IResource getResource(String path) {
        File file = new File(baseDir, path);
        if (file.exists()) {
            path = StringHelper.normalizePath(path);
            if (!path.startsWith("/") && path.indexOf(':') < 0)
                path = "/" + path;
            return new FileResource(path, file);
        }

        if (path.startsWith("/")) {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists())
                return resource;
        }
        throw new NopException(ERR_MCP_FILE_NOT_FOUND)
                .param(ARG_PATH, path);
    }
}
