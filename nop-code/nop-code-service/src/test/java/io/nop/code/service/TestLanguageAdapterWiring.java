package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.service.api.ICodeIndexService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接线验证测试（Wiring Verification Rule，[G11-03-01]）：经 autotest 装载 merged beans 后，
 * 六个语言模块（java/python/typescript/go/csharp/rust）的 ILanguageAdapter bean
 * 必须全部在容器中可解析，且经 CodeIndexService.setRegistry 的生产接线路径
 * 注册进 LanguageAdapterRegistry。
 * <p>
 * 防止 app-service.beans.xml 再次漏 import 某个 _lang-*.beans.xml：
 * 漏 import 时 getBeansOfType 与 getSupportedLanguages 均缺失对应语言，
 * ProjectAnalyzer 对该语言文件静默跳过（getAnalyzer()==null）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestLanguageAdapterWiring extends JunitBaseTestCase {

    private static final Set<CodeLanguage> SIX_LANGUAGES = EnumSet.of(
            CodeLanguage.JAVA, CodeLanguage.PYTHON, CodeLanguage.TYPESCRIPT,
            CodeLanguage.GO, CodeLanguage.RUST, CodeLanguage.CSHARP);

    private static final String[] SIX_ADAPTER_BEAN_IDS = {
            "io.nop.code.lang.java.JavaLanguageAdapter",
            "io.nop.code.lang.python.PythonLanguageAdapter",
            "io.nop.code.lang.typescript.TypeScriptLanguageAdapter",
            "io.nop.code.lang.go.GoLanguageAdapter",
            "io.nop.code.lang.csharp.CSharpLanguageAdapter",
            "io.nop.code.lang.rust.RustLanguageAdapter"
    };

    @Inject
    LanguageAdapterRegistry registry;

    @Inject
    ICodeIndexService codeIndexService;

    /**
     * merged beans 实测：容器装配后六个语言 adapter bean 均可解析。
     */
    @Test
    public void testContainerResolvesSixLanguageAdapterBeans() {
        Map<String, ILanguageAdapter> beans =
                BeanContainer.instance().getBeansOfType(ILanguageAdapter.class);
        for (String beanId : SIX_ADAPTER_BEAN_IDS) {
            assertTrue(beans.containsKey(beanId),
                    "ILanguageAdapter bean missing from container: " + beanId
                            + ", resolved beans = " + beans.keySet());
        }
    }

    /**
     * 生产接线路径实测：CodeIndexService.setRegistry 经
     * BeanContainer.getBeansOfType(ILanguageAdapter.class) 将六个 adapter 注册进 registry，
     * getSupportedLanguages() 覆盖六语言且每个语言 getAdapter() 均可解析。
     */
    @Test
    public void testRegistrySupportsSixLanguages() {
        Set<CodeLanguage> supported = registry.getSupportedLanguages();
        for (CodeLanguage lang : SIX_LANGUAGES) {
            assertTrue(supported.contains(lang),
                    "language not registered in LanguageAdapterRegistry: " + lang
                            + ", supported = " + supported);
            assertNotNull(registry.getAdapter(lang),
                    "no adapter resolvable for language: " + lang);
        }
    }
}
