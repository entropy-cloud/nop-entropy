/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.gen;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.table.ITableView;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.utils.DebugHelper;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.ooxml.common.OfficeErrors.ARG_NODE;
import static io.nop.ooxml.common.OfficeErrors.ARG_NS;
import static io.nop.ooxml.common.OfficeErrors.ARG_NS_LIST;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_LIB_NAMESPACE_CONFLICT_WITH_INTERNAL_NAMESPACE;

public class XplGenConfig extends AbstractComponentModel {
    private boolean dump;
    private String dumpFile;
    private List<String> importLibs;

    private XNode beforeGen;
    private XNode afterGen;

    private boolean deleteAllAfterConfigTable;

    public String getDumpFile() {
        return dumpFile;
    }

    public void setDumpFile(String dumpFile) {
        this.dumpFile = dumpFile;
    }

    public boolean isDump() {
        return dump;
    }

    public void setDump(boolean dump) {
        this.dump = dump;
    }

    public boolean isDeleteAllAfterConfigTable() {
        return deleteAllAfterConfigTable;
    }

    public void setDeleteAllAfterConfigTable(boolean deleteAllAfterConfigTable) {
        this.deleteAllAfterConfigTable = deleteAllAfterConfigTable;
    }

    public XNode checkDump(XNode node, String title) {
        if (dump) {
            if (!StringHelper.isBlank(dumpFile)) {
                File file = new File(dumpFile);
                node = DebugHelper.reparse(node, new FileResource(file));
            } else {
                node.dump(title);
            }
        }
        return node;
    }

    public List<String> getImportLibs() {
        return importLibs;
    }

    public void setImportLibs(List<String> importLibs) {
        this.importLibs = importLibs == null ? null : new ArrayList<>(importLibs);
    }

    public void addImportLib(String lib) {
        if (importLibs == null || importLibs.isEmpty())
            importLibs = new ArrayList<>();
        importLibs.add(lib);
    }

    public XNode getBeforeGen() {
        return beforeGen;
    }

    public void setBeforeGen(XNode beforeGen) {
        this.beforeGen = beforeGen;
    }

    public XNode getAfterGen() {
        return afterGen;
    }

    public void setAfterGen(XNode afterGen) {
        this.afterGen = afterGen;
    }

    public static XplGenConfig parseFromTable(ITableView table) {
        return new XplGenConfigParser().parseFromTable(table);
    }

    public ITextTemplateOutput compile(XNode doc) {
        return compile(doc, XLangOutputMode.xml);
    }

    public ITextTemplateOutput compile(XNode doc, XLangOutputMode outputMode) {
        XLangCompileTool cp = XLang.newCompileTool();
        cp.allowUnregisteredScopeVar(true);
        // 收集word模板内置的名字空间，避免它们被解释为xpl标签。引入的标签库的名字空间也应该需要回避这些namespace。
        Set<String> ns = collectNs(doc);
        cp.getScope().disableNs(ns);

        XNode root = XNode.make("c:unit");

        if (importLibs != null) {
            for (String lib : importLibs) {
                String namespace = XplLibHelper.getNamespaceFromLibPath(lib);
                if (ns.contains(namespace))
                    throw new NopException(ERR_OOXML_LIB_NAMESPACE_CONFLICT_WITH_INTERNAL_NAMESPACE)
                            .param(ARG_NS, namespace).param(ARG_NS_LIST, ns).param(ARG_NODE, doc);
                XNode libNode = XNode.make("c:import");
                libNode.setAttr("from", lib);
                root.appendChild(libNode);
            }
        }

        XNode child = root.makeChild("c:out");
        child.setAttr("escape", "none");
        child.content("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (beforeGen != null) {
            root.appendChild(beforeGen.cloneInstance());
        }
        root.appendChild(doc);
        if (afterGen != null) {
            root.appendChild(afterGen.cloneInstance());
        }

        root = checkDump(root, "compile:===========================================");

        ITextTemplateOutput output = cp.compileTag(root, outputMode);

        return output;
    }

    Set<String> collectNs(XNode node) {
        Set<String> ret = new HashSet<>();
        node.forEachNode(child -> {
            child.forEachAttr((name, value) -> {
                if (name.startsWith(CoreConstants.NS_XMLNS_PREFIX)) {
                    if (value.toString().startsWith("http://")) {
                        String ns = name.substring(CoreConstants.NS_XMLNS_PREFIX.length());
                        ret.add(ns);
                    }
                }
            });
        });
        return ret;
    }
}