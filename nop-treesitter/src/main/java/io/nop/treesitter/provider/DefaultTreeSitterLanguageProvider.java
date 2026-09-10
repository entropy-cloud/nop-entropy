package io.nop.treesitter.provider;

import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.PythonScanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The module's built-in grammar provider: the five shipped grammars loaded
 * from their classpath blobs (cached after first use), extended by third-party
 * providers discovered through {@code ServiceLoader}. A custom provider's
 * grammar name shadows the built-in of the same name.
 *
 * <p>This class never publishes itself through
 * {@code META-INF/services} — it is exposed as an IoC bean only, and the
 * ServiceLoader merge skips it defensively so a misconfigured deployment
 * cannot recurse.</p>
 */
public class DefaultTreeSitterLanguageProvider implements ITreeSitterLanguageProvider {

    private static final Map<String, String> BUILTIN_BLOBS = Map.of(
            "json", "/grammars/json/tree-sitter-json-blob.bin",
            "java", "/grammars/java/tree-sitter-java-blob.bin",
            "javascript", "/grammars/javascript/tree-sitter-javascript-blob.bin",
            "python", "/grammars/python/tree-sitter-python-blob.bin",
            "typescript", "/grammars/typescript/tree-sitter-typescript-blob.bin",
            "tsx", "/grammars/tsx/tree-sitter-tsx-blob.bin");

    private final Map<String, Language> cache = new ConcurrentHashMap<>();
    private volatile Map<String, ITreeSitterLanguageProvider> customProviders;

    @Override
    public Language getLanguage(String name) {
        if (name == null) {
            return null;
        }
        Language cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        Language language = loadLanguage(name);
        if (language != null) {
            cache.put(name, language);
        }
        return language;
    }

    @Override
    public Set<String> languageNames() {
        Set<String> names = new java.util.LinkedHashSet<>(BUILTIN_BLOBS.keySet());
        names.addAll(customProviders().keySet());
        return names;
    }

    private Language loadLanguage(String name) {
        ITreeSitterLanguageProvider provider = customProviders().get(name);
        if (provider != null) {
            return provider.getLanguage(name);
        }
        String blobPath = BUILTIN_BLOBS.get(name);
        if (blobPath == null) {
            return null;
        }
        Language language = Language.fromClasspath(blobPath);
        if ("python".equals(name)) {
            language.setExternalScannerFactory(PythonScanner::new);
        }
        return language;
    }

    /**
     * Third-party providers discovered on the classpath, indexed by every name
     * they advertise; the default class itself is skipped so a services file
     * that names it cannot recurse. Overridable in tests to exercise
     * name-shadowing without a services file.
     */
    protected List<ITreeSitterLanguageProvider> loadCustomProviders() {
        List<ITreeSitterLanguageProvider> providers = new ArrayList<>();
        for (ITreeSitterLanguageProvider provider : ServiceLoader
                .load(ITreeSitterLanguageProvider.class)) {
            if (provider == this || provider.getClass() == DefaultTreeSitterLanguageProvider.class) {
                continue;
            }
            providers.add(provider);
        }
        return providers;
    }

    private Map<String, ITreeSitterLanguageProvider> customProviders() {
        Map<String, ITreeSitterLanguageProvider> loaded = customProviders;
        if (loaded != null) {
            return loaded;
        }
        synchronized (this) {
            if (customProviders == null) {
                Map<String, ITreeSitterLanguageProvider> merged = new LinkedHashMap<>();
                for (ITreeSitterLanguageProvider provider : loadCustomProviders()) {
                    for (String name : provider.languageNames()) {
                        merged.putIfAbsent(name, provider);
                    }
                }
                customProviders = merged;
                return merged;
            }
            return customProviders;
        }
    }
}
