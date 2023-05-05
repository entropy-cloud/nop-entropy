package io.nop.tool.refactor;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.ITextTokenizer;
import io.nop.commons.text.tokenizer.IToken;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

public class SourceRefactor {
    private final ITextTokenizer tokenizer;
    private final Function<IToken, IToken> transformer;

    public SourceRefactor(ITextTokenizer tokenizer, Function<IToken, IToken> transformer) {
        this.tokenizer = tokenizer;
        this.transformer = transformer;
    }

    public String refactor(SourceLocation loc, String text) {
        StringBuilder sb = new StringBuilder();
        Iterator<IToken> it = tokenizer.tokenize(loc, text);
        while (it.hasNext()) {
            IToken token = transformer.apply(it.next());
            if (token != null)
                sb.append(token.getText());
        }
        return sb.toString();
    }

    public void refactorResource(IResource sourceFile, IResource targetFile) {
        String text = ResourceHelper.readText(sourceFile);
        String refactored = refactor(SourceLocation.fromPath(sourceFile.getStdPath()), text);
        ResourceHelper.writeText(targetFile, refactored);
    }

    public void refactorFile(File sourceFile, File targetFile) {
        refactorResource(new FileResource(sourceFile), new FileResource(targetFile));
    }

    public void refactorDir(File sourceDir, Predicate<File> filter, File targetDir) {
        if (sourceDir.isDirectory()) {
            File[] subs = sourceDir.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    refactorDir(sub, filter, new File(targetDir, sub.getName()));
                }
            }
        } else {
            if (filter == null || filter.test(sourceDir)) {
                refactorFile(sourceDir, targetDir);
            }
        }
    }
}