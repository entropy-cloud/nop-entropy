/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tool.refactor;

import io.nop.commons.text.tokenizer.ITextTokenizer;
import io.nop.commons.text.tokenizer.IToken;
import io.nop.commons.text.tokenizer.NamespaceTextTokenizer;
import io.nop.commons.text.tokenizer.SimpleTextTokenizer;
import io.nop.core.unittest.BaseTestCase;
import io.nop.tool.refactor.pattern.TokenPatternNormalizer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSourceRefactor extends BaseTestCase {

    @Test
    public void testRefactor() {
        File dir = new File(getTestResourcesDir(), "data");
        Map<String, String> map = new HashMap<>();
        map.put("getUserId", "getId");
        map.put("userId", "id");
        map.put("kind", "abc");
        map.put("ext:kind", "kind");

        Function<IToken, IToken> transformer = new IdentifierTransformer(map);
        ITextTokenizer tokenizer = new NamespaceTextTokenizer(new SimpleTextTokenizer());

        TokenPatternNormalizer normalizer = new TokenPatternNormalizer(tokenizer);
        normalizer.addReplaced("maven.artifactId", "mavenArtifactId");

        SourceRefactor refactor = new SourceRefactor(normalizer, transformer);
        File targetDir = new File(getTargetDir(), "data");
        refactor.refactorDir(dir, FileExtFilter.forFileExt("java", "xml", "json5", "json", "md", "txt", "xlsx"), targetDir);
    }

    @Test
    public void testPattern(){
        TokenPatternNormalizer normalizer = new TokenPatternNormalizer(new SimpleTextTokenizer());
        normalizer.addReplaced("{a.b}","{x.y123}");

        SourceRefactor refactor = new SourceRefactor(normalizer, v->v);
        String text = refactor.refactor(null,"_{a.b}.txt");
        assertEquals("_{x.y123}.txt",text);
    }

    public static void main(String[] args){
        File dir = new File("C:\\workspace\\nop-cardlite\\cardlite-gen\\src");

        Map<String,String> map = new HashMap<>();
        map.put("var","let");

        Function<IToken, IToken> transformer = new IdentifierTransformer(map);
        ITextTokenizer tokenizer = new NamespaceTextTokenizer(new SimpleTextTokenizer());

        TokenPatternNormalizer normalizer = new TokenPatternNormalizer(tokenizer);
        normalizer.addReplaced("{table.name}", "{entityModel.shortName}");
        normalizer.addReplaced("{packagePath}", "{basePackagePath}");

        SourceRefactor refactor = new SourceRefactor(normalizer, transformer);
        File targetDir = new File("c:/refactored");
        refactor.refactorDir(dir, FileExtFilter.forFileExt("xgen"), targetDir);
    }
}
