package io.nop.ai.code_analyzer.maven;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Java源码文件查找器
 * 支持在Maven模块结构中查找Java源码文件，遵循Maven标准目录布局
 */
public class JavaSourceFileFinder {

    // 常量定义
    private static final String[] MAVEN_SOURCE_DIRS = {
            "src/main/java",
            "src/test/java",
            "src/main/groovy",
            "src/test/groovy",
            "src/main/kotlin",
            "src/test/kotlin"
    };

    private static final String[] JAVA_SOURCE_DIRS = {
            "src/main/java",
            "src/test/java"
    };

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "target", "build", ".git", ".svn", ".idea", ".vscode", "node_modules"
    );

    private static final String JAVA_FILE_EXTENSION = ".java";

    private final MavenModuleStructure moduleStructure;
    private final Map<String, File> classNameCache = new ConcurrentHashMap<>();

    public JavaSourceFileFinder(MavenModuleStructure moduleStructure) {
        this.moduleStructure = moduleStructure;
    }

    /**
     * 根据类的全限定名查找对应的Java源码文件（支持内部类）
     *
     * 查找策略：从最具体的路径开始，逐级向上查找
     *
     * 例如对于 "com.example.A.B.C"：
     * 1. 先查找 "com/example/A/B/C.java"
     * 2. 再查找 "com/example/A/B.java"
     * 3. 再查找 "com/example/A.java"
     * 4. 最后查找 "com.java"
     */
    public File findJavaSourceFile(String className) {
        if (isBlank(className)) {
            return null;
        }

        // 先检查缓存
        File cachedFile = classNameCache.get(className);
        if (cachedFile != null) {
            return cachedFile.exists() ? cachedFile : null;
        }

        // 逐级查找策略
        File foundFile = findWithHierarchicalSearch(className);

        // 缓存结果
        if (foundFile != null) {
            classNameCache.put(className, foundFile);
        }

        return foundFile;
    }

    /**
     * 分层查找：从最具体的路径开始，逐级向上查找
     */
    private File findWithHierarchicalSearch(String className) {
        String currentClassName = className;

        while (currentClassName != null && !currentClassName.isEmpty()) {
            // 尝试查找当前类名对应的文件
            File file = searchInAllModules(currentClassName);
            if (file != null) {
                return file;
            }

            // 移除最后一个点号后面的部分，向上查找
            currentClassName = removeLastSegment(currentClassName);
        }

        return null;
    }

    /**
     * 移除类名的最后一个段落
     *
     * @param className 完整类名，如 "com.example.A.B.C"
     * @return 移除最后一段后的类名，如 "com.example.A.B"，如果没有点号则返回null
     */
    private String removeLastSegment(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return null; // 没有更多层级了
        }
        return className.substring(0, lastDotIndex);
    }

    /**
     * 在所有模块中搜索指定类名的文件
     */
    private File searchInAllModules(String className) {
        String relativeFilePath = className.replace('.', File.separatorChar) + JAVA_FILE_EXTENSION;

        return moduleStructure.getModules().values().stream()
                .map(this::resolveModuleDirectory)
                .map(moduleDir -> findInMavenSourceDirectories(moduleDir, relativeFilePath))
                .filter(Objects::nonNull)
                .filter(File::exists)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据类的简单名称查找对应的Java源码文件
     */
    public List<File> findJavaSourceFilesBySimpleName(String simpleClassName) {
        if (isBlank(simpleClassName)) {
            return Collections.emptyList();
        }

        List<File> results = new ArrayList<>();

        // 对于简单名称，也使用层级查找
        String currentClassName = simpleClassName;
        Set<String> searchedNames = new HashSet<>(); // 避免重复搜索

        while (currentClassName != null && !currentClassName.isEmpty()) {
            if (!searchedNames.contains(currentClassName)) {
                findBySimpleNameInAllModules(currentClassName, results);
                searchedNames.add(currentClassName);
            }
            currentClassName = removeLastSegment(currentClassName);
        }

        // 去重
        return results.stream().distinct().collect(ArrayList::new, (list, file) -> list.add(file), ArrayList::addAll);
    }

    /**
     * 根据简单类名在所有模块中查找
     */
    private void findBySimpleNameInAllModules(String simpleClassName, List<File> results) {
        String fileName = simpleClassName + JAVA_FILE_EXTENSION;

        moduleStructure.getModules().values().forEach(module -> {
            File moduleDir = resolveModuleDirectory(module);
            findFilesByNameInMavenDirs(moduleDir, fileName, results);
        });
    }

    /**
     * 获取所有Java源码文件
     */
    public List<File> getAllJavaSourceFiles() {
        List<File> results = new ArrayList<>();

        moduleStructure.getModules().values().parallelStream()
                .map(this::resolveModuleDirectory)
                .forEach(moduleDir -> findJavaFilesInModule(moduleDir, results));

        return results;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        classNameCache.clear();
    }

    // 辅助方法
    private File findInMavenSourceDirectories(File moduleDir, String relativeFilePath) {
        return Stream.of(MAVEN_SOURCE_DIRS)
                .map(sourceDir -> new File(moduleDir, sourceDir + File.separator + relativeFilePath))
                .filter(File::exists)
                .filter(File::isFile)
                .findFirst()
                .orElse(null);
    }

    private void findFilesByNameInMavenDirs(File moduleDir, String fileName, List<File> results) {
        Stream.of(MAVEN_SOURCE_DIRS)
                .map(sourceDir -> new File(moduleDir, sourceDir))
                .filter(File::exists)
                .filter(File::isDirectory)
                .forEach(srcDir -> findFilesByName(srcDir, fileName, results));
    }

    private  void findJavaFilesInModule(File moduleDir, List<File> results) {
        Stream.of(JAVA_SOURCE_DIRS)
                .map(sourceDir -> new File(moduleDir, sourceDir))
                .filter(File::exists)
                .filter(File::isDirectory)
                .forEach(srcDir -> findJavaFiles(srcDir, results));
    }

    private File resolveModuleDirectory(MavenModule module) {
        File moduleDir = new File(module.getModulePath());
        return moduleDir.isAbsolute() ? moduleDir : new File(System.getProperty("user.dir"), module.getModulePath());
    }

    private void findFilesByName(File dir, String fileName, List<File> results) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().equals(fileName)) {
                results.add(file);
            } else if (file.isDirectory() && !isExcludedDirectory(file.getName())) {
                findFilesByName(file, fileName, results);
            }
        }
    }

    private  void findJavaFiles(File dir, List<File> results) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(JAVA_FILE_EXTENSION)) {
                results.add(file);
            } else if (file.isDirectory()) {
                findJavaFiles(file, results);
            }
        }
    }

    private boolean isExcludedDirectory(String dirName) {
        return dirName.startsWith(".") || EXCLUDED_DIRS.contains(dirName);
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}