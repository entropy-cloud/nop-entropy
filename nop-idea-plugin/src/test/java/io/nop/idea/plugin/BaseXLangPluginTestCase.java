/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.services.NopAppListener;

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
            // 将真实的 JDK 注入到项目中，以确保能够通过 JavaPsiFacade 查找得到 JDK class
            String jdkHome = System.getProperty("java.home");
            Sdk jdk = JavaSdkImpl.getInstance().createJdk("System JDK", jdkHome, true);

            ProjectJdkTable.getInstance().addJdk(jdk);
            ModuleRootModificationUtil.setModuleSdk(getModule(), jdk);

            cleanup.appendOnCancelTask(() -> {
                ProjectJdkTable.getInstance().removeJdk(jdk);
            });

            // Note: *.xdef 等需显式注册，否则，这类文件会被视为二进制文件，
            // 在通过 PsiDocumentManager 获取 Document 时，将返回 null
            FileTypeManager.getInstance().associateExtension(XLangFileType.INSTANCE, "xdef");

            FileTypeManager.getInstance().associateExtension(XLangFileType.INSTANCE, XLANG_EXT);

            new NopAppListener().appFrameCreated(new ArrayList<>());

            // Note: 提前将被引用的 vfs 资源添加到 Project 中
            addAllNopXDefsToProject();
            addVfsResourcesToProject("/nop/core/xlib/meta-gen.xlib", "/dict/core/std-domain.dict.yaml");
            addAllTestVfsResourcesToProject();
        });
    }

    @Override
    protected void tearDown() throws Exception {
        ApplicationManager.getApplication().runWriteAction(() -> {
            cleanup.cancel();
        });

        super.tearDown();
    }

    protected PsiFile configureByXLangText(String text) {
        return myFixture.configureByText("unit." + XLANG_EXT, text);
    }

    protected void addAllNopXDefsToProject() {
        String jarPath = getClass().getResource("/_vfs/nop/schema/xdef.xdef")
                                   .getPath()
                                   .replaceAll("^file:", "")
                                   .replaceAll("!.+$", "");

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jarPath))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().endsWith(".xdef")) {
                    continue;
                }

                String path = entry.getName().replaceAll("^_vfs/", "/");
                String text = IoHelper.readText(zip, Charset.defaultCharset().name());

                addVfsResourceToProject('/' + path, text);
            }
        } catch (Exception ignore) {
        }
    }

    /** 将 vfs 测试资源全部复制到 Project 中 */
    protected void addAllTestVfsResourcesToProject() {
        File vfsDir = new File(getClass().getResource("/_vfs").getFile());

        FileHelper.walk(vfsDir, (file) -> {
            if (file.isFile()) {
                String path = FileHelper.getRelativePath(vfsDir, file);
                String text = FileHelper.readText(file, Charset.defaultCharset().name());

                addVfsResourceToProject('/' + path, text);
            }
            return FileVisitResult.CONTINUE;
        });
    }

    /**
     * 将测试环境中的 vfs 资源添加到 Project 中
     * <p/>
     * 在需要做文件引用时，需要将目标文件提前加入 Project 以确保目标文件已存在
     */
    protected void addVfsResourcesToProject(String... resources) {
        for (String resource : resources) {
            String text = readVfsResource(resource);

            addVfsResourceToProject(resource, text);
        }
    }

    protected void addVfsResourceToProject(String path, String text) {
        if (path.endsWith(".java")) {
            myFixture.addClass(text);
        } else {
            myFixture.addFileToProject("_vfs" + path, text);
        }
    }

    protected String readVfsResource(String resource) {
        IResource res = new ClassPathResource("classpath:_vfs" + resource);
        return ResourceHelper.readText(res);
    }

    protected PsiElement getElementAtCaret() {
        assertCaretExists();

        return myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    }

    /** 找到光标位置的 {@link XLangReference} 或者其他类型的唯一引用 */
    protected PsiReference findReferenceAtCaret() {
        assertCaretExists();

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
        assertCaretExists();

        return GotoDeclarationAction.findAllTargetElements(getProject(),
                                                           myFixture.getEditor(),
                                                           myFixture.getCaretOffset());
    }

    private void assertCaretExists() {
        assertTrue("No '<caret>' found in current text", myFixture.getCaretOffset() > 0);
    }

    /** 检查自动补全所选中的第一个补全项是否与预期相符 */
    protected void assertCompletion(String expectedText) {
        // 获取当前查找元素
        LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        assertNotNull("Lookup not active", lookup);

        List<LookupElement> items = lookup.getItems();
        assertFalse("No completion items", items.isEmpty());

        // 选择第一个补全项
        LookupElement item = items.get(0);
        lookup.setCurrentItem(item);

        // 模拟选中补全项
        lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);

        // 验证结果
        myFixture.checkResult(expectedText);
    }

    /**
     * 检查 {@link PsiElement} 的解析树是否与指定的 vfs 文件 <code>expectedAstFile</code> 的内容相同
     */
    protected void assertASTTree(PsiElement tree, String expectedAstFile) {
        String testTree = DebugUtil.psiToString(tree, true, false);

        String expectedTree = readVfsResource(expectedAstFile);

        assertEquals(expectedTree, testTree);
    }
}
