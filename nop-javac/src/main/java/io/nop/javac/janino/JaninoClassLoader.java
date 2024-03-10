/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac.janino;

import io.nop.commons.util.FileHelper;
import io.nop.javac.IDynamicClassLoader;
import org.codehaus.commons.compiler.util.resource.DirectoryResourceCreator;
import org.codehaus.commons.compiler.util.resource.DirectoryResourceFinder;
import org.codehaus.commons.compiler.util.resource.PathResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.ResourceCreator;
import org.codehaus.commons.compiler.util.resource.ResourceFinder;
import org.codehaus.janino.CachingJavaSourceClassLoader;
import org.codehaus.janino.util.ClassFile;

import java.io.File;

public class JaninoClassLoader extends CachingJavaSourceClassLoader implements IDynamicClassLoader {
    private final ResourceFinder sourceFinder;
    private volatile boolean closed;

    public JaninoClassLoader(ClassLoader parentClassLoader, ResourceFinder sourceFinder, String characterEncoding,
                             ResourceFinder classFileCacheResourceFinder, ResourceCreator classFileCacheResourceCreator) {
        super(parentClassLoader, sourceFinder, characterEncoding, classFileCacheResourceFinder,
                classFileCacheResourceCreator);
        // this.classFileCacheResourceFinder = classFileCacheResourceFinder;
        // this.classFileCacheResourceCreator = classFileCacheResourceCreator;
        this.sourceFinder = sourceFinder;
    }

    public static JaninoClassLoader create(ClassLoader parentClassLoader, File[] sourcePath, File cacheDirectory) {
        ResourceFinder finder = sourcePath == null ? new DirectoryResourceFinder(FileHelper.currentDir())
                : new PathResourceFinder(sourcePath);
        ResourceFinder classFinder = new DirectoryResourceFinder(cacheDirectory);
        ResourceCreator classCreator = new DirectoryResourceCreator(cacheDirectory);
        return new JaninoClassLoader(parentClassLoader, finder, "UTF-8", classFinder, classCreator);
    }

    public static JaninoClassLoader createForProject(ClassLoader parentClassLoader, File projectDir, boolean test) {
        File[] sourcePath;
        File cacheDir;
        if (test) {
            sourcePath = new File[]{new File(projectDir, "target/gen-test-sources")};
            cacheDir = new File(projectDir, "target/gen-test-classes");
        } else {
            // 非测试情况下，生成到maven源目录下，便于将aop代码一起打包
            sourcePath = new File[]{new File(projectDir, "target/generated-sources")};
            // 必须存放到maven识别的目录之外，否则IDE自动编译产生的类和janino动态装载的类可能在两个ClassLoader中，出现冲突的情况。
            cacheDir = new File(projectDir, "target/classes");
        }
        return create(parentClassLoader, sourcePath, cacheDir);
    }

    public static File getSourceFile(File projectDir, String className, boolean test) {
        File file;
        if (test) {
            file = new File(projectDir, "target/gen-test-sources/" + className.replace('.', '/') + ".java");
        } else {
            file = new File(projectDir, "target/generated-sources/" + className.replace('.', '/') + ".java");
        }
        return file;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws Exception {
        closed = true;
    }

    @Override
    public void setSourceFileCharacterEncoding(String optionalCharacterEncoding) {

    }

    public Resource getSourceResource(String className) {
        Resource sourceResource = this.sourceFinder.findResource(ClassFile.getSourceResourceName(className));
        return sourceResource;
    }

    public boolean containsSourceResource(String className) {
        Resource res = getSourceResource(className);
        return res != null && res.lastModified() > 0;
    }
}