package io.nop.ai.code_analyzer.code;

import io.nop.api.core.ioc.BeanContainer;

import static io.nop.ai.code_analyzer.CodeAnalyzerConstants.BEAN_PREFIX_SOURCE_CODE_ANALYZER;

public interface ISourceCodeAnalyzer {
    CodeFileInfo analyze(String path, String code);

    static boolean isSupported(String lang) {
        return BeanContainer.instance().containsBean(BEAN_PREFIX_SOURCE_CODE_ANALYZER + lang);
    }

    static ISourceCodeAnalyzer getAnalyzer(String lang) {
        return (ISourceCodeAnalyzer) BeanContainer.instance().getBean(BEAN_PREFIX_SOURCE_CODE_ANALYZER + lang);
    }
}
