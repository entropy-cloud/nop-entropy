/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.IAsyncFunctionService;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.TextFile;
import io.nop.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletionStage;

import static io.nop.web.WebErrors.ARG_PATH;
import static io.nop.web.WebErrors.ERR_WEB_MISSING_RESOURCE;

public class SystemJsProvider extends ResourceWithHistoryProvider {
    static final Logger LOG = LoggerFactory.getLogger(SystemJsProvider.class);

    private final Cancellable cleanup = new Cancellable();
    private IAsyncFunctionService jsFunctionService;

    public void setJsFunctionService(IAsyncFunctionService jsFunctionService) {
        this.jsFunctionService = jsFunctionService;
    }

    @PostConstruct
    public void init() {
        registerJs();
    }

    @PreDestroy
    public void destroy() {
        cleanup.cancel();
    }

    void registerJs() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(WebConstants.MODEL_TYPE_JS);

        config.loader(WebConstants.MODEL_TYPE_JS, this::loadJs);

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }

    public String getJs(String path) {
        TextFile file = (TextFile) ResourceComponentManager.instance().loadComponentModel(path);
        return file.getText();
    }

    private TextFile loadJs(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        SourceLocation loc = SourceLocation.fromPath(resource.getPath());

        // 如果后缀名为js的文件已经存在，则直接返回此文件的内容
        if (resource.exists()) {
            ResourceComponentManager.instance().traceDepends(resource.getPath());
            String text = ResourceHelper.readText(resource).trim();
            // 如果不是SystemJs格式，则假设它是ESM格式，需要转换格式
            if (!text.startsWith("System.register(")) {
                if (jsFunctionService != null) {
                    CompletionStage<?> future = jsFunctionService.invokeAsync(WebConstants.FUNC_ROLLUP_TRANSFORM, path, text);
                    String result = (String) FutureHelper.syncGet(future);
                    dumpResource(resource, result);
                    text = result;
                } else {
                    LOG.warn("nop.web.no-js-function-service:resource={}", resource.getPath());
                }
            }
            return new TextFile(loc, text);
        }

        // 查找后缀名为xjs的对应文件，试图从该文件动态生成对应的js文件
        IResource xjsResource = ResourceHelper.getSiblingWithExt(resource, WebConstants.FILE_EXT_XJS);
        if (!xjsResource.exists()) {
            throw new NopException(ERR_WEB_MISSING_RESOURCE)
                    .param(ARG_PATH, resource.getPath());
        }

        ResourceComponentManager.instance().traceDepends(xjsResource.getPath());

        String source = ResourceHelper.readText(xjsResource);
        if (StringHelper.isBlank(source)) {
            return emptyFile(loc);
        }

        source = new XGenJsProcessor().process(loc, source);
        // 将动态生成的结果保存到dump目录下
        dumpResource(xjsResource, source);

        if (StringHelper.isEmpty(source)) {
            return emptyFile(loc);
        }

        // 调用js服务将ESM模块文件格式转换为SystemJs格式
        if (jsFunctionService != null) {
            CompletionStage<?> future = jsFunctionService.invokeAsync(WebConstants.FUNC_ROLLUP_TRANSFORM, path, source);
            String result = (String) FutureHelper.syncGet(future);
            dumpResource(resource, result);
            source = result;
        }
        return new TextFile(loc, source);
    }

    public static TextFile emptyFile(SourceLocation loc) {
        // 空文件
        String source = "System.register([], (_export) => ({\n" +
                "  execute() {}\n" +
                "}));";
        return new TextFile(loc, source);
    }

    public static void dumpResource(IResource resource, String source) {
        if (AppConfig.isDebugMode()) {
            String dumpPath = ResourceHelper.getDumpPath(resource.getPath());
            IResource dumpFile = VirtualFileSystem.instance().getResource(dumpPath);
            ResourceHelper.writeText(dumpFile, source);
        }
    }

    public String getJsSource(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return ResourceHelper.readText(resource);
    }

    public void saveJsSource(String path, String source) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        withHistorySupport(resource, r -> {
            ResourceHelper.writeText(resource, source);
            return true;
        });
    }
}