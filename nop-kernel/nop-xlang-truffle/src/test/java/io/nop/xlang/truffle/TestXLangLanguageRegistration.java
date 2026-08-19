package io.nop.xlang.truffle;

import io.nop.xlang.truffle.lang.XLangLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语言注册可发现（roadmap I5 / Phase 2）：polyglot Engine 层可按 id {@code xl} 定位语言；
 * dsl-processor 生成的 provider 服务文件 repo-observable（classpath 资源存在且指向本语言类）。
 */
public class TestXLangLanguageRegistration {

    @Test
    public void testEngineDiscoversXLangById() {
        Engine engine = Engine.create();
        try {
            Map<String, org.graalvm.polyglot.Language> languages = engine.getLanguages();
            assertTrue(languages.containsKey(XLangLanguage.ID), "engine must discover language by id xl, found: "
                    + languages.keySet());
            org.graalvm.polyglot.Language language = languages.get(XLangLanguage.ID);
            assertEquals("XLang", language.getName());
            assertEquals(XLangLanguage.MIME_TYPE, language.getDefaultMimeType());
        } finally {
            engine.close();
        }
    }

    @Test
    public void testContextBootsWithXLang() {
        try (Context context = Context.create(XLangLanguage.ID)) {
            assertNotNull(context.getEngine());
        }
    }

    @Test
    public void testDslProcessorGeneratedProviderFileOnClasspath() throws IOException {
        String providerResource = "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(providerResource);
        if (in == null)
            in = TestXLangLanguageRegistration.class.getClassLoader().getResourceAsStream(providerResource);
        assertNotNull(in, "dsl-processor generated provider service file must be on classpath: " + providerResource);
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        in.close();
        assertTrue(content.contains("io.nop.xlang.truffle.lang.XLangLanguageProvider"),
                "provider file must list the generated XLangLanguageProvider: " + content);
    }
}
