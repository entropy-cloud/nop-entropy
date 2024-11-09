/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.IXNodeTransformer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.ResourceConstants;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.delta.OverrideHelper;
import io.nop.xlang.expr.ExprConstants;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdef.impl.XDefHelper;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xpl.utils.XplParseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.type.PredefinedGenericTypes.X_NODE_TYPE;
import static io.nop.xlang.XLangErrors.ARG_EXPECTED;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_PATH;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_XDEF_PATH;
import static io.nop.xlang.XLangErrors.ERR_XDEF_CHILD_NOT_SUPPORT_EXTENDS;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NODE_UNEXPECTED_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_RUN_EXTENDS_RESULT_NOT_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_SUPER_EXTENDS_INVALID_PATH;
import static io.nop.xlang.XLangErrors.ERR_XDSL_SUPER_EXTENDS_NO_CURRENT_PATH;
import static io.nop.xlang.ast.definition.ScopeVarDefinition.readOnly;

/**
 * 执行x:extends展开
 */
public class XDslExtender {
    static final Logger LOG = LoggerFactory.getLogger(XDslExtender.class);

    private final XDslKeys keys;
    private final DeltaMerger merger;

    private List<IXNodeTransformer> transformers;

    public XDslExtender(XDslKeys keys) {
        this.keys = keys;
        this.merger = new DeltaMerger(keys);
    }

    public XDslExtendResult xtend(IXDefinition def, IXDefNode defNode, XNode node, XDslExtendPhase phase,
                                  IEvalScope genScope) {
        if (node.attrBoolean(keys.VALIDATED)) {
            XDslExtendResult result = new XDslExtendResult(keys);
            result.setXdef(def);
            result.setBase(null);
            result.setNode(node);
            normalize(result);
            result.setValidated(true);
            return result;
        }

        node = transformNode(node, def, genScope);

        SourceLocation loc = node.getLocation();

        String currentPath = node.resourcePath();
        XDslExtendResult result = extendNode(def, defNode, node, currentPath, phase, genScope);

        if (phase == XDslExtendPhase.buildBase) {
            normalize(result);
            result.getNode().setLocation(loc);
            return result;
        }

        def = result.getXdef();
        XNode ret = result.getNode();

        ret = processPrototypes(defNode, ret);

        if (phase.ordinal() >= XDslExtendPhase.postExtends.ordinal()) {
            ret = postProcess(def, defNode, ret, currentPath, genScope);
        }

        // 如果执行过x:extends，则标记为replace，从而避免因为父节点的继承点又发生合并的情况。
        // 例如：
        // <root x:extends="a.page">
        // <sub x:extends="b.sub" >
        // <sub_child x:override="append" />
        // </sub>
        // </root>
        // sub处理完自己的继承之后，就不会再合并root节点引入的a.page中的sub节点。
        // 父节点的x:extends引入了一个继承方向，而子节点自身的x:extends又引入了一个不同的继承方向,
        // sub_child上x:override指的的覆盖b.sub中的节点，而不是指覆盖a.page中引入的某个节点。
        ret.setAttr(null, keys.OVERRIDE, XDefOverride.REPLACE);
        ret.setAttr(null, keys.VALIDATED, true);

        boolean dump = XplParseHelper.getAttrBool(ret, keys.DUMP, false);
        if (dump)
            ret.dump("xtend-completed");

        ret.setLocation(loc);
        normalize(result);
        return result;
    }

    List<IXNodeTransformer> loadTransformers(IXDefinition xdef, IEvalScope scope) {
        if (transformers != null)
            return transformers;

        Set<String> transformerClass = xdef.getXdefTransformerClass();
        if (CollectionHelper.isEmpty(transformerClass))
            return null;

        transformers = new ArrayList<>();
        for (String className : transformerClass) {
            IXNodeTransformer transformer = (IXNodeTransformer) scope.getClassModelLoader().loadClassModel(className).newInstance();
            transformers.add(transformer);
        }
        return transformers;
    }

    XNode transformNode(XNode node, IXDefinition def, IEvalScope scope) {
        List<IXNodeTransformer> transformers = loadTransformers(def, scope);
        if (transformers == null)
            return node;
        for (IXNodeTransformer txn : transformers) {
            node = txn.transform(node);
        }
        return node;
    }

    void normalize(XDslExtendResult result) {
        XNode node = result.getNode();
        XNode config = node.uniqueChild(keys.CONFIG);
        XNode postExtends = node.uniqueChild(keys.POST_EXTENDS);
        XNode postParse = node.uniqueChild(keys.POST_PARSE);

        if (config != null) {
            config.detach();
        }

        if (postExtends != null) {
            postExtends.detach();
        }

        if (postParse != null) {
            postParse.detach();
        }

        node.normalizeText(true);

        result.setConfig(config);
        result.setPostExtends(postExtends);
        result.setPostParse(postParse);
    }

    private XDslExtendResult extendNode(IXDefinition def, IXDefNode defNode, XNode node, String currentPath,
                                        XDslExtendPhase phase, IEvalScope genScope) {
        SourceLocation loc = node.getLocation();
        String extendsPath = node.attrText(keys.EXTENDS);
        XNode genExtends = node.childByTag(keys.GEN_EXTENDS);

        XDslSource source = buildSource(def, node, currentPath, genScope);

        List<XDslSource> extendsList = source.getLinearizedExtends();

        // 从最外层向内查找IXDefinition
        if (def == null) {
            def = source.getXDef();
            if (def == null) {
                for (int i = extendsList.size() - 1; i >= 0; i--) {
                    def = extendsList.get(i).getXDef();
                    if (def != null)
                        break;
                }
            }
        }

        // 由最外层的IXDefinition提供defaultExtends
        XNode base = null;
        if (!source.isIgnoreDefaultExtends() && def != null) {
            base = def.getDefaultExtendsNode();
            if (base != null)
                base = base.cloneInstance();
        }

        // 从内向外逐层合并
        for (XDslSource extendsNode : extendsList) {
            checkNodeName(defNode, extendsNode.getNode());
            base = this.mergeNode(base, extendsNode.getNode(), defNode, false);
        }

        XNode merged = source.getNode();
        if (phase.ordinal() >= XDslExtendPhase.mergeBase.ordinal()) {
            checkNodeName(defNode, source.getNode());
            merged = this.mergeNode(base, source.getNode(), defNode, false);
        }

        merged.setLocation(loc);

        XDslExtendResult result = new XDslExtendResult(keys);
        result.setXdef(def);
        result.setBase(base);
        result.setNode(merged);
        result.setExtendsPath(extendsPath);
        result.setGenExtends(genExtends);
        return result;
    }

    void checkNodeName(IXDefNode defNode, XNode node) {
        if (defNode == null)
            return;

        if (defNode.getTagName().equals("*"))
            return;

        if (!defNode.getTagName().equals(node.getTagName())) {
            throw new NopException(ERR_XDSL_NODE_UNEXPECTED_TAG_NAME).param(ARG_NODE, node)
                    .param(ARG_TAG_NAME, node.getTagName()).param(ARG_EXPECTED, defNode.getTagName());
        }
    }

    private XDslSource buildSource(IXDefinition def, XNode node, String currentPath, IEvalScope genScope) {
        if (def == null) {
            // 如果没有指定IXDefinition，则根据x:schema属性加载IXDefinition
            def = loadXDef(node);
        }

        XDslSource source = new XDslSource(node, def);
        Set<String> extendsList = XplParseHelper.getAttrCsvSet(node, keys.EXTENDS);
        if (extendsList != null) {
            SourceLocation loc = node.removeAttr(keys.EXTENDS).getLocation();
            for (String extendPath : extendsList) {
                // 如果设置了x:extends="none"，则后续合并时会忽略defaultExtends
                if (extendPath.equals(XDslConstants.EXTENDS_NONE)) {
                    source.setIgnoreDefaultExtends(true);
                    source.clearStaticExtends();
                    continue;
                }

                // 如果设置了x:extends="super"，则实际加载路径为super:{currentPath}
                if (extendPath.equals(XDslConstants.EXTENDS_SUPER)) {
                    if (StringHelper.isEmpty(currentPath)) {
                        throw new NopException(ERR_XDSL_SUPER_EXTENDS_NO_CURRENT_PATH).param(ARG_NODE, node);
                    }
                    if (!StringHelper.isValidVPath(currentPath))
                        throw new NopException(ERR_XDSL_SUPER_EXTENDS_INVALID_PATH).param(ARG_NODE, node)
                                .param(ARG_PATH, currentPath);
                    extendPath = ResourceConstants.SUPER_NS + ':' + currentPath;
                } else {
                    extendPath = loc == null ? extendPath : StringHelper.absolutePath(loc.getPath(), extendPath);
                }
                XDslSource extendSource = loadSource(def, extendPath, genScope);
                source.addStaticSource(extendSource);
            }
        }

        XNode childExtends = source.getNode().uniqueChild(keys.GEN_EXTENDS);
        if (childExtends != null) {
            List<XDslSource> genSources = genCpExtends(def, node, childExtends, currentPath, genScope);
            // 一旦生成完毕，x:gen-extends节点将被删除
            childExtends.detach();
            for (XDslSource genSource : genSources) {
                source.addDynamicSource(genSource);
            }
        }

        extendsSub(def == null ? null : def.getRootNode(), source.getNode(), genScope);
        return source;
    }

    private IXDefinition loadXDef(XNode node) {
        String schemaPath = node.attrText(keys.SCHEMA);
        if (schemaPath == null)
            return null;
        return SchemaLoader.loadXDefinition(schemaPath);
    }

    private XDslSource loadSource(IXDefinition def, String path, IEvalScope genScope) {
        XNode node = XModelInclude.instance().loadActiveNode(path);
        node = transformNode(node, def, genScope);
        return buildSource(def, node, node.resourcePath(), genScope);
    }

    private List<XDslSource> genCpExtends(IXDefinition def, XNode root, XNode node, String currentPath,
                                          IEvalScope genScope) {
        // 编译期执行不需要优化。
        XLangCompileTool tool = XLang.newCompileTool().disableOptimize();
        IXLangCompileScope cpScope = tool.getScope();
        // 允许输出未知的节点，且总是采用outputMode=node模式编译。在编译期增加xroot和xnode两个变量
        cpScope.setAllowUnknownTag(true);
        cpScope.setOutputMode(XLangOutputMode.node);
        cpScope.registerScopeVarDefinition(readOnly(ExprConstants.SCOPE_VAR_DSL_ROOT, X_NODE_TYPE), false);
        cpScope.registerScopeVarDefinition(readOnly(ExprConstants.SCOPE_VAR_XPL_NODE, X_NODE_TYPE), false);

        ExprEvalAction action = tool.compileTagBody(node);
        if (action == null)
            return Collections.emptyList();

        IEvalScope scope = genScope.newChildScope();
        scope.setLocalValue(node.getLocation(), ExprConstants.SCOPE_VAR_DSL_ROOT, root);
        scope.setLocalValue(node.getLocation(), ExprConstants.SCOPE_VAR_XPL_NODE, node);

        XNode extendsNode = action.generateNode(scope);
        if (extendsNode == null || !extendsNode.hasBody())
            return Collections.emptyList();

        boolean dump = XplParseHelper.getAttrBool(root, keys.DUMP, false);
        if (dump)
            extendsNode.dump("run-dynamic-extends");

        extendsNode.renameNsPrefix(XDslConstants.NS_XDSL_PREFIX, keys.X_NS_PREFIX);

        if (extendsNode.hasContent())
            throw new NopException(ERR_XDSL_RUN_EXTENDS_RESULT_NOT_NODE).param(ARG_NODE, extendsNode);

        if (AppConfig.isDebugMode())
            addPathRef(extendsNode, currentPath);

        List<XNode> genExtends = extendsNode.detachChildren();
        List<XDslSource> ret = new ArrayList<>(genExtends.size());
        for (XNode gen : genExtends) {
            if (XplParseHelper.getAttrBool(gen, keys.DUMP, false)) {
                gen.dump("run-dynamic-extends.renamed-xdsl-ns");
            }
            ret.add(buildSource(def, gen, currentPath, scope));
        }
        return ret;
    }

    void addPathRef(XNode node, String ref) {
        if (node.frozen())
            return;

        SourceLocation loc = node.getLocation();
        if (loc != null) {
            loc = loc.addRef(ref);
            node.setLocation(loc);
        }

        if (node.hasAttr()) {
            Map<String, ValueWithLocation> attrs = new LinkedHashMap<>();
            node.forEachAttr((name, vl) -> attrs.put(name, vl.addRef(ref)));
            node.attrValueLocs(attrs);
        }

        for (XNode child : node.getChildren()) {
            addPathRef(child, ref);
        }
    }

    // 处理子节点上的x:extends
    private void extendsSub(IXDefNode defNode, XNode node, IEvalScope genScope) {
        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            // 即使child是x:exp-extends节点，它内部的x:extends也会被自动处理！
            // 也就是说，x:extends和x:exp-extends的处理顺序先于x:exp-extends实际执行的时刻
            if (containsExtends(child)) {
                IXDefinition subDef = null;
                IXDefNode childDef = null;
                if (defNode != null) {
                    childDef = defNode.getChild(child.getTagName());
                    if (childDef != null && !childDef.isSupportExtends())
                        throw new NopException(ERR_XDEF_CHILD_NOT_SUPPORT_EXTENDS).param(ARG_NODE, child)
                                .param(ARG_XDEF_PATH, childDef.getLocation());

                    if (childDef != null) {
                        String ref = childDef.getXdefRef();
                        if (!XDefHelper.isLocalRef(ref)) {
                            subDef = SchemaLoader.loadXDefinition(ref);
                        }
                    }
                }
                XNode sub = xtend(subDef, childDef, child, XDslExtendPhase.mergeBase, genScope).getNode();
                child.replaceBy(sub);
            } else {
                extendsSub(null, child, genScope);
            }
        }
    }

    private boolean containsExtends(XNode node) {
        if (!StringHelper.isBlank(node.attrText(keys.EXTENDS))) {
            return true;
        }

        XNode child = node.childByTag(keys.GEN_EXTENDS);
        if (child != null && child.hasBody())
            return true;

        return false;
    }

    private XNode mergeNode(XNode xa, XNode xb, IXDefNode defNode, boolean forPrototype) {
        if (xa == null)
            return xb;

        SourceLocation oldLoc = xa.getLocation();
        SourceLocation newLoc = xb.getLocation();

        merger.merge(xa, xb, defNode, forPrototype);
        xa.setLocation(xb.getLocation());

        boolean dump = XplParseHelper.getAttrBool(xa, keys.DUMP, false);
        if (dump) {
            LOG.info("nop.xdsl.merge-node:oldLoc={},newLoc={}", oldLoc, newLoc);
            xa.dump("xtend-merge-result");
        }
        return xa;
    }

    private XNode postProcess(IXDefinition def, IXDefNode defNode, XNode node, String currentPath,
                              IEvalScope genScope) {
        XNode ret = node;

        // 必须确保post-extends处理时所有其他xtend逻辑都已经执行，这样才能保证post-extends在确定性的基础上执行。
        XNode childExtends = node.uniqueChild(keys.POST_EXTENDS);
        if (childExtends != null) {
            List<XDslSource> genSources = genCpExtends(def, node, childExtends, currentPath, genScope);
            childExtends.detach();
            genSources = XDslSource.collectAllExtends(genSources);

            for (XDslSource extendsNode : genSources) {
                ret = this.mergeNode(ret, extendsNode.getNode(), defNode, false);
            }
        }

        return ret;
    }

    private XNode processPrototypes(IXDefNode defNode, XNode node) {
        cleanRemoved(node);
        merger.processPrototype(node, defNode);
        return node;
    }

    private void cleanRemoved(XNode node) {
        for (int i = 0, n = node.getChildCount(); i < n; i++) {
            XNode child = node.child(i);
            XDefOverride override = OverrideHelper.getOverride(child, keys.OVERRIDE);
            if (override == XDefOverride.REMOVE) {
                node.removeChild(child);
                i--;
                n--;
            } else {
                cleanRemoved(child);
            }
        }
    }
}