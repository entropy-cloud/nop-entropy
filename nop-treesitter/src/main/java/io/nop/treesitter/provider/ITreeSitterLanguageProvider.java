package io.nop.treesitter.provider;

import io.nop.treesitter.language.Language;

import java.util.Set;

/**
 * Resolves grammar names to loaded {@link Language} instances. Implemented by
 * the module's default provider (the five built-in grammars) and by third
 * parties registering under
 * {@code META-INF/services/io.nop.treesitter.provider.ITreeSitterLanguageProvider}
 * — the default provider merges those via {@code ServiceLoader}.
 */
public interface ITreeSitterLanguageProvider {

    /**
     * The language registered under {@code name}, or {@code null} when this
     * provider does not know it (never throws for unknown names).
     */
    Language getLanguage(String name);

    /**
     * Names of every grammar this provider can resolve.
     */
    Set<String> languageNames();
}
