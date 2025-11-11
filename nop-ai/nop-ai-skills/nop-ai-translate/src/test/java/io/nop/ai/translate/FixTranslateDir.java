package io.nop.ai.translate;

import io.nop.ai.core.file.FileOperatorHelper;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.nio.file.FileVisitResult;

public class FixTranslateDir {
    public static void main(String[] args) {
       // cleanup();

        File srcDir = new File("c:/tmp/nop-tmp/translate");
        File targetDir = new File("c:/tmp/nop-tmp/output-translate1");

        FileHelper.walk2(srcDir, targetDir, (f1, f2) -> {
            if (f1.isDirectory())
                return FileVisitResult.CONTINUE;

            if (f1.getName().endsWith(".md")) {
                fixMarkdown(f1, f2);
            } else {
                if (f1.length() != f2.length())
                    FileHelper.copyFile(f1, f2);
            }
            return FileVisitResult.CONTINUE;
        });

        if(true)
            return;

        FileHelper.walk(targetDir, file -> {
            if (file.isDirectory())
                return FileVisitResult.CONTINUE;
            if (file.getName().contains("-chunk-")) {
                file.delete();
            }
            return FileVisitResult.CONTINUE;
        });
    }

    static void cleanup() {
        File enDir = new File("c:/can/nop/nop-entropy/docs-en");
        File baseDir = new File("c:/can/nop/nop-entropy/docs");

        FileHelper.walk2(enDir, baseDir, (f1, f2) -> {
            if (!f2.exists()) {
                System.out.println("delete:"+f1);
                FileHelper.deleteAll(f1);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        });
    }

    static void fixMarkdown(File f1, File f2) {
        if (f1.length() <= 0 || !f2.exists())
            return;

        String md5 = FileOperatorHelper.fileNormalizedMd5(f1);
        String text = FileHelper.readText(f2, null);
        if (text.contains(":" + md5))
            return;
        text += "\n<!-- SOURCE_MD5:" + md5 + "-->\n";
        FileHelper.writeText(f2, text, null);
    }
}
