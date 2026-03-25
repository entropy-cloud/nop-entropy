package io.nop.ai.toolkit.fs;

/**
 * 文件信息 Bean，所有字段 final
 */
public final class FileInfo {
    private final String path;
    private final String name;
    private final boolean directory;
    private final long size;
    private final long lastModified;

    public FileInfo(String path, String name, boolean directory, long size, long lastModified) {
        this.path = path;
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String toFormattedString() {
        String type = directory ? "[DIR]" : "[FILE]";
        return type + " " + path + " " + size + " " + lastModified;
    }
}
