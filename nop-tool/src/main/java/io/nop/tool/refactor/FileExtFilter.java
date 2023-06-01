package io.nop.tool.refactor;

import io.nop.commons.util.StringHelper;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class FileExtFilter implements Predicate<String> {
    private final List<String> fileExts;

    public FileExtFilter(List<String> fileExts) {
        this.fileExts = fileExts;
    }

    public static FileExtFilter forFileExt(String... fileExts) {
        return new FileExtFilter(Arrays.asList(fileExts));
    }

    @Override
    public boolean test(String path) {
        String fileExt = StringHelper.fileExt(path);
        return fileExts.contains(fileExt);
    }
}
