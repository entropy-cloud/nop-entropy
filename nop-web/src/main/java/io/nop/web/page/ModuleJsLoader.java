/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.IAsyncFunctionService;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.text.tokenizer.SimpleTextReader;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_FILE_TYPE;
import static io.nop.core.CoreErrors.ARG_MODEL_TYPE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_EXISTS;

public class ModuleJsLoader implements Function<String, String> {
    static final Logger LOG = LoggerFactory.getLogger(ModuleJsLoader.class);
    private final Cancellable cancellable = new Cancellable();

    private IAsyncFunctionService systemJsTransformer;

    public void setSystemJsTransformer(IAsyncFunctionService systemJsTransformer) {
        this.systemJsTransformer = systemJsTransformer;
    }

    @PostConstruct
    public void init() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.FILE_EXT_JS);

        config.loader(WebConstants.FILE_EXT_JS, this::loadModuleJs);
        config.loader(WebConstants.FILE_EXT_MJS, this::loadModuleJs);

        cancellable.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    @PreDestroy
    public void destroy() {
        cancellable.cancel();
    }

    @Override
    public String apply(String path) {
        if (path.endsWith(".lib")) {
            path += "." + WebConstants.FILE_EXT_JS;
        }
        TextFile file = (TextFile) ResourceComponentManager.instance().loadComponentModel(path);
        return file.getText();
    }

    public TextFile loadModuleJs(String path) {
        SourceLocation loc = SourceLocation.fromPath(path);
        String fileExt = StringHelper.fileExt(path);

        // 如果文件名后缀是mjs，则返回ECMAScript Module格式的js库
        if (WebConstants.FILE_EXT_MJS.equals(fileExt)) {
            String mjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_MJS);
            IResource resource = VirtualFileSystem.instance().getResource(mjsPath);
            if (resource.exists()) {
                ResourceComponentManager.instance().traceDepends(resource.getPath());
                return new TextFile(loc, ResourceHelper.readText(resource));
            }

            String xjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_XJS);
            resource = VirtualFileSystem.instance().getResource(xjsPath);
            if (resource.exists()) {
                String source = generateFromXjs(loc, resource);
                if (StringHelper.isEmpty(source)) source = "export {}";
                // 将动态生成的结果保存到dump目录下
                ResourceHelper.dumpResource(resource, source);
                return new TextFile(loc, source);
            }

            throw new NopException(ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE_PATH, path);
        } else if (WebConstants.FILE_EXT_JS.equals(fileExt) || WebConstants.FILE_EXT_XJS.equals(fileExt)) {
            String jsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_JS);
            IResource jsResource = VirtualFileSystem.instance().getResource(jsPath);

            // 如果文件名后缀是js或者XJS，则返回SystemJS格式的js库
            String xjsPath = StringHelper.replaceFileExt(path, WebConstants.FILE_EXT_XJS);
            IResource xjsResource = VirtualFileSystem.instance().getResource(xjsPath);
            boolean supportXjs = xjsResource.exists() && systemJsTransformer != null;

            // 如果存在js文件，且不支持xjs文件，则直接返回js文件内容
            if (jsResource.exists() && !supportXjs) {
                ResourceComponentManager.instance().traceDepends(jsResource.getPath());
                return new TextFile(loc, ResourceHelper.readText(jsResource));
            }


            if (supportXjs) {
                ResourceComponentManager.instance().traceDepends(xjsResource.getPath());

                String source = generateFromXjs(loc, xjsResource);
                source = transformToSystemJs(xjsResource, source);

                // 将动态生成的结果保存到dump目录下
                ResourceHelper.dumpResource(jsResource, source);

                // 如果xjs文件在文件目录中，则直接生成一个对应的js文件到同样的目录下
                File file = xjsResource.toFile();
                if (file != null) {
                    File jsFile = new File(file.getParent(), StringHelper.replaceFileExt(file.getName(), WebConstants.FILE_EXT_JS));
                    FileHelper.writeTextIfNotMatch(jsFile, source, null);
                }

                return new TextFile(loc, source);
            }

            throw new NopException(ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE_PATH, path);
        } else {
            throw new NopException(ERR_COMPONENT_UNKNOWN_FILE_TYPE_FOR_MODEL_TYPE)
                    .param(ARG_MODEL_TYPE, WebConstants.MODEL_TYPE_JS)
                    .param(ARG_FILE_TYPE, fileExt);
        }
    }

    private boolean isSystemJs(String source) {
        return new SimpleTextReader(source).skipBlank().startsWith("System.register(");
    }

    private String transformToSystemJs(IResource resource, String text) {
        if (StringHelper.isEmpty(text))
            return systemJsEmptyFile();

        if (!isSystemJs(text)) {
            if (systemJsTransformer != null) {
                CompletionStage<?> future = systemJsTransformer.invokeAsync(WebConstants.FUNC_ROLLUP_TRANSFORM,
                        resource.getPath(), text);
                String result = (String) FutureHelper.syncGet(future);
                text = result;
            } else {
                LOG.warn("nop.web.no-system-js-transformer:resource={}", resource.getPath());
            }
        }
        return text;
    }

    private String generateFromXjs(SourceLocation loc, IResource resource) {
        ResourceComponentManager.instance().traceDepends(resource.getPath());
        String source = ResourceHelper.readText(resource);
        if (!StringHelper.isBlank(source)) {
            source = new XGenJsProcessor().process(loc, source);
        }
        return source;
    }

    public static String systemJsEmptyFile() {
        // 空文件
        return "System.register([], (_export) => ({\n" + "  execute() {}\n" + "}));";
    }
}
