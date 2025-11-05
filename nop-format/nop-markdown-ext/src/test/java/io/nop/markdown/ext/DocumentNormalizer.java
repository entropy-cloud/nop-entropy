package io.nop.markdown.ext;

import java.io.File;

public class DocumentNormalizer {
    public static void main(String[] args) {
        File dir = new File("c:/can/nop/nop-entropy/docs");
        new MarkdownNormalizer().normalizeDir(dir);
    }
}
