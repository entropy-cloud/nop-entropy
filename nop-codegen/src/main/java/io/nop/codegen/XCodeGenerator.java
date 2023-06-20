/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.loop.INestedLoop;
import io.nop.core.model.loop.impl.NestedLoop;
import io.nop.core.model.loop.model.NestedLoopModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.tpl.ITemplateLoader;
import io.nop.core.resource.tpl.TemplateFileGenerator;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.nop.codegen.CodeGenErrors.ARG_FILE_NAME;
import static io.nop.codegen.CodeGenErrors.ERR_CODEGEN_UNKNOWN_MODEL_TYPE;

/**
 * 数据驱动的代码生成器，也就是说有哪些文件要生成，生成过程中的循环和判断逻辑都完全由数据模板来指定，而不是直接内置在代码生成器的逻辑中。
 * <p>
 * 1. 通过@init.xrun配置文件执行初始化逻辑
 * <p>
 * 2. 目录名通过{varName}形式来表达嵌套循环逻辑和开关控制逻辑
 * <p>
 * 3. 约定以_为前缀的文件以及目录结构中包含/_gen/的文件总是被覆盖。另外在文件头中标记了XGEN_MARK_FORCE_OVERRIDE的总是被覆盖
 * <p>
 * 4. 以.xgen为后缀的文件按照xpl模板来解析执行，利用xpl的扩展能力来实现生成器逻辑的封装和扩展.
 * <p>
 * 5. 缺省情况下，空文件将会被自动删除。
 * <p>
 * 6. 代码生成器会自动跟踪每个目标文件生成时所依赖的所有模型文件，只有当模型文件变化时，才会重新生成对应的目标文件
 * <p>
 * 7. 代码生成器会自动缓存所有生成的文本文件的内容，如果再次生成的text和上次相同，则会跳过文件写入过程，减少文件IO。
 */
public class XCodeGenerator extends TemplateFileGenerator {
    static final SourceLocation s_loc = SourceLocation.fromClass(XCodeGenerator.class);

    static final Logger LOG = LoggerFactory.getLogger(XCodeGenerator.class);

    public XCodeGenerator(ITemplateLoader loader, String tplRootPath, String targetRootPath) {
        super(loader, tplRootPath, targetRootPath);
    }

    public XCodeGenerator(String tplRootPath, String targetRootPath) {
        this(XCodeGenerator::loadTpl, tplRootPath, targetRootPath);
    }

    static XplModel loadTpl(IResource resource) {
        return (XplModel) ResourceComponentManager.instance().loadComponentModel(resource.getStdPath());
    }

    public static XCodeGenerator forPrecompile(String projectPath, boolean toTarget) {
        String tplRootPath = StringHelper.appendPath(projectPath, "precompile");
        String targetRootPath = StringHelper.appendPath(projectPath, toTarget ? "target/xgen" : "");
        XCodeGenerator generator = new XCodeGenerator(tplRootPath, targetRootPath);
        if (toTarget)
            generator.forceOverride(true);
        return generator;
    }

    public static XCodeGenerator forPostcompile(String projectPath, boolean toTarget) {
        String tplRootPath = StringHelper.appendPath(projectPath, "postcompile");
        String targetRootPath = StringHelper.appendPath(projectPath, toTarget ? "target/xgen" : "");
        XCodeGenerator generator = new XCodeGenerator(tplRootPath, targetRootPath);
        if (toTarget)
            generator.forceOverride(true);
        return generator;
    }

    /**
     * 以项目目录为模板路径和输出路径
     *
     * @param projectDir 项目的根目录。模板路径和目标文件路径都是此目录下的相对路径
     * @param tplPath    projectDir下的某个模板文件路径
     * @param targetPath projectDir的子目录。代码将生成到此子目录下
     */
    public static void runProjectFile(File projectDir, String tplPath, String targetPath) {
        String projectPath = FileHelper.getFileUrl(projectDir);
        String targetRootPath = FileHelper.getFileUrl(new File(projectDir, targetPath));
        XCodeGenerator generator = new XCodeGenerator(projectPath, targetRootPath);
        if (targetPath.startsWith("target/"))
            generator.forceOverride(true);
        generator.execute(tplPath, XLang.newEvalScope());
    }

    /**
     * 从项目的precompile目录装载模板，生成到项目目录下
     */
    public static void runPrecompile(File projectDir, String subPath, boolean toTarget) {
        String projectPath = FileHelper.getFileUrl(projectDir);
        forPrecompile(projectPath, toTarget).execute(subPath, XLang.newEvalScope());
    }

    /**
     * 从项目的postcompile目录装载模板，生成到项目目录下。postcompile假设工程已经成功编译，因此可以通过反射访问工程中的Java类
     */
    public static void runPostcompile(File projectDir, String subPath, boolean toTarget) {
        String projectPath = FileHelper.getFileUrl(projectDir);
        forPostcompile(projectPath, toTarget).execute(subPath, XLang.newEvalScope());
    }

    public void execute(String tplPath, IEvalScope scope) {
        execute(tplPath, null, scope);
    }

    /**
     * 执行代码生成。
     *
     * @param tplPath 单个模板文件或者模板文件目录
     * @param vars    额外传入的上下文中可以使用的变量
     * @param scope   当前执行上下文，其中定义了codeGenLoop对象，它提供了INestedLoop循环处理逻辑。
     */
    public void execute(String tplPath, Map<String, Object> vars, IEvalScope scope) {
        LOG.debug("nop.execute-codegen:tplPath={},vars={},tplRootPath={},targetRootPath={}", tplPath, vars,
                this.getTplRootPath(), this.getTargetRootPath());

        scope = scope.newChildScope();
        if (vars != null) {
            scope.setLocalValues(s_loc, vars);
        }
        scope.setLocalValue(s_loc, CodeGenConstants.VAR_CODE_GENERATOR, this);

        boolean hasInit = runInit(scope);
        INestedLoop loop = (INestedLoop) scope.getLocalValue(CodeGenConstants.VAR_CODE_GEN_LOOP);
        if (loop == null) {
            if (hasInit)
                LOG.warn("nop.codegen.code-gen-loop-not-found");
            loop = new NestedLoop(new NestedLoopModel(Collections.emptySet(), Collections.emptyMap()),
                    Collections.emptyMap());
        }
        scope.setLocalValues(s_loc, loop.getGlobalVars());

        executeWithLoop(tplPath, loop, scope);
    }

    /**
     * 设置codeGenModelPath为指定路径，然后执行代码生成模板
     */
    public void renderModel(String modelPath, String tplRootPath, String tplPath, IEvalScope scope) {
        String path = StringHelper.appendPath(getTplRootPath(), "/");
        String fullPath = StringHelper.absolutePath(path, modelPath);

        Map<String, Object> vars = new HashMap<>();
        vars.put(CodeGenConstants.VAR_CODE_GEN_MODEL_PATH, fullPath);
        ComponentModelLoader loader = ResourceComponentManager.instance().getComponentModelLoader(fullPath);
        if (loader != null) {
            IComponentModel model = ResourceComponentManager.instance().loadComponentModel(fullPath);
            scope.setLocalValue(null, CodeGenConstants.VAR_CODE_GEN_MODEL, model);
        } else {
            if (fullPath.endsWith(".xlsx"))
                throw new NopException(ERR_CODEGEN_UNKNOWN_MODEL_TYPE).param(ARG_FILE_NAME, StringHelper.fileFullName(fullPath));

            LOG.warn("nop.cli.undefined-code-gen-model-type:{}", StringHelper.fileFullName(fullPath));
        }

        XCodeGenerator gen = withTplDir(tplRootPath);
        gen.execute(tplPath, vars, scope);
    }

    public XCodeGenerator withTplDir(String tplRootPath) {
        XCodeGenerator gen = new XCodeGenerator(this.getLoader(), tplRootPath, getTargetRootPath());
        gen.withContentCache(this.contentCache);
        gen.withDependencyManager(dependencyManager);
        gen.forceOverride(this.isForceOverride());
        return gen;
    }

    public XCodeGenerator withTargetDir(String targetRootPath) {
        targetRootPath = StringHelper.absolutePath(StringHelper.appendPath(this.getTargetRootPath(), "/"),
                targetRootPath);
        XCodeGenerator gen = new XCodeGenerator(this.getLoader(), getTplRootPath(), targetRootPath);
        gen.withContentCache(this.contentCache);
        gen.withDependencyManager(dependencyManager);
        gen.forceOverride(this.isForceOverride());
        return gen;
    }

    private boolean runInit(IEvalScope scope) {
        IResource resource = getTplResource(CodeGenConstants.INIT_FILE_NAME);
        if (resource.exists()) {
            XplModel xpl = loadTpl(resource);
            if (xpl != null) {
                xpl.invoke(scope);
            }
            return true;
        }
        return false;
    }

    /**
     * 以tplRootPath为当前路径来计算绝对路径
     *
     * @param path 相对路径
     * @return path所对应的绝对路径
     */
    public String getTplPath(String path) {
        if (StringHelper.isEmpty(path))
            return null;

        if (path.startsWith("/"))
            return path;
        String basePath = StringHelper.appendPath(getTplRootPath(), "/");
        String fullPath = StringHelper.absolutePath(basePath, path);
        return fullPath;
    }

    public String getTargetPath(String path) {
        if (StringHelper.isEmpty(path))
            return null;

        if (path.startsWith("/"))
            return path;
        String basePath = StringHelper.appendPath(getTargetRootPath(), "/");
        String fullPath = StringHelper.absolutePath(basePath, path);
        return fullPath;
    }
}
