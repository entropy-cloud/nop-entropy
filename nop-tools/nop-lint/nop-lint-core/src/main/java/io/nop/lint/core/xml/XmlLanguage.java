package io.nop.lint.core.xml;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.CompiledRule;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.treesitter.language.Language;

/**
 * The XML language binding (id {@code "xml"}, design 01 §1/§3.5): parses
 * through the platform's own XNode parser — there is no tree-sitter XML
 * grammar blob — and compiles its rules through {@link XmlRuleCompiler} (the
 * {@link #compileRule(RuleDslModel)} hook), so the engine's downstream
 * pipeline (kind filter, matching, xscript, suppression tail, stats) runs
 * XML rules exactly like every other language's.
 *
 * <p>Fail-closed surface: incremental parsing has no backend on this path
 * and rejects; {@code kindId} interns only valid tag names (meta-var marker
 * forms resolve to -1, which fails rule compilation loudly); the
 * annotation-carried suppression provider is null by contract — XML
 * suppresses through its comments, which ride the always-on scanner via the
 * facade's comment trivia (design 09 §2 增注).</p>
 */
public final class XmlLanguage implements LintLanguage {

    /**
     * The registry-normalized binding id.
     */
    public static final String ID = "xml";

    private static final XmlLanguage INSTANCE = new XmlLanguage();

    /**
     * Public no-arg constructor: the JDK ServiceLoader discovers classpath
     * providers only through one. Every instance is stateless and identical.
     */
    public XmlLanguage() {
    }

    /**
     * The canonical shared instance.
     */
    public static XmlLanguage get() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Language treeSitter() {
        // No tree-sitter XML grammar exists (design 01 §1): the XML path is
        // facade-native. A null backend never reaches the tree-sitter
        // machinery — compilation goes through compileRule below.
        return null;
    }

    @Override
    public LintTree parse(String source) {
        return XmlSourceParser.parse(source);
    }

    @Override
    public LintTree parse(byte[] source) {
        return XmlSourceParser.parse(source);
    }

    @Override
    public LintTree parseIncremental(LintTree oldTree, byte[] newSource) {
        throw new NopLintException("lint language 'xml' does not support incremental parsing "
                + "(no tree-sitter backend; a full parse runs instead — the caller must not "
                + "assume one)");
    }

    @Override
    public String preprocessPattern(String patternText) {
        // XML attribute values and text carry the meta-var markers verbatim;
        // the XNode parser gives them no special meaning, so identity is
        // correct (same adjudication shape as the Java/TS bindings).
        return patternText;
    }

    @Override
    public int kindId(String kindName) {
        return XmlTagKinds.idFor(kindName);
    }

    @Override
    public CompiledRule compileRule(RuleDslModel model) {
        return XmlRuleCompiler.compile(model, this);
    }
}
