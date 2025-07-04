package io.nop.converter.impl;

import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_JSON5;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_XML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YAML;
import static io.nop.converter.DocConvertConstants.FILE_TYPE_YML;
import static io.nop.core.CoreConfigs.CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP;

public class ResourceDocumentObject implements IDocumentObject {
    private final String fileType;
    private final String fileExt;
    private final IResource resource;

    public ResourceDocumentObject(String fileType, IResource resource) {
        this.fileType = fileType;
        this.fileExt = StringHelper.lastPart(fileType, '.');
        this.resource = Guard.notNull(resource, "resource");
    }

    @Override
    public SourceLocation getLocation() {
        return SourceLocation.fromPath(resource.getPath());
    }

    @Override
    public String getFileType() {
        return fileType;
    }

    @Override
    public String getFileExt() {
        return fileExt;
    }

    @Override
    public boolean isBinaryOnly() {
        return DocConvertHelper.defaultBinaryOnly(fileType);
    }

    @Override
    public Object getModelObject(DocumentConvertOptions options) {
        if (FILE_TYPE_JSON.equals(fileExt) || FILE_TYPE_YAML.equals(fileExt)
                || FILE_TYPE_JSON5.equals(fileExt) || FILE_TYPE_YML.equals(fileExt)) {
            return parseJson();
        }
        if (FILE_TYPE_XML.equals(fileExt))
            return XNodeParser.instance().parseFromResource(resource);
        return null;
    }

    protected Object parseJson() {
        JsonParseOptions options = new JsonParseOptions();
        if (FILE_TYPE_YAML.equals(fileExt) || FILE_TYPE_YML.equals(fileExt)) {
            options.setYaml(true);
        } else if (FILE_TYPE_JSON5.equals(fileExt)) {
            options.setStrictMode(false);
        }
        options.setTargetType(JObject.class);
        options.setIgnoreUnknownProp(CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP.get());
        options.setKeepLocation(true);
        return JsonTool.instance().parseFromResource(resource, options);
    }

    @Override
    public String getText(DocumentConvertOptions options) {
        if (isBinaryOnly())
            throw new UnsupportedOperationException("Binary document does not support text retrieval");
        return ResourceHelper.readText(resource);
    }

    @Override
    public void saveToResource(IResource resource, DocumentConvertOptions options) {
        this.resource.saveToResource(resource);
    }

    @Override
    public void saveToStream(OutputStream out, DocumentConvertOptions options) throws IOException {
        InputStream in = null;
        try {
            in = resource.getInputStream();
            IoHelper.copy(in, out);
            out.flush();
        } finally {
            IoHelper.safeCloseObject(in);
        }
    }

    @Override
    public IResource getResource() {
        return resource;
    }
}