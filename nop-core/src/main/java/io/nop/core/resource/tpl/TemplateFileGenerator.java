/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.tpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.io.stream.DiscardOutputStream;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.loop.INestedLoop;
import io.nop.core.model.loop.INestedLoopVar;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.cache.IResourceContentCache;
import io.nop.core.resource.cache.ResourceContentCache;
import io.nop.core.resource.component.IResourceDependencyManager;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.nop.core.CoreConstants.POSTFIX_NOT_DELETE;
import static io.nop.core.CoreConstants.VAR_TARGET_RESOURCE;
import static io.nop.core.CoreConstants.XGEN_FILE_DIR;
import static io.nop.core.CoreConstants.XGEN_FILE_PREFIX;
import static io.nop.core.CoreConstants.XGEN_FILE_SUFFIX;
import static io.nop.core.CoreConstants.XGEN_MARK_FORCE_OVERRIDE;
import static io.nop.core.CoreConstants.XGEN_MARK_TPL_FORCE_OVERRIDE;
import static io.nop.core.CoreConstants.XRUN_FILE_SUFFIX;

public class TemplateFileGenerator {
    static final Logger LOG = LoggerFactory.getLogger(TemplateFileGenerator.class);
    static final SourceLocation s_loc = SourceLocation.fromClass(TemplateFileGenerator.class);

    private final ITemplateLoader loader;
    private final String targetRootPath;
    private final String tplRootPath;

    private boolean autoFormat = true;

    protected IResourceContentCache contentCache;

    private IResourceLoader tplResourceLoader = VirtualFileSystem.instance();

    private IResourceLoader targetResourceLoader = VirtualFileSystem.instance();

    protected IResourceDependencyManager dependencyManager;

    private boolean forceOverride;

    private boolean checkOverrideHead = true;

    private Map<String, Boolean> tplForceOverrides = new ConcurrentHashMap<>();

    public TemplateFileGenerator(ITemplateLoader loader, String tplRootPath, String targetRootPath) {
        this.loader = loader;
        this.tplRootPath = Guard.notEmpty(tplRootPath, "tplRootPath");
        this.targetRootPath = Guard.notEmpty(normalizeTargetRootDir(targetRootPath), "targetRootPath");
    }

    static String normalizeTargetRootDir(String path) {
        return ResourceHelper.resolveRelativePath(path);
    }

    protected ITemplateLoader getLoader() {
        return loader;
    }

    public TemplateFileGenerator withDependsCache() {
        return this.withDependencyManager().withContentCache();
    }

    public TemplateFileGenerator withDependencyManager(IResourceDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
        return this;
    }

    public TemplateFileGenerator autoFormat(boolean autoFormat) {
        this.autoFormat = autoFormat;
        return this;
    }

    public TemplateFileGenerator checkOverrideHead(boolean checkOverrideHead) {
        this.checkOverrideHead = checkOverrideHead;
        return this;
    }

    public boolean isCheckOverrideHead(){
        return checkOverrideHead;
    }

    public boolean isAutoFormat(){
        return autoFormat;
    }

    public TemplateFileGenerator withDependencyManager() {
        return this.withDependencyManager(ResourceComponentManager.instance());
    }

    public TemplateFileGenerator forceOverride(boolean b) {
        this.forceOverride = b;
        return this;
    }

    public boolean isForceOverride() {
        return forceOverride;
    }

    public TemplateFileGenerator withContentCache(IResourceContentCache cache) {
        this.contentCache = cache;
        return this;
    }

    public TemplateFileGenerator withContentCache() {
        return withContentCache(ResourceContentCache.instance());
    }

    public TemplateFileGenerator tplResourceLoader(IResourceLoader loader) {
        this.tplResourceLoader = loader;
        return this;
    }

    public TemplateFileGenerator targetResourceLoader(IResourceLoader loader) {
        this.targetResourceLoader = loader;
        return this;
    }

    public IResourceLoader getTplResourceLoader() {
        return tplResourceLoader;
    }

    public IResourceLoader getTargetResourceLoader() {
        return targetResourceLoader;
    }

    public String getTargetRootPath() {
        return targetRootPath;
    }

    public String getTplRootPath() {
        return tplRootPath;
    }

    public void executeWithLoop(String tplPath, INestedLoop loop, IEvalScope scope) {
        Guard.notNull(loop, "loop");

        // 找到根目录
        IResource resource = getTplResource("");
        if (!resource.exists()) {
            LOG.warn("nop.tpl.execute-is-skipped-since-resource-not-exists:resource={}", resource);
            return;
        }

        if (tplPath.equals("") || tplPath.equals("/")) {
            processDir(new TemplateGenPath(), resource, loop, scope);
        } else {
            // 如果指定了只处理某个子目录或者文件
            List<String> parts = StringHelper.split(tplPath, '/');
            parts = parts.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());
            skipParentDir(new TemplateGenPath(), parts, 0, loop, scope);
        }

    }

    private void skipParentDir(TemplateGenPath genPath, List<String> parts, int index,
                               INestedLoop loop, IEvalScope scope) {
        IResource subDir = getTplResource(StringHelper.join(parts.subList(0, index + 1), "/"));
        if (index >= parts.size() - 1) {
            processDirOrFile(genPath, subDir, loop, scope);
        } else {
            processTpl(genPath, subDir, loop, scope, newLoop -> {
                skipParentDir(genPath, parts, index + 1, loop, scope);
            });
        }
    }

    public IResource getTplResource(String path) {
        return tplResourceLoader.getResource(StringHelper.appendPath(tplRootPath, path));
    }

    public Collection<? extends IResource> getResourceChildren(IResource resource) {
        return tplResourceLoader.getChildren(resource.getStdPath());
    }

    public IResource getTargetResource(String path) {
        return targetResourceLoader.getResource(StringHelper.appendPath(targetRootPath, path));
    }

    void processDirOrFile(TemplateGenPath genPath, IResource resource, INestedLoop loop, IEvalScope scope) {
        if (shouldIgnoreTpl(resource))
            return;

        if (resource.isDirectory()) {
            processTpl(genPath, resource, loop, scope, (newLoop) -> {
                processDir(genPath, resource, newLoop, scope);
            });
        } else {
            processFile(genPath, resource, loop, scope);
        }
    }

    void processDir(TemplateGenPath genPath, IResource resource, INestedLoop loop, IEvalScope scope) {
        Collection<? extends IResource> children = getResourceChildren(resource);
        if (children != null) {
            for (IResource child : children) {
                processDirOrFile(genPath, child, loop, scope);
            }
        }
    }

    protected boolean shouldIgnoreTpl(IResource tpl) {
        return tpl.getName().startsWith("@");
    }

    INestedLoop buildLoop(TemplateGenPath genPath, INestedLoop loop) {
        try {
            return loop.loopForVars(genPath.getTopVarNames());
        } catch (NopException e) {
            e.addXplStack("tpl.renderFile:" + genPath.getTplPath());
            throw e;
        }
    }

    void processTpl(TemplateGenPath genPath, IResource resource, INestedLoop loop, IEvalScope scope,
                    Consumer<INestedLoop> consumer) {
        String name = resource.getName();
        if (name.endsWith(XGEN_FILE_SUFFIX)) {
            name = StringHelper.removeTail(name, XGEN_FILE_SUFFIX);
        }
        genPath.push(name);

        loop = buildLoop(genPath, loop);

        for (INestedLoopVar loopVar : loop) {
            loopVar.forSelfAndParents(var -> {
                scope.setLocalValue(s_loc, var.getVarName(), var.getVarValue());
            });

            // 如果生成的输出路径为IGNORE或者空目录，则表示该路径需要被跳过
            if (!genPath.resolveTop(scope))
                continue;

            consumer.accept(loopVar.loopForVar(null));
        }
        genPath.pop(name);
    }

    void processFile(TemplateGenPath genPath, IResource resource, INestedLoop loop, IEvalScope scope) {
        processTpl(genPath, resource, loop, scope, (newLoop) -> {
            String targetPath = genPath.getTargetPath();
            IResource targetFile = getTargetResource(targetPath);

            if (!isAllowWrite(resource, targetFile, targetPath, isTextFile(resource))) {
                LOG.debug("nop.tpl.skip-resource-since-not-allow-write:resource={}", targetFile);
                return;
            }

            if (dependencyManager != null && !forceOverride) {
                dependencyManager.runWhenDependsChanged(targetFile.getPath(), () -> {
                    dependencyManager.collectDepends(targetFile.getPath(), () -> {
                        renderTemplate(resource, targetFile, scope);
                        return null;
                    });
                    return null;
                });
            } else {
                renderTemplate(resource, targetFile, scope);
            }
        });
    }

    protected boolean isTextFile(IResource tpl) {
        return true;
    }

    protected boolean isTplFile(IResource resource) {
        return resource.getName().endsWith(XGEN_FILE_SUFFIX) || resource.getName().endsWith(XRUN_FILE_SUFFIX);
    }

    protected void renderTemplate(IResource resource, IResource targetFile, IEvalScope scope) {
        if (!isTplFile(resource)) {
            LOG.info("nop.tpl.copy-file:from={},target={}", resource, targetFile);
            resource.saveToResource(targetFile);
            return;
        }

        scope.setLocalValue(null, VAR_TARGET_RESOURCE, targetFile);
        if (resource.getName().endsWith(XRUN_FILE_SUFFIX)) {
            // xrun文件表示忽略其直接输出内容
            ITemplateOutput tpl = loader.getTemplate(resource);
            if (tpl != null) {
                try {
                    tpl.generateToStream(new DiscardOutputStream(), scope);
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
            }
            return;
        }

        ITemplateOutput tpl = loader.getTemplate(resource);
        boolean removeEmpty = !shouldKeepEmptyTargetFile(targetFile);
        if (tpl instanceof ITextTemplateOutput) {
            // 总是在内存中生成文本，避免报错的时候产生不完整的输出文件
            String text = ((ITextTemplateOutput) tpl).generateText(scope);
            text = normalizeText(text, targetFile);

            LOG.debug("nop.tpl.update-resource-text:tplFile={},targetFile={},len={}", resource, targetFile,
                    text.length());
            if (contentCache != null) {
                contentCache.updateCachedText(targetFile, text, true, removeEmpty);
            } else {
                if (text.length() <= 0 && removeEmpty) {
                    LOG.info("nop.tpl.remove-empty-resource:targetFile={}", targetFile);
                    deleteTargetResource(targetFile);
                } else {
                    if (isNotChange(targetFile, text)) {
                        LOG.info("nop.tpl.skip-write-resource-since-text-not-change:tplFile={},targetFile={},len={}",
                                resource, targetFile, text.length());
                    } else {
                        targetFile.writeText(text, null);
                    }
                }
            }
        } else {
            if (tpl != null) {
                try {
                    tpl.generateToResource(targetFile, scope);
                } catch (Exception e) {
                    // 如果生成过程出现异常，则目标文件可能处于损坏的状态
                    targetFile.delete();
                    throw NopException.adapt(e);
                }
            }
            if (targetFile.length() <= 0 && removeEmpty) {
                LOG.info("nop.tpl.remove-empty-resource:targetFile={}", targetFile);
                deleteTargetResource(targetFile);
            }
        }
    }

    protected void deleteTargetResource(IResource resource) {
        resource.delete();
    }

    protected String normalizeText(String text, IResource resource) {
        if (!autoFormat)
            return text;

        SourceLocation loc = SourceLocation.fromPath(resource.getPath());

        String name = resource.getName();
        if (name.endsWith(".json") || name.endsWith(".json5")) {
            try {
                Object bean = JsonTool.parseNonStrict(text);
                return JsonTool.serialize(bean, true);
            } catch (Exception e) {
                // ignore
                LOG.debug("nop.err.resource.gen.invalid-json:path={},\n{}", resource.getPath(), text, e);
                return text;
            }
        }

        /*
         * janino的格式化没有注释，无法使用 if (name.endsWith(".java")) { try { return
         * JavaCompileTool.instance().formatJavaSource(loc, text); } catch (Exception e) {
         * LOG.debug("nop.err.resource.gen.invalid-java:\n{}", text, e); return text; } }
         */

        if (maybeXml(text)) {
            try {
                XNode node = XNodeParser.instance().keepComment(true).parseFromText(loc, text);
                StringBuilder sb = new StringBuilder();
                node.saveToWriter(sb);
                return sb.toString();
            } catch (Exception e) {
                LOG.debug("nop.err.resource.gen.invalid-xml:path={},\n{}", resource.getPath(), text, e);
                return text;
            }
        }
        return text;
    }

    protected boolean maybeXml(String text) {
        boolean empty = true;
        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                if (c != '<')
                    return false;
                empty = false;
                break;
            }
        }

        for (int i = text.length() - 1; i > 0; i--) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                if (c != '>')
                    return false;
                break;
            }
        }

        return !empty;
    }

    protected boolean isNotChange(IResource targetFile, String text) {
        if (targetFile.exists() && targetFile.readText().equals(text)) {
            return true;
        }
        return false;
    }

    /**
     * 目标文件的依赖如果没有发生变化，则可以不用重新生成
     *
     * @param targetFile 生成的目标文件
     * @return 目标文件的依赖是否已经发生变化
     */
    public boolean isTargetUpToDate(IResource targetFile) {
        if (forceOverride || !targetFile.exists())
            return false;

        if (dependencyManager != null) {
            return !dependencyManager.isDependencyChanged(targetFile.getPath());
        }

        return false;
    }

    protected boolean shouldKeepEmptyTargetFile(IResource targetFile) {
        return targetFile.getName().endsWith(POSTFIX_NOT_DELETE);
    }

    /**
     * 满足如下条件时允许模板重新生成
     * <ul>
     * <li>如果强制要求更新或者目标文件不存在</li>
     * <li>文件名以_为前缀</li>
     * <li>生成到_gen目录下</li>
     * <li>模板文件头部标注了强制覆盖，或者目标文件头部标注了强制覆盖</li>
     * </ul>
     *
     * @param tplFile    模板文件
     * @param targetFile 目标文件
     * @param targetPath 目标文件在输出目录下的全路径
     * @param textFile   是否输出文本文件
     * @return true表示允许重新生成，否则该文件不用重新生成，可以直接跳过处理
     */
    protected boolean isAllowWrite(IResource tplFile, IResource targetFile, String targetPath, boolean textFile) {
        if (forceOverride || !targetFile.exists())
            return true;

        if (targetFile.getName().startsWith(XGEN_FILE_PREFIX)) {
            return true;
        }

        if (targetPath.contains(XGEN_FILE_DIR) || targetPath.startsWith("_gen/"))
            return true;

        if (checkOverrideHead && textFile) {
            String targetText = readTextHeader(targetFile);
            if (targetText.isEmpty())
                return true;

            if (targetText.contains(XGEN_MARK_FORCE_OVERRIDE))
                return true;

            // 模板本身可以指定需要强制覆盖
            boolean tplOverride = tplForceOverrides.computeIfAbsent(tplFile.getPath(), p -> {
                String srcText = readTextHeader(tplFile);
                if (srcText.contains(XGEN_MARK_TPL_FORCE_OVERRIDE))
                    return true;
                return false;
            });

            if (tplOverride)
                return true;
        }

        return false;
    }

    protected String readTextHeader(IResource resource) {
        if (contentCache != null) {
            String text = contentCache.getCachedText(resource, true);
            return StringHelper.head(text, 200);
        }
        return ResourceHelper.readTextHeader(resource, null, 200);
    }
}