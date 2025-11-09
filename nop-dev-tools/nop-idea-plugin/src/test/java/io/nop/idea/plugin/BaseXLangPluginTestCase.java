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
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
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
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.services.NopAppListener;
import io.nop.xlang.debugger.XLangDebugger;
import io.nop.xlang.debugger.initialize.XLangDebuggerInitializer;

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
            initXLangDebugger();

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

    private void initXLangDebugger() {
        // Note: 在单元测试中，vfs 资源是针对 Project 被复制到单独的 src 目录下的，
        // 通过 ProjectVirtualFileSystem 得到的 vfs 资源路径与调试断点的文件路径是不一致的，
        // 因此，需要针对测试资源做路径转换，以匹配断点所在的文件路径
        XLangDebuggerInitializer debugger = new XLangDebuggerInitializer() {
            @Override
            protected XLangDebugger createDebugger() {
                return new XLangDebugger() {
                    @Override
                    protected String toSourcePath(SourceLocation loc) {
                        String prefix = "/src/_vfs/";
                        String path = super.toSourcePath(loc);

                        if (path.startsWith(prefix)) {
                            String vfsFileName = path.substring(prefix.length());
                            File rootDir = new File(getVfsDir(), "../../../..");
                            File vfsSrcFile = new File(new File(rootDir, "src/test/resources/_vfs"), vfsFileName);

                            if (vfsSrcFile.isFile()) {
                                path = vfsSrcFile.toURI().toString();
                                path = StringHelper.normalizePath(path);
                            }
                        }
                        return path;
                    }
                };
            }
        };

        if (debugger.isEnabled()) {
            debugger.initialize();
            cleanup.appendOnCancelTask(debugger::destroy);
        }
    }

    protected PsiFile configureByXLangText(String text) {
        String fileName = "unit-" + StringHelper.randomDigits(8) + '.' + XLANG_EXT;
        return myFixture.configureByText(fileName, text);
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

    protected File getVfsDir() {
        return new File(getClass().getResource("/_vfs").getFile());
    }

    /** 将 vfs 测试资源全部复制到 Project 中 */
    protected void addAllTestVfsResourcesToProject() {
        File vfsDir = getVfsDir();

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

    protected String insertCaretIntoVfs(String resource, String insertAt, String replacement) {
        return readVfsResource(resource).replace(insertAt, replacement);
    }

    /**
     * 获取 &lt;caret> 标记下的原始 {@link PsiElement} 元素
     * <p/>
     * 与之不同的是，{@link CodeInsightTestFixture#getElementAtCaret() myFixture.getElementAtCaret()}
     * 得到的是原始元素的引用元素
     */
    protected PsiElement getOriginalElementAtCaret() {
        assertCaretExists();

        return myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    }

    protected PsiElement getResolvedElementAtCaret() {
        return myFixture.getElementAtCaret();
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

    protected String getDocAtCaret() {
        // Note: 通过 ApplicationManager.getApplication().runReadAction(() -> {})
        // 消除异常 "Read access is allowed from inside read-action"
        PsiElement originalElement = getOriginalElementAtCaret();

        PsiElement resolvedElement;
        // Note: 当 <caret> 位置的元素没有引用元素时，会直接抛出异常，这里需要屏蔽该异常
        try {
            resolvedElement = getResolvedElementAtCaret();
        } catch (Throwable ignore) {
            resolvedElement = originalElement;
        }

        Language lang = originalElement.getContainingFile().getLanguage();
        DocumentationProvider docProvider = LanguageDocumentation.INSTANCE.forLanguage(lang);

        return docProvider.generateDoc(resolvedElement, originalElement);
    }

    protected void assertCaretExists() {
        assertTrue("No '<caret>' found in current text", myFixture.getCaretOffset() > 0);
    }

    /** 检查自动补全所选中的第一个补全项是否与预期相符 */
    protected void doAssertCompletion(String expectedText) {
        doAssertCompletion(null, expectedText);
    }

    /**
     * 检查选中指定的补全项之后的文本是否与预期相符
     * <p/>
     * 注意：<ul>
     * <li>在仅有唯一的补全元素时，将自动完成补全，且不能再获取到补全列表；</li>
     * <li>只有在调用 `myFixture.completeBasic()` 后，才能完成补全，获得补全列表；</li>
     * </ul>
     */
    protected void doAssertCompletion(String selectedItem, String expectedText) {
        // 获取当前查找元素
        LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        assertNotNull("Lookup not active", lookup);

        List<LookupElement> items = lookup.getItems();
        assertFalse("No completion items", items.isEmpty());

        // 选择第一个补全项
        LookupElement item = null;
        if (selectedItem == null) {
            item = items.get(0);
        } else {
            for (LookupElement i : items) {
                if (i.getLookupString().equals(selectedItem)) {
                    item = i;
                    break;
                }
            }
            assertNotNull("No completion item matched with '" + selectedItem + "'", item);
        }
        lookup.setCurrentItem(item);

        // 模拟选中补全项
        lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);

        // 验证结果
        myFixture.checkResult(expectedText);
    }

    /**
     * 检查 {@link PsiElement} 的解析树是否与指定的 vfs 文件 <code>expectedAstFile</code> 的内容相同
     */
    protected void doAssertASTTree(PsiElement tree, String expectedAstFile) {
        String testTree = DebugUtil.psiToString(tree, true, false);

        String expectedTree = readVfsResource(expectedAstFile);

        assertEquals(expectedTree, testTree);
    }
}
