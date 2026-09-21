package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Resolves the {@code language} field of a rule to a {@link LintLanguage}
 * binding (the discovery mechanism deferred by plan 02 to the rule-engine
 * item). Bindings are discovered through the JDK {@link ServiceLoader}
 * mechanism: language modules ship
 * {@code META-INF/services/io.nop.lint.core.lang.LintLanguage}; tests and
 * embedders may also {@link #register(LintLanguage)} manually.
 *
 * <p>Decision: language ids are matched case-insensitively after trimming
 * ({@link Locale#ROOT} lower case). The existing rule fixtures write
 * {@code language: Java} while binding ids are lowercase ({@code "java"});
 * exact-case matching would silently fail every fixture rule. Blank or
 * unknown ids fail closed with the original value in the message.</p>
 */
public final class LanguageRegistry {

    private final Map<String, LintLanguage> languages = new HashMap<>();

    /**
     * An empty registry; populate via {@link #register(LintLanguage)}.
     */
    public static LanguageRegistry empty() {
        return new LanguageRegistry();
    }

    /**
     * A registry populated through ServiceLoader discovery of
     * {@link LintLanguage} providers on the context class loader (this
     * class's loader when the context loader is unset). Provider loading
     * failures propagate: a broken binding must not vanish silently.
     */
    public static LanguageRegistry discoverDefaults() {
        LanguageRegistry registry = new LanguageRegistry();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = contextLoader != null ? contextLoader : LanguageRegistry.class.getClassLoader();
        for (LintLanguage language : ServiceLoader.load(LintLanguage.class, loader)) {
            registry.register(language);
        }
        return registry;
    }

    /**
     * Registers a binding under its normalized id. Re-registering the same
     * instance is an idempotent no-op; binding the same id to a different
     * instance fails closed — two providers claiming one id is a discovery
     * conflict, not a winner-takes-all race.
     */
    public void register(LintLanguage language) {
        if (language == null) {
            throw new NopLintException("lint language binding must not be null");
        }
        String normalized = normalize(language.id());
        if (normalized == null) {
            throw new NopLintException("lint language binding declares a blank id: "
                    + language.getClass().getName());
        }
        LintLanguage existing = languages.get(normalized);
        if (existing != null && existing != language) {
            throw new NopLintException("lint language id '" + language.id() + "' is already bound to "
                    + existing.getClass().getName() + "; conflicting registration from "
                    + language.getClass().getName());
        }
        languages.put(normalized, language);
    }

    /**
     * Resolves a rule's language field to a binding. Blank or unknown ids
     * fail closed; the message carries the original value so a mistyped rule
     * is diagnosable instead of silently matching nothing.
     */
    public LintLanguage resolve(String languageId) {
        String normalized = normalize(languageId);
        if (normalized == null) {
            throw new NopLintException("lint language id must not be blank (got: "
                    + (languageId == null ? "null" : "'" + languageId + "'") + ")");
        }
        LintLanguage language = languages.get(normalized);
        if (language == null) {
            throw new NopLintException("unknown lint language '" + languageId
                    + "'; registered languages: " + registeredIds());
        }
        return language;
    }

    /**
     * The normalized, sorted ids of all registered bindings.
     */
    public List<String> registeredIds() {
        List<String> ids = new ArrayList<>(languages.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    private static String normalize(String languageId) {
        if (languageId == null) {
            return null;
        }
        String trimmed = languageId.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
