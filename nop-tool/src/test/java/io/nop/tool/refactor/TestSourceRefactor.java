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
}
