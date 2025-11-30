/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.DefaultClassResolver;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.parse.AbstractResourceParser;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xpl.tags.ImportTagCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_PARSE_MISSING_RESOURCE;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT;

public abstract class AbstractDslParser<T> extends AbstractResourceParser<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractDslParser.class);

    private IXDslNodeLoader modelLoader = DslNodeLoader.INSTANCE;
    protected XLangCompileTool compileTool;
    private IRawTypeResolver rawTypeResolver = DefaultClassResolver.INSTANCE;
    private boolean intern;
    private List<ImportAsDeclaration> importExprs;
    private IXDefinition xdef;
    private String requiredSchema;

    public String getRequiredSchema() {
        return requiredSchema;
    }

    public void setRequiredSchema(String requiredSchema) {
        this.requiredSchema = requiredSchema;
    }

    public void setModelLoader(IXDslNodeLoader modelLoader) {
        this.modelLoader = modelLoader;
    }

    public void setCompileTool(XLangCompileTool compileTool) {
        this.compileTool = compileTool;
    }

    public void setRawTypeResolver(IRawTypeResolver rawTypeResolver) {
        this.rawTypeResolver = rawTypeResolver;
    }

    public IXDslNodeLoader getModelLoader() {
        return modelLoader;
    }

    public XLangCompileTool getCompileTool() {
        return compileTool;
    }

    public AbstractDslParser<T> withCompileTool(XLangCompileTool compileTool) {
        this.setCompileTool(compileTool);
        return this;
    }

    public IRawTypeResolver getRawTypeResolver() {
        return rawTypeResolver;
    }

    public List<ImportAsDeclaration> getImportExprs() {
        return importExprs;
    }

    public void setImportExprs(List<ImportAsDeclaration> importExprs) {
        this.importExprs = importExprs;
    }

    public boolean isIntern() {
        return intern;
    }

    public void setIntern(boolean intern) {
        this.intern = intern;
    }

    protected String intern(String str) {
        if (str != null)
            str = str.intern();
        return str;
    }

    public IXDefinition getXdef() {
        return xdef;
    }

    public void setXdef(IXDefinition xdef) {
        this.xdef = xdef;
    }

    @Override
    protected T doParseResource(IResource resource) {
        XDslExtendResult extendResult = modelLoader.loadFromResource(resource, getRequiredSchema(),
                XDslExtendPhase.validate);
        if (compileTool == null)
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        setXdef(extendResult.getXdef());

        applyCompileConfig(extendResult.getConfig());
        runPreParse(extendResult);

        T parseResult = doParseNode(extendResult.getNode());

        parseResult = runPostParse(parseResult, extendResult);
        return parseResult;
    }

    protected void runPreParse(XDslExtendResult extendResult) {
        XNode rootNode = extendResult.getNode();

        IXLangCompileScope scope = compileTool.getScope();
        scope.setLocalValue(null, XLangConstants.SCOPE_VAR_DSL_ROOT, rootNode);

        boolean runPreParse = false;

        if (getXdef().getXdefPreParse() != null) {
            runPreParse = true;
            getXdef().getXdefPreParse().invoke(scope);
        }

        if (extendResult.getPreParse() != null) {
            IEvalAction preParse = compileTool.compileTagBody(extendResult.getPreParse());
            if (preParse != null) {
                runPreParse = true;
                preParse.invoke(scope);
            }
        }

        for (String name : scope.keySet()) {
            Object value = scope.getLocalValue(name);
            IGenericType type = value == null ? null : ReflectionManager.instance().buildRawType(value.getClass());
            ScopeVarDefinition varDef = ScopeVarDefinition.readOnly(name, type);
            ScopeVarDefinition oldDef = scope.getScopeVarDefinition(name, true);
            if (oldDef != null)
                scope.unregisterScopeVarDefinition(oldDef, true);
            scope.registerScopeVarDefinition(varDef, true);
        }

        if (runPreParse && extendResult.isDump())
            rootNode.dump("run-pre-parse");
    }


    protected void applyCompileConfig(XNode config) {
        if (config == null)
            return;

        if (config.hasContent())
            throw new NopException(ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT).param(ARG_NODE, config);

        importExprs = new ArrayList<>();
        for (XNode child : config.getChildren()) {
            if (!child.getTagName().equals(XLangConstants.TAG_C_IMPORT))
                throw new NopException(ERR_XDSL_CONFIG_CHILD_MUST_BE_IMPORT).param(ARG_NODE, config);

            ImportAsDeclaration expr = ImportTagCompiler.INSTANCE.parseTag(child, compileTool.getCompiler(),
                    compileTool.getScope());
            if (expr != null) {
                importExprs.add(expr);
            }
        }

        String configText = config.innerXml();
        compileTool.setConfigText(configText);
    }

    protected T runPostParse(T parseResult, XDslExtendResult extendResult) {

        if (getXdef().getXdefPostParse() != null) {
            IEvalScope scope = compileTool.getScope();
            scope.setLocalValue(null, XLangConstants.SYS_VAR_DSL_MODEL, parseResult);
            Object ret = getXdef().getXdefPostParse().invoke(scope);
            if (ret != null)
                parseResult = (T) ret;
        }

        if (extendResult.getPostParse() != null) {
            IEvalAction postParse = compileTool.compileTagBody(extendResult.getPostParse());
            if (postParse != null) {
                IEvalScope scope = compileTool.getScope();
                scope.setLocalValue(null, XLangConstants.SYS_VAR_DSL_MODEL, parseResult);
                Object ret = postParse.invoke(scope);
                if (ret != null)
                    parseResult = (T) ret;
            }
        }

        return parseResult;
    }

    public T parseFromNode(XNode node) {
        LOG.debug("nop.core.component.parse-from-node:node={},parser={}", node, getClass());

        long beginTime = CoreMetrics.nanoTime();
        try {
            if (shouldTraceDepends()) {
                T ret = ResourceComponentManager.instance().collectDepends(node.resourcePath(),
                        () -> parseFromNode0(node));
                return ret;
            } else {
                return parseFromNode0(node);
            }
        } catch (NopException e) {
            e.addXplStack(getClass().getSimpleName() + ".parseFromNode(" + node + ")");
            throw e;
        } finally {
            long diff = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.debug("nop.core.component.parse-use-time:tm={}ms,node={},parser={}", CoreMetrics.nanoToMillis(diff),
                    node, getClass());
        }
    }

    public XNode resolveDslNode(XNode node) {
        XDslExtendResult extendResult = modelLoader.loadFromNode(node.cloneInstance(), getRequiredSchema(),
                XDslExtendPhase.validate);
        setXdef(extendResult.getXdef());
        if (compileTool == null)
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

        applyCompileConfig(extendResult.getConfig());
        runPreParse(extendResult);

        return extendResult.getNode();
    }

    public XNode parseNodeFromResource(final IResource resource, boolean ignoreUnknown) {
        this.setResourcePath(resource.getPath());

        if (!resource.exists()) {
            if (ignoreUnknown)
                return null;
            throw new NopException(ERR_COMPONENT_PARSE_MISSING_RESOURCE).param(ARG_RESOURCE_PATH, resource.getPath())
                    .param(ARG_RESOURCE, resource);
        }

        LOG.debug("nop.core.component.begin-parse-node-from-resource:resourcePath={},parser={}", getResourcePath(), getClass());

        long beginTime = CoreMetrics.nanoTime();
        try {
            if (shouldTraceDepends()) {
                return ResourceComponentManager.instance().collectDepends(getResourcePath(),
                        () -> {
                            XNode node = XModelInclude.instance().loadActiveNodeFromResource(resource);
                            return resolveDslNode(node);
                        });
            } else {
                XNode node = XModelInclude.instance().loadActiveNodeFromResource(resource);
                return resolveDslNode(node);
            }
        } catch (NopException e) {
            e.addXplStack(getClass().getSimpleName() + ".parseNodeFromResource(" + getRequiredSchema() + ")");
            throw e;
        } finally {
            long diff = CoreMetrics.nanoTimeDiff(beginTime);

            LOG.info("nop.core.component.finish-parse-node-from-resource:usedTime={},path={},parser={}",
                    CoreMetrics.nanoToMillis(diff), getResourcePath(), getClass());
        }
    }

    protected T parseFromNode0(XNode node) {
        XDslExtendResult extendResult = modelLoader.loadFromNode(node.cloneInstance(), getRequiredSchema(),
                XDslExtendPhase.validate);
        setXdef(extendResult.getXdef());
        if (compileTool == null)
            compileTool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

        applyCompileConfig(extendResult.getConfig());
        runPreParse(extendResult);

        T parseResult = doParseNode(extendResult.getNode());

        parseResult = runPostParse(parseResult, extendResult);
        return parseResult;
    }

    protected abstract T doParseNode(XNode node);
}