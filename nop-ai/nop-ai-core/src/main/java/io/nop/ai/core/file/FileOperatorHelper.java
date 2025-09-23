package io.nop.ai.core.file;

public class FileOperatorHelper {
    public static boolean filterNopProjectFiles(String path) {
        return !isNopIgnoredFile(path);
    }

    public static boolean isNopIgnoredFile(String path) {
        if (path.startsWith(".git/"))
            return true;

        if (path.startsWith(".idea/"))
            return true;

        if (path.startsWith(".github/"))
            return true;

        if (path.startsWith("_dump"))
            return true;

        if (path.contains("/_gen/") || path.contains("/_"))
            return true;

        if (path.contains("/target/") && !path.contains("/src/main/"))
            return true;
        return false;
    }
}
