/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tool.refactor;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.ITextTokenizer;
import io.nop.commons.text.tokenizer.IToken;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.impl.TextOfficePackagePart;

import java.io.File;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 将文本切分为多个token，然后应用转换器逐个对token进行替换。
 */
public class SourceRefactor {
    private final ITextTokenizer tokenizer;
    private final Function<IToken, IToken> transformer;
    private boolean normalizeFileName = true;

    public SourceRefactor(ITextTokenizer tokenizer, Function<IToken, IToken> transformer) {
        this.tokenizer = tokenizer;
        this.transformer = transformer;
    }

    public SourceRefactor normalizeFileName(boolean b) {
        this.normalizeFileName = b;
        return this;
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
        if (isOfficeFile(sourceFile)) {
            refactorOfficeFile(sourceFile, targetFile);
        } else {
            String text = ResourceHelper.readText(sourceFile);
            String refactored = refactor(SourceLocation.fromPath(sourceFile.getStdPath()), text);
            ResourceHelper.writeText(targetFile, refactored);
        }
    }

    private boolean isOfficeFile(IResource file) {
        String name = file.getName();
        return name.endsWith(".xlsx") || name.endsWith(".docx");
    }

    private void refactorOfficeFile(IResource sourceFile, IResource targetFile) {
        OfficePackage pkg = new OfficePackage();
        pkg.loadFromResource(sourceFile);
        try {
            SourceLocation loc = SourceLocation.fromPath(sourceFile.getPath());
            pkg.getFiles().forEach(part -> {
                if (part.getPath().endsWith(".xml")) {
                    String text = part.loadText();
                    SourceLocation textLoc = loc.addRef(part.getPath());
                    text = refactor(textLoc, text);
                    pkg.addFile(new TextOfficePackagePart(part.getPath(), text));
                }
            });

            pkg.saveToResource(targetFile, DisabledEvalScope.INSTANCE);
        } finally {
            pkg.close();
        }
    }


    public void refactorFile(File sourceFile, File targetFile) {
        refactorResource(new FileResource(sourceFile), new FileResource(targetFile));
    }

    public void refactorDir(File sourceDir, Predicate<File> filter, File targetDir) {
        if (sourceDir.isDirectory()) {
            File[] subs = sourceDir.listFiles();
            if (subs != null) {
                for (File sub : subs) {
                    File targetSub = getTargetSub(targetDir, sub);
                    refactorDir(sub, filter, targetSub);
                }
            }
        } else {
            if (filter == null || filter.test(sourceDir)) {
                refactorFile(sourceDir, targetDir);
            }
        }
    }

    protected File getTargetSub(File dir, File sub) {
        if (!normalizeFileName)
            return new File(dir, sub.getName());

        String name = sub.getName();
        name = refactor(null, name);
        return new File(dir, name);
    }
}