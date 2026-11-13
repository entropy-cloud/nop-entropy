/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.services;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.dict.DictModel;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.resource.ProjectDictProvider;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.debugger.SharedCoreInitializerRegistrar;
import io.nop.xlang.initialize.DslXmlResourceLoader;
import io.nop.xlang.initialize.XLangCoreInitializer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public final class NopProjectService implements Disposable {
    static final Logger LOG = LoggerFactory.getLogger(NopProjectService.class);

    private final ResourceComponentManager componentManager = new ResourceComponentManager(false);

    private final Cancellable cleanup = new Cancellable();

    private final ResourceLoadingCache<DictModel> dictCache = new ResourceLoadingCache<>("project-dict-cache",
            ProjectDictProvider::loadDictModel,
            null);

    /**
     * init 三态失败标记（CRA 8.40 CL-204）：INITIALIZING 承担原 {@code inited} 先置的重入截断职能
     * （registerXDef/registerXlib 经 ProjectResourceComponentManager 委托链同线程重入本方法——
     * 单纯置位后移会导致无界递归）；READY 幂等；FAILED 使 {@link #get()} fail-fast 携带原因
     * （不再静默返回半初始化服务），恢复路径 = 重开项目（新服务实例）。「失败回滚重试」被否决：
     * 全局部分与其他项目为同一实例——撤销即跨项目销毁（缺陷复现）。
     */
    private enum InitState { NEW, INITIALIZING, READY, FAILED }

    private InitState initState = InitState.NEW;
    private volatile Throwable initFailure;

    public NopProjectService() {
    }

    public DictBean getDict(String dictName) {
        DictModel dictModel = dictCache.get(dictName);
        return dictModel == null ? null : dictModel.getDictBean();
    }

    private synchronized void init(Project project) {
        if (initState == InitState.INITIALIZING || initState == InitState.READY) {
            return;
        }
        if (initState == InitState.FAILED) {
            throw new IllegalStateException(
                    "NopProjectService initialization failed previously, reopen the project to retry",
                    initFailure);
        }
        initState = InitState.INITIALIZING;
        try {
            ProjectEnv.withProject(project, () -> {
                doInit();
                return null;
            });
            initState = InitState.READY;
        } catch (Throwable t) {
            // 捕 Throwable（含插件宿主可能的 LinkageError/NoClassDefFoundError）——
            // 否则状态永久停在 INITIALIZING，get() 静默返回半初始化服务（CL-204 症状复现）
            initState = InitState.FAILED;
            initFailure = t;
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            throw new IllegalStateException("NopProjectService initialization failed", t);
        }
    }

    /**
     * XLang 全局设施持有改为 registrar 引用计数（CRA 8.40 W-05）：每项目照常各自 initialize
     * （全局部分同实例幂等覆盖、per-project registerXDef 经委托链每项目执行），持有凭据的释放
     * 登记进 {@link #cleanup}——仅最后一个项目 dispose 时计数归零才撤销全局设施，先关闭的项目
     * 不再拆走其余项目的 XLang 全局设施。
     */
    private void doInit() {
        SharedCoreInitializerRegistrar registrar = SharedCoreInitializerRegistrar.instance();
        XLangCoreInitializer xlang = new XLangCoreInitializer();
        ICancellable hold;
        try {
            hold = registrar.acquire(xlang);
        } catch (Exception e) {
            // 初始化失败：无其他持有者时销毁本次初始化器，防孤儿全局注册残留
            try {
                registrar.destroyIfOrphaned(xlang);
            } catch (Exception ex) {
                LOG.warn("nop.idea.destroy-orphan-initializer-fail", ex);
            }
            throw NopException.adapt(e);
        }
        Consumer<String> releaseTask = hold::cancel;
        cleanup.appendOnCancelTask(releaseTask);
        try {
            registerXlib();
        } catch (Exception e) {
            // 后续步骤失败（裁定③-B.5）：立即释放本次已取得的持有（计数感知）并撤销
            // dispose 侧登记，不再等到项目关闭
            hold.cancel("init-failed");
            cleanup.removeOnCancel(releaseTask);
            throw NopException.adapt(e);
        }
    }

    public static NopProjectService get() {
        Project project = ProjectEnv.currentProject();
        if (project == null) {
            throw new IllegalStateException("Not in project environment, please call with ProjectEnv#withProject");
        }

        NopProjectService service = project.getService(NopProjectService.class);
        service.init(project);
        return service;
    }

    public ResourceComponentManager getComponentManager() {
        return componentManager;
    }

    @Override
    public void dispose() {
        cleanup.cancel();
    }

    private void registerXlib() {
        ComponentModelConfig config = new ComponentModelConfig();
        config.modelType(XLangConstants.MODEL_TYPE_XLIB);
        ComponentModelConfig.LoaderConfig loaderConfig = new ComponentModelConfig.LoaderConfig("xdsl-loader",
                null, XLangConstants.XDSL_SCHEMA_XLIB, null, new DslXmlResourceLoader(XLangConstants.XDSL_SCHEMA_XLIB, null));
        config.loader(XLangConstants.FILE_TYPE_XLIB, loaderConfig);

        cleanup.append(ResourceComponentManager.instance().registerComponentModelConfig(config));
    }
}
