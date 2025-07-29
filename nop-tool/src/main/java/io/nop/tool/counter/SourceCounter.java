package io.nop.tool.counter;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SourceCounter {
    private final Map<String, AtomicLong> counterByFileExt = new ConcurrentHashMap<>();
    private Set<String> fileExtensions = Set.of("java", "xml", "json5", "yaml");
    private boolean includeTests;

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
            String fileExt = StringHelper.fileExt(file.getName());
            if (fileExtensions.contains(fileExt)) {
                AtomicLong value = counterByFileExt.computeIfAbsent(fileExt, k -> new AtomicLong());
                value.addAndGet(getCodeLines(file));
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

    public Map<String, Long> getResults() {
        Map<String, Long> ret = new TreeMap<>();
        counterByFileExt.forEach((name, value) -> {
            ret.put(name, value.get());
        });
        return ret;
    }

    protected boolean shouldIgnore(File dir) {
        String name = dir.getName();
        if (name.startsWith("."))
            return true;
        if (name.equals("_dump"))
            return true;

        if (name.equals("target") && new File(dir.getParent(), "pom.xml").exists()) {
            return true;
        }

        if (!includeTests) {
            String path = dir.getPath().replace('\\', '/');
            if (path.endsWith("src/test"))
                return true;
        }
        return false;
    }

    protected int getCodeLines(File file) {
        String text = this.readText(file);
        if (StringHelper.isBlank(text))
            return 0;
        return StringHelper.stripedSplit(text, '\n').size();
    }

    protected String readText(File file) {
        try {
            return FileHelper.readText(file, null);
        } catch (Exception e) {
            return FileHelper.readText(file, "GBK");
        }
    }
}