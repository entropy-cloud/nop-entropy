package io.nop.tool.counter;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SourceCounter {
    static final Logger LOG = LoggerFactory.getLogger(SourceCounter.class);

    private final Map<String, AtomicLong> counterByFileExt = new ConcurrentHashMap<>();
    private Set<String> fileExtensions = Set.of("java", "xml", "json5", "json", "yaml");
    private boolean includeTests;
    private boolean logDetails;

    public void setLogDetails(boolean b) {
        this.logDetails = b;
    }

    public void setFileExtensions(Set<String> fileExtensions) {
        this.fileExtensions = fileExtensions;
    }

    public void setIncludeTests(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public void count(File file) {
        if (shouldIgnore(file))
            return;

        if (file.isFile()) {
            String fileExt = getFileExt(file.getName());
            if (fileExtensions.contains(fileExt)) {
                AtomicLong value = counterByFileExt.computeIfAbsent(fileExt, k -> new AtomicLong());
                value.addAndGet(getCodeLines(file));
                counterByFileExt.computeIfAbsent("fileCount", k -> new AtomicLong()).incrementAndGet();
            }
        } else {
            File[] subFiles = file.listFiles();
            if (subFiles != null) {
                for (File subFile : subFiles) {
                    count(subFile);
                }
            }
        }
    }

    String getFileExt(String fileName) {
        String fileExt = StringHelper.fileExt(fileName);
        if (fileExt.equals("xdef") || fileExt.equals("xmeta") || fileExt.equals("xpl"))
            fileExt = "xml";
        return fileExt;
    }

    public Map<String, Long> getResults() {
        Map<String, Long> ret = new TreeMap<>();
        counterByFileExt.forEach((name, value) -> {
            ret.put(name, value.get());
        });
        return ret;
    }

    protected boolean shouldIgnore(File file) {
        String name = file.getName();
        if (name.startsWith("."))
            return true;
        if (name.equals("_dump"))
            return true;

        if (name.equals("target") && new File(file.getParent(), "pom.xml").exists()) {
            return true;
        }

        if (name.equals("target") && file.getParentFile().listFiles().length == 1)
            return true;

        if (name.endsWith(".db") || name.endsWith(".svg") || name.endsWith(".png") || name.endsWith(".jpg"))
            return true;

        if (name.endsWith(".xlsx") || name.endsWith(".pdf") || name.equals(".pptx") || name.endsWith(".docx") || name.endsWith(".csv"))
            return true;

        if (name.endsWith(".zip") || name.endsWith(".gz") || name.endsWith(".jar"))
            return true;

        if (name.equals("gradle"))
            return true;

        if (name.equals("cases") && new File(file.getParent(), "pom.xml").exists())
            return true;

        if (name.equals("build") && new File(file.getParent(), "src").exists())
            return true;

        if (name.equals("_vfs"))
            return false;

        String path = FileHelper.getAbsolutePath(file);
        if (name.startsWith("_") || path.indexOf("/_gen/") > 0)
            return true;

        if (path.endsWith("/parse/antlr"))
            return true;

        if (!includeTests) {
            if (path.endsWith("/src/test"))
                return true;
        }

        if (file.isFile()) {
            if (file.length() == 0)
                return true;
            String text = FileHelper.readText(file, null);
            text = StringHelper.limitLen(text, 200);
            if (text.contains("__XGEN_FORCE_OVERRIDE__"))
                return true;
        }
        return false;
    }

    protected int getCodeLines(File file) {
        String text = this.readText(file);
        text = removeComments(text);
        if (StringHelper.isBlank(text))
            return 0;
        int count = StringHelper.stripedSplit(text, '\n').size();
        if (logDetails)
            LOG.info("file-lines:{},file={}", count, file);
        return count;
    }

    protected String readText(File file) {
        try {
            return FileHelper.readText(file, null);
        } catch (Exception e) {
            return FileHelper.readText(file, "GBK");
        }
    }

    /**
     * 删除Java源代码中的注释
     *
     * @param sourceCode 原始Java源代码
     * @return 删除注释后的代码
     */
    public static String removeComments(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return sourceCode;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = sourceCode.length();

        while (i < len) {
            char ch = sourceCode.charAt(i);

            // 处理字符串字面量
            if (ch == '"') {
                result.append(ch);
                i++;
                // 跳过字符串内容，直到找到结束的引号
                while (i < len) {
                    ch = sourceCode.charAt(i);
                    result.append(ch);
                    if (ch == '"') {
                        i++;
                        break;
                    }
                    // 处理转义字符
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        if (i < len) {
                            result.append(sourceCode.charAt(i));
                        }
                    }
                    i++;
                }
                continue;
            }

            // 处理字符字面量
            if (ch == '\'') {
                result.append(ch);
                i++;
                // 跳过字符内容，直到找到结束的单引号
                while (i < len) {
                    ch = sourceCode.charAt(i);
                    result.append(ch);
                    if (ch == '\'') {
                        i++;
                        break;
                    }
                    // 处理转义字符
                    if (ch == '\\' && i + 1 < len) {
                        i++;
                        if (i < len) {
                            result.append(sourceCode.charAt(i));
                        }
                    }
                    i++;
                }
                continue;
            }

            // 检查单行注释 //
            if (ch == '/' && i + 1 < len && sourceCode.charAt(i + 1) == '/') {
                // 跳过单行注释，直到行尾
                while (i < len && sourceCode.charAt(i) != '\n') {
                    i++;
                }
                // 保留换行符
                if (i < len && sourceCode.charAt(i) == '\n') {
                    result.append('\n');
                    i++;
                }
                continue;
            }

            // 检查多行注释 /* */
            if (ch == '/' && i + 1 < len && sourceCode.charAt(i + 1) == '*') {
                i += 2; // 跳过 /*
                // 查找注释结束 */
                while (i + 1 < len) {
                    if (sourceCode.charAt(i) == '*' && sourceCode.charAt(i + 1) == '/') {
                        i += 2; // 跳过 */
                        break;
                    }
                    i++;
                }
                continue;
            }

            // 普通字符，直接添加
            result.append(ch);
            i++;
        }

        return result.toString();
    }

}