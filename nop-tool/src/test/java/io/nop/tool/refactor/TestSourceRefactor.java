package io.nop.tool.refactor;

import io.nop.commons.text.tokenizer.IToken;
import io.nop.commons.text.tokenizer.SimpleTextTokenizer;
import io.nop.core.unittest.BaseTestCase;
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

        Function<IToken, IToken> transformer = new IdentifierTransformer(map);
        SourceRefactor refactor = new SourceRefactor(new SimpleTextTokenizer(), transformer);
        File targetDir = new File(getTargetDir(), "data");
        refactor.refactorDir(dir, FileExtFilter.forFileExt("java", "xml", "json5", "json","md","txt"), targetDir);
    }
}
