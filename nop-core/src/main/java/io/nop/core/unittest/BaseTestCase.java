/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.unittest;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.tree.TreeVisitors;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE;

/**
 * 所有单元测试类的基类，提供数据驱动测试所需的帮助函数。 数据驱动测试将测试数据保存到外部数据文件中。
 * <p>
 * attachmentXXX方法提供了从测试类所在目录读取文件的能力
 */
public class BaseTestCase {
    static final SourceLocation s_loc = SourceLocation.fromClass(BaseTestCase.class);
    private File attachmentDir;
    private File targetDir;

    private static Map<String, Object> g_testConfigs = new ConcurrentHashMap<>();
    private static boolean g_testRunning;
    private static Map<String, CyclicBarrier> g_barriers = new ConcurrentHashMap<>();

    private static List<Runnable> g_lazyActions = new ArrayList<>();

    private static boolean g_localDb = false;

    /**
     * 强制使用本地内存数据库
     */
    public static boolean isForceLocalDb() {
        return g_localDb;
    }

    public static void setForceLocalDb(boolean localDb) {
        g_localDb = localDb;
    }

    public static boolean isTestRunning() {
        return g_testRunning;
    }

    public static void beginTest() {
        resetAll();
        g_testRunning = true;
    }

    public static void endTest() {
        resetAll();
    }

    public static Map<String, Object> getTestConfigs() {
        return g_testConfigs;
    }

    public static void resetAll() {
        g_testConfigs.clear();
        g_barriers.clear();
        g_lazyActions.clear();
        g_localDb = false;
        g_testRunning = false;
    }

    public static void setTestConfig(String name, Object value) {
        IConfigReference<Object> var = AppConfig.varRef(s_loc, name, (Class) value.getClass(), null);
        setTestConfig(var, value);
    }

    public static <T> void setTestConfig(IConfigReference<T> var, T value) {
        g_testConfigs.put(var.getName(), value);
        AppConfig.getConfigProvider().updateConfigValue(var, value);
    }

    public static void addLazyAction(Runnable action) {
        g_lazyActions.add(action);
    }

    public static void runLazyActions() {
        List<Runnable> actions = new ArrayList<>(g_lazyActions);
        g_lazyActions.clear();
        for (Runnable action : actions) {
            action.run();
        }
    }

    public static void clearLazyActions() {
        g_lazyActions.clear();
    }

    public static void addBarrier(String name, CyclicBarrier barrier) {
        g_barriers.put(name, barrier);
    }

    public static boolean waitAllBarrier(long timeout) {
        long endTime = CoreMetrics.currentTimeMillis() + timeout;
        while (!g_barriers.isEmpty()) {
            Iterator<CyclicBarrier> it = g_barriers.values().iterator();
            CyclicBarrier barrier = it.next();
            it.remove();

            try {
                barrier.await(timeout, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                return false;
            }
            timeout = endTime - CoreMetrics.currentTimeMillis();
            if (timeout <= 0)
                break;
        }
        return false;
    }

    public BaseTestCase() {
        this.attachmentDir = FileHelper.getClassFile(getClass()).getParentFile();
    }

    public static void forceStackTrace() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    public void setAttachmentDir(File attachmentDir) {
        this.attachmentDir = attachmentDir;
    }

    public void setTargetDir(File targetDir) {
        this.targetDir = targetDir;
    }

    public File attachmentFile(String name) {
        File file = new File(attachmentDir(), name);
        return file;
    }

    public File attachmentDir() {
        return attachmentDir;
    }

    public List<IFile> attachmentResources(String name, boolean cascade) {
        IFile resource = attachmentResource(name);
        List<IFile> files = new ArrayList<>();
        if (cascade) {
            for (IFile file : TreeVisitors.depthFirstIterator(resource, false)) {
                if (file.isDirectory())
                    continue;
                files.add(file);
            }
        } else {
            List<IFile> children = resource.getChildren();
            if (children != null) {
                for (IFile file : children) {
                    if (file.isDirectory())
                        continue;
                    files.add(file);
                }
            }
        }
        return files;
    }

    public MarkdownTestFile markdownTestFile(IResource resource) {
        return new MarkdownTestFileParser().parseFromResource(resource);
    }

    public void runMarkdownTest(IResource resource, Function<MarkdownTestSection, Object> action) {
        markdownTestFile(resource).run(action);
    }

    public void runMarkdownTest(IResource resource, String blockTitle, Function<MarkdownTestSection, Object> action) {
        MarkdownTestFile file = markdownTestFile(resource);
        MarkdownTestFile.runSection(file.requireSection(blockTitle), action);
    }

    /**
     * 获取测试类所在目录下的文件
     *
     * @param name 文件名称
     */
    public IFile attachmentResource(String name) {
        File file = attachmentFile(name);
        return new FileResource(FileHelper.getFileUrl(file), file);
    }

    public <T> T attachmentBean(String name, Class<T> clazz) {
        return (T) ResourceHelper.readJson(attachmentResource(name), clazz);
    }

    public IResource classpathResource(String path) {
        return new ClassPathResource("classpath:" + path);
    }

    public <T> T classpathBean(String path, Class<T> clazz) {
        return (T) ResourceHelper.readJson(classpathResource(path), clazz);
    }

    public String attachmentText(String name) {
        return ResourceHelper.readText(attachmentResource(name), null);
    }

    public String attachmentJsonText(String name) {
        return JsonTool.stringify(ResourceHelper.readJson(attachmentResource(name), null), null, "  ");
    }

    public String attachmentXmlText(String name) {
        return ResourceHelper.readXml(attachmentResource(name), null).xml();
    }

    public ByteString attachmentBytes(String name) {
        byte[] bytes = ResourceHelper.readBytes(attachmentResource(name));
        return ByteString.of(bytes);
    }

    public XNode attachmentXml(String name) {
        return ResourceHelper.readXml(attachmentResource(name), null, false, true);
    }

    /**
     * 删除前缀为指定值的所有文件
     */
    public void clearAttachment(String prefix) {
        File dir = attachmentDir();
        if (dir != null) {
            FileHelper.removeChildWithPrefix(dir, prefix);
        }
    }

    public void saveAttachmentBean(String name, Object bean) {
        IResource resource = attachmentResource(name);
        ResourceHelper.writeJson(resource, bean);
    }

    public void saveAttachmentText(String name, String text) {
        IResource resource = attachmentResource(name);
        ResourceHelper.writeText(resource, text, null);
    }

    public void saveAttachmentBytes(String name, byte[] bytes) {
        IResource resource = attachmentResource(name);
        ResourceHelper.writeBytes(resource, bytes);
    }

    /**
     * maven模块的target目录。这里嘉定采用maven缺省目录布局。
     */
    public File getTargetDir() {
        if (targetDir != null)
            return targetDir;

        File dir = attachmentDir();
        File ret = dir;
        int n = StringHelper.countChar(getClass().getName(), '.');
        for (int i = 0; i < n; i++) {
            ret = ret.getParentFile();
        }
        ret = ret.getParentFile();
        if (!ret.getName().equals("target"))
            throw new IllegalStateException("not standard maven resource dir structure:" + dir);
        this.targetDir = ret;
        return ret;
    }

    public String normalizeJson(String json) {
        return JsonTool.stringify(JsonTool.instance().parseFromText(null, json, null), null, "  ");
    }

    public String normalizeCRLF(String str) {
        return StringHelper.replace(str, "\r\n", "\n");
    }

    public File getTargetFile(String subPath) {
        File file = getTargetDir();
        return new File(file, subPath);
    }

    public IFile getTargetResource(String subPath) {
        return new FileResource(getTargetFile(subPath));
    }

    public File getSrcDir() {
        return new File(getTargetDir(), "../src");
    }

    public File getModuleDir() {
        return new File(getTargetDir(), "..");
    }

    public File getCasesDir() {
        File moduleDir = getModuleDir();
        File casesDir = new File(moduleDir, "cases");
        return casesDir;
    }

    public File getTestResourcesDir() {
        return new File(getSrcDir(), "test/resources");
    }

    public IResource testResource(String name) {
        // String path = StringHelper.filePath(getClass().getName().replace('.', '/'));
        File file = new File(getTestResourcesDir(), name);
        return new FileResource(file);
    }

    public void saveBeanToTestResourceIfNotExists(String name, Object bean) {
        IResource resource = testResource(name);
        if (!resource.exists()) {
            ResourceHelper.writeJson(resource, bean);
        }
    }

    public void saveTextToTestResourceIfNotExists(String name, String text) {
        IResource resource = testResource(name);
        if (!resource.exists()) {
            ResourceHelper.writeText(resource, text, null);
            saveAttachmentText(name, text);
        }
    }
}