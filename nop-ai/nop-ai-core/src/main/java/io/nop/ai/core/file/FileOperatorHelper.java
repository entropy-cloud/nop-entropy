package io.nop.ai.core.file;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;

import java.io.File;

public class FileOperatorHelper {
    public static boolean filterNopProjectFiles(String path) {
        return !isNopIgnoredFile(path);
    }

    public static boolean isNopIgnoredFile(String path) {
        // Dot-prefixed at root or any segment (e.g., .git, .vscode, .DS_Store)
        if (path.startsWith(".") || path.contains("/."))
            return true;

        if (path.startsWith("dist/"))
            return true;

        if (path.startsWith("build/"))
            return true;

        if (path.startsWith("out/"))
            return true;

        if (path.startsWith("bin/"))
            return true;

        if (path.startsWith("obj/"))
            return true;

        if (path.contains("node_modules"))
            return true;

        if (path.startsWith("_dump") || path.contains("/_dump/"))
            return true;

        if (path.contains("/_gen/") || path.contains("/_"))
            return true;

        if (path.contains("/_cases/"))
            return true;

        String fileExt = StringHelper.fileExt(path);
        if (fileExt.equals("log") || fileExt.equals("db") || fileExt.equals("tmp"))
            return true;

        int pos = path.indexOf("/src/main/");
        if (pos < 0) {
            if (path.startsWith("target/") || path.contains("/target/"))
                return true;
        } else {
            int pos2 = path.lastIndexOf("target/", pos);
            if (pos2 < 0)
                return true;
        }
        return false;
    }

    public static String normalizedMd5(String text) {
        text = StringHelper.normalizeCRLF(text, false);
        return StringHelper.md5Hash(text);
    }

    public static String fileNormalizedMd5(File file) {
        String text = FileHelper.readText(file, null);
        return normalizedMd5(text);
    }
}
