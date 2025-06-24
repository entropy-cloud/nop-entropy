package io.nop.idea.plugin;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
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
import io.nop.idea.plugin.reference.XLangReference;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.xlang.initialize.XLangCoreInitializer;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-17
 */
public abstract class BaseXLangPluginTestCase extends LightJavaCodeInsightFixtureTestCase {
    private static final String XLANG_EXT = "xtest";

    private final Cancellable cleanup = new Cancellable();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Note: 消除异常 "Write access is allowed inside write-action only"
        ApplicationManager.getApplication().runWriteAction(() -> {
            // Note: *.xdef 等需显式注册，否则，这类文件会被视为二进制文件，
            // 在通过 PsiDocumentManager 获取 Document 时，将返回 null
            FileTypeManager.getInstance().associateExtension(XLangFileType.INSTANCE, "xdef");

            FileTypeManager.getInstance().associateExtension(XLangFileType.INSTANCE, XLANG_EXT);
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

    protected void configureByXLangText(String text) {
        myFixture.configureByText("unit." + XLANG_EXT, text);
    }

    /**
     * 将测试环境中的 vfs 资源添加到 Project 中
     * <p/>
     * 在需要做文件跳转时，需要将目标文件提前加入 Project 以确保目标文件已存在
     */
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

    protected PsiElement getElementAtCaret() {
        return myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    }

    /** 找到光标位置的 {@link XLangReference} 或者其他类型的唯一引用 */
    protected PsiReference findReferenceAtCaret() {
        // 实际有多个引用时，将构造返回 PsiMultiReference，
        // 其会按 PsiMultiReference#COMPARATOR 对引用排序得到优先引用，
        // 再调用该优先引用的 #resolve() 得到 PsiElement
        PsiReference ref = TargetElementUtil.findReference(myFixture.getEditor(), myFixture.getCaretOffset());

        if (ref instanceof PsiMultiReference mref) {
            for (PsiReference r : mref.getReferences()) {
                if (r instanceof XLangReference) {
                    return r;
                }
            }
            return ((PsiMultiReference) ref).getReferences()[0];
        }
        return ref;
    }

    protected String getDoc() {
        // Note: 通过 ApplicationManager.getApplication().runReadAction(() -> {})
        // 消除异常 "Read access is allowed from inside read-action"
        PsiElement originalElement = getElementAtCaret();

        PsiElement element = DocumentationManager.getInstance(getProject())
                                                 .findTargetElement(myFixture.getEditor(), myFixture.getFile());

        DocumentationProvider docProvider = DocumentationManager.getProviderFromElement(originalElement);

        return docProvider.generateDoc(element, originalElement);
    }

    protected PsiElement[] getGotoTargets() {
        return GotoDeclarationAction.findAllTargetElements(getProject(),
                                                           myFixture.getEditor(),
                                                           myFixture.getCaretOffset());
    }
}
