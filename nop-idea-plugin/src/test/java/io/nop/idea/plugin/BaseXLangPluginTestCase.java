package io.nop.idea.plugin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.core.dict.DictProvider;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.initialize.impl.ReflectionHelperMethodInitializer;
import io.nop.core.initialize.impl.VirtualFileSystemInitializer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.xlang.initialize.XLangCoreInitializer;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-17
 */
public abstract class BaseXLangPluginTestCase extends LightJavaCodeInsightFixtureTestCase {
    private final Cancellable cleanup = new Cancellable();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Note: 消除异常 "Write access is allowed inside write-action only"
        ApplicationManager.getApplication().runWriteAction(() -> {
            // 临时注册 XLang 文件类型
            for (String ext : getXLangFileExtensions()) {
                FileTypeManager.getInstance().associateExtension(XLangFileType.INSTANCE, ext);
            }
        });

        // 初始化 XLang 环境：由于测试资源均在 classpath 中，故而，需采用默认的 ICoreInitializer 进行初始化，
        // 而不能通过 NopAppListener 初始化
        ProjectEnv.withProject(getProject(), () -> {
            AppConfig.getConfigProvider().updateConfigValue(ApiConfigs.CFG_DEBUG, false);

            ICoreInitializer[] initializers = new ICoreInitializer[] {
                    new XLangCoreInitializer(),
                    new VirtualFileSystemInitializer(),
                    new ReflectionHelperMethodInitializer(),
                    };
            for (ICoreInitializer initializer : initializers) {
                initializer.initialize();
                cleanup.appendOnCancelTask(initializer::destroy);
            }

            cleanup.append(DictProvider.registerLoader());

            return null;
        });
    }

    @Override
    protected void tearDown() throws Exception {
        cleanup.cancel();
        super.tearDown();
    }

    protected String[] getXLangFileExtensions() {
        return new String[0];
    }

    /** 将测试环境中的 vfs 资源添加到 Project 中 */
    protected void addVfsResourcesToProject(String... resources) {
        for (String resource : resources) {
            String text = readVfsResource(resource);

            myFixture.addFileToProject("_vfs" + resource, text);
        }
    }

    protected String readVfsResource(String resource) {
        IResource res = VirtualFileSystem.instance().getResource(resource);
        return ResourceHelper.readText(res);
    }
}
