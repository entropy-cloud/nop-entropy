/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.maven.plugin.shaded;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.type.IGenericType;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.xlang.xdef.*;
import io.nop.xlang.xdef.domain.GenericTypeDomainOptions;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslParseHelper;
import io.nop.xlang.xmeta.SchemaLoader;
import org.apache.maven.plugins.shade.relocation.Relocator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XdslResourceTransformer extends AbstractResourceTransformer {
    private final List<String> FILE_TYPES = List.of("xmeta", "xdef", "xwf", "xlib", "xpl", "xgen", "xrun","xbiz");

    static {
        CoreInitialization.initialize();
    }

    @Override
    public boolean canTransformResource(String path) {
        String fileExt = StringHelper.fileExt(path);
        return FILE_TYPES.contains(fileExt) || fileExt.equals("xml");
    }

    @Override
    public void processResource(String path, InputStream in, List<Relocator> relocatorList) throws IOException {
        try {
            byte[] data = IoHelper.readBytes(in);
            path = StringHelper.appendPath("/", path);
            processResource(new ByteArrayResource(path, data, -1L), relocatorList);
        } catch (NopException e) {
            e.printStackTrace();
            throw e;
        }
    }

    void processResource(IResource resource, List<Relocator> relocatorList) throws IOException {
        String path = resource.getPath();
        XNode node = XNodeParser.instance().parseFromResource(resource);
        XDslKeys keys = XDslKeys.of(node);
        String schema = node.attrText(keys.SCHEMA);
        if (path.endsWith(".xdef")) {
            processXDef(node, relocatorList);
            saveNode(path, node);
            return;
        }

        String fileType = StringHelper.fileExt(path);
        if(isXplFile(fileType)){
            node.forEachNode(child->{
                processXplNode(child,relocatorList);
            });
            saveNode(path,node);
            return;
        }

        if (schema == null) {
            beansResources.put(path, resource);
            return;
        }

        IXDefinition xdef = SchemaLoader.loadXDefinition(schema);
        if (xdef == null) {
            beansResources.put(path, resource);
            return;
        }

        if (path.endsWith(".beans.xml")) {
            fixBeanId(node, relocatorList);
        }

        processNode(node, xdef.getRootNode(), relocatorList);
        saveNode(path, node);
    }

    private void processXDef(XNode node, List<Relocator> relocatorList) {
        node.forEachNode(child -> {
            for (Map.Entry<String, ValueWithLocation> entry : child.attrValueLocs().entrySet()) {
                //String name = entry.getKey();
                String value = entry.getValue().asString();
                if (value.startsWith("enum:")) {
                    String typeName = value.substring("enum:".length()).trim();
                    String relocated = relocate(typeName, relocatorList);
                    entry.setValue(ValueWithLocation.of(null, "enum:" + relocated));
                } else if (value.startsWith("io.nop.")) {
                    String relocated = relocate(value, relocatorList);
                    entry.setValue(ValueWithLocation.of(null, "enum:" + relocated));
                }
            }
        });
    }

    private void fixBeanId(XNode node, List<Relocator> relocatorList) {
        node.forEachNode(child -> {
            String id = child.attrText("id");
            if (!StringHelper.isEmpty(id)) {
                String relocated = relocate(id, relocatorList);
                if (!id.equals(relocated)) {
                    child.setAttr("id", relocated);
                }
            }
        });
    }

    private void processNode(XNode node, IXDefNode defNode, List<Relocator> relocatorList) {
        for (Map.Entry<String, ValueWithLocation> entry : node.attrValueLocs().entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue().asString();
            IXDefAttribute attr = defNode.getAttribute(name);
            if (attr != null) {
                String stdDomain = attr.getType().getStdDomain();
                if (isClassName(stdDomain)) {
                    String relocated = relocate(value, relocatorList);
                    if (!value.equals(relocated)) {
                        entry.setValue(ValueWithLocation.of(null, relocated));
                    }
                } else if (isGenericType(stdDomain)) {
                    try {
                        IGenericType genericType = new GenericTypeParser().parseFromText(entry.getValue().getLocation(), value);
                        genericType.resolveClassName(className -> {
                            return relocate(className, relocatorList);
                        });
                        entry.setValue(ValueWithLocation.of(null, genericType.toString()));
                    } catch (NopException e) {
                        e.param("attr", name);
                        throw e;
                    }
                } else if (isDefType(stdDomain)) {
                    XDefTypeDecl decl = XDslParseHelper.parseAttrDefType(node, name,);
                    if (XDefConstants.STD_DOMAIN_ENUM.equals(decl.getStdDomain()) && decl.getOptions() instanceof GenericTypeDomainOptions) {
                        String typeName = ((GenericTypeDomainOptions) decl.getOptions()).getTypeName();
                        String relocated = relocate(typeName, relocatorList);
                        entry.setValue(ValueWithLocation.of(null, "enum:" + relocated));
                    }
                }
            } else {
                if (name.endsWith("packageName") || name.endsWith("PackageName")) {
                    String relocated = relocate(value, relocatorList);
                    if (!value.equals(relocated)) {
                        entry.setValue(ValueWithLocation.of(null, relocated));
                    }
                }
            }
        }

        if (defNode.getXdefValue() != null) {
            String value = node.contentText();
            if (node.hasBody()) {
                String stdDomain = defNode.getXdefValue().getStdDomain();
                if (isClassName(stdDomain)) {
                    String relocated = relocate(value, relocatorList);
                    if (!value.equals(relocated)) {
                        node.content(relocated);
                    }
                } else if (isGenericType(stdDomain)) {
                    IGenericType genericType = new GenericTypeParser().parseFromText(node.getLocation(), value);
                    genericType.resolveClassName(className -> {
                        return relocate(className, relocatorList);
                    });
                    node.content(genericType.toString());
                } else if (isXplType(stdDomain)) {
                    node.forEachNode(child -> {
                        processXplNode(child, relocatorList);
                    });
                }
            }
        }

        for (XNode child : node.getChildren()) {
            IXDefNode childDefNode = defNode.getChild(child.getTagName());
            if (childDefNode != null) {
                processNode(child, childDefNode, relocatorList);
            } else if (child.getTagName().startsWith("x:")) {
                child.forEachNode(c -> {
                    processXplNode(c, relocatorList);
                });
            }
        }
    }

    void processXplNode(XNode node, List<Relocator> relocatorList) {
        String value = node.contentText();
        if (!StringHelper.isEmpty(value)) {
            if (value.contains("import ")) {
                List<String> lines = StringHelper.split(value, '\n');
                lines = transformImport(lines, relocatorList);
                node.content(CDataText.encodeIfNecessary(StringHelper.join(lines, "\n")));
            }
        }

        for (Map.Entry<String, ValueWithLocation> entry : node.attrValueLocs().entrySet()) {
            String attrValue = entry.getValue().asString();
            if (attrValue.startsWith("io.nop.")) {
                String relocated = relocate(attrValue, relocatorList);
                entry.setValue(ValueWithLocation.of(null, relocated));
            }
        }
    }

    List<String> transformImport(List<String> lines, List<Relocator> relocatorList) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().startsWith("import ")) {
                result.add(line);
                continue;
            }

            int pos = line.indexOf("import ");
            String className = line.substring(pos + "import ".length()).trim();
            if (className.endsWith(";"))
                className = className.substring(0, className.length() - 1).trim();

            String relocated = relocate(className, relocatorList);
            line = line.substring(0, pos) + "import " + relocated + ";";

            result.add(line);
        }
        return result;
    }

    boolean isXplFile(String fileType) {
        return "xpl".equals(fileType) || "xgen".equals(fileType) || "xrun".equals(fileType);
    }

    boolean isDefType(String stdDomain) {
        return stdDomain.equals(XDefConstants.STD_DOMAIN_DEF_TYPE);
    }

    boolean isClassName(String stdDomain) {
        return stdDomain.equals(XDefConstants.STD_DOMAIN_CLASS_NAME) || stdDomain.equals(XDefConstants.STD_DOMAIN_PACKAGE_NAME);
    }

    boolean isGenericType(String stdDomain) {
        return stdDomain.equals(XDefConstants.STD_DOMAIN_GENERIC_TYPE);
    }

    boolean isXplType(String stdDomain) {
        return stdDomain.equals("xml") || stdDomain.equals("xpl") || stdDomain.startsWith("xpl-");
    }

    IStdDomainHandler getStdDomainHandler(String stdDomain){
        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
        if(handler == null){
            handler = new
        }
    }
}
