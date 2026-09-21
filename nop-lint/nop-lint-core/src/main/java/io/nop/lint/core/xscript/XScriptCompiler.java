package io.nop.lint.core.xscript;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.lint.core.NopLintException;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.ImportDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.definition.ScopeVarDefinition;

/**
 * The compile-time whitelist for rule {@code xscript} bodies (design 07 §1,
 * §3): the script is compiled once at rule-compile time into an executable
 * expression, and every identifier it references must resolve against a
 * deliberately small surface. There is no sandbox behind this gate — an
 * unknown name fails the compile, so a rule can never run with more power
 * than the whitelist grants.
 *
 * <p>Enforcement rides entirely on public XLang compile entry points, zero
 * platform change:</p>
 * <ul>
 * <li>the compile scope is created through {@link XLang#newCompileTool()}
 * with {@code allowUnregisteredScopeVar(false)}; only {@link #VAR_NODE},
 * {@link #VAR_CAPTURES}, {@link #VAR_REPORT} and {@link #VAR_DECL_TYPE} are
 * registered scope variables, so {@code Class.forName(...)}, bare file or
 * network helper calls, and every other unknown identifier fail resolution
 * at compile time</li>
 * <li>{@code import} declarations parse but would silently register classes,
 * so the parsed AST is scanned and rejected before compilation</li>
 * <li>class definitions do not parse at all (the XLang grammar has no class
 * production) and fail as syntax errors</li>
 * </ul>
 *
 * <p>The platform's JS-style built-in globals ({@code Math}, {@code JSON},
 * {@code Number}, {@code Date}, {@code Object}, {@code Promise}) stay
 * resolvable: they map to the platform's own pure utility classes and are
 * the design's "少量内置全局". {@code typeAnalyzer}/{@code scopeAnalyzer}
 * are deliberately unregistered — they belong to later capability levels,
 * and a v1 script referencing them fails closed.</p>
 */
public final class XScriptCompiler {

    /**
     * The current match node binding.
     */
    public static final String VAR_NODE = "node";

    /**
     * The pattern capture bindings (single nodes and sequences).
     */
    public static final String VAR_CAPTURES = "captures";

    /**
     * The diagnostic report function.
     */
    public static final String VAR_REPORT = "report";

    /**
     * The L1 declaration-type query function (design 06 §5.2 consumer
     * contract).
     */
    public static final String VAR_DECL_TYPE = "declType";

    private XScriptCompiler() {
    }

    /**
     * Compiles one rule's xscript body.
     *
     * @param ruleId the owning rule's id; every rejection message carries it
     * @param script the raw xscript text
     * @return the compiled, executable expression action
     * @throws NopLintException when the script is blank, violates the
     *                          whitelist (import declaration, unresolvable
     *                          identifier), or fails to parse/compile
     */
    public static IEvalAction compile(String ruleId, String script) {
        if (script == null || script.isBlank()) {
            throw new NopLintException("Rule '" + ruleId
                    + "' declares a blank 'xscript' body (a rule that cannot run must not compile)");
        }
        XLangCompileTool tool = XLang.newCompileTool().allowUnregisteredScopeVar(false);
        registerWhitelist(tool);
        SourceLocation loc = SourceLocation.fromPath("/lint/rule/" + ruleId + ".xscript");

        Program program;
        try {
            program = tool.parseFullExpr(loc, script);
        } catch (NopException e) {
            throw new NopLintException("Rule '" + ruleId + "' has an invalid xscript: " + e.getMessage(), e);
        }
        if (program == null) {
            throw new NopLintException("Rule '" + ruleId
                    + "' declares a blank 'xscript' body (a rule that cannot run must not compile)");
        }
        rejectImports(ruleId, program);

        try {
            IEvalAction action = tool.buildEvalAction(program);
            if (action == null) {
                throw new NopLintException("Rule '" + ruleId
                        + "' compiled to no executable expression (blank xscript body)");
            }
            return action;
        } catch (NopException e) {
            throw new NopLintException("Rule '" + ruleId + "' has an invalid xscript: " + e.getMessage(), e);
        }
    }

    private static void registerWhitelist(XLangCompileTool tool) {
        tool.getScope().registerScopeVarDefinition(
                ScopeVarDefinition.readOnly(VAR_NODE, PredefinedGenericTypes.ANY_TYPE), false);
        tool.getScope().registerScopeVarDefinition(
                ScopeVarDefinition.readOnly(VAR_CAPTURES, PredefinedGenericTypes.MAP_STRING_ANY_TYPE), false);
        tool.getScope().registerScopeVarDefinition(
                ScopeVarDefinition.readOnly(VAR_REPORT, PredefinedGenericTypes.ANY_TYPE), false);
        tool.getScope().registerScopeVarDefinition(
                ScopeVarDefinition.readOnly(VAR_DECL_TYPE, PredefinedGenericTypes.ANY_TYPE), false);
    }

    private static void rejectImports(String ruleId, Program program) {
        for (XLangASTNode statement : program.getBody()) {
            if (statement instanceof ImportAsDeclaration || statement instanceof ImportDeclaration) {
                throw new NopLintException("Rule '" + ruleId + "' uses 'import' in xscript, which the "
                        + "compile-time whitelist forbids: rule scripts are self-contained expressions "
                        + "over the bound context objects");
            }
        }
    }
}
