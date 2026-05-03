package io.nop.code.core.adapter;

import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeLanguage;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * 语言适配器注册中心
 */
public class LanguageAdapterRegistry {
    private final Map<CodeLanguage, ILanguageAdapter> adapters = new HashMap<>();

    @Inject
    public void setAdapters(List<ILanguageAdapter> adapterList) {
        for (ILanguageAdapter adapter : adapterList) {
            adapters.put(adapter.getLanguage(), adapter);
        }
    }

    public ILanguageAdapter getAdapter(CodeLanguage language) {
        return adapters.get(language);
    }

    public ICodeFileAnalyzer getAnalyzer(String filePath) {
        for (ILanguageAdapter adapter : adapters.values()) {
            for (String ext : adapter.getFileExtensions()) {
                if (filePath.endsWith(ext)) {
                    return adapter.getFileAnalyzer();
                }
            }
        }
        return null;
    }

    public Set<CodeLanguage> getSupportedLanguages() {
        return adapters.keySet();
    }

    public void registerAdapter(ILanguageAdapter adapter) {
        adapters.put(adapter.getLanguage(), adapter);
    }
}
