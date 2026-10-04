package io.nop.treesitter.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.ITreeSitterLanguageProvider;
import io.nop.treesitter.shell.DynamicConstruct;
import io.nop.treesitter.shell.HeredocScript;
import io.nop.treesitter.shell.LiteralHarvest;
import io.nop.treesitter.shell.ProvenCommandSequence;
import io.nop.treesitter.shell.ProvenSplit;
import io.nop.treesitter.shell.ShellAnalysis;
import io.nop.treesitter.shell.ShellPath;
import io.nop.treesitter.shell.ShellSummary;
import io.nop.treesitter.shell.UnprovenScript;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.treesitter.biz.TreeSitterErrors.ARG_EXPECTED_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ARG_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ARG_MISSING_NODES;
import static io.nop.treesitter.biz.TreeSitterErrors.ERR_TREE_SITTER_UNKNOWN_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ERR_TREE_SITTER_UNSUPPORTED_SHELL_LANGUAGE;

/**
 * The GraphQL surface of the shell analysis layer.
 *
 * <p><b>Boundary decision.</b> The layer's capability 1 returns a sealed
 * two-case result, and the type system is what stops a harvest being used as a
 * proof inside Java. That protection does not survive a serialized boundary, so
 * across GraphQL the distinction is carried as an explicit
 * {@code proofStatus} field on every summary rather than as a type. A caller that
 * reads {@code proofStatus} has the same guarantee a Java caller gets from
 * {@code instanceof}; a caller that ignores it has been warned by the field's
 * name. For the same reason the results are flattened to plain maps rather than
 * exposed as schema object types: the flattening makes the status field
 * unmissable, and it keeps the schema free of types whose sealed structure the
 * wire format cannot express.
 *
 * <p>Query names avoid the platform's reserved action names, and the harvest
 * query is named {@code listLiteralCommands} rather than something like
 * {@code parseCommands}: the name is the last place a caller sees before the type
 * system stops helping them.
 */
@BizModel("TreeSitterShell")
public class ShellAnalysisBizModel {

    /**
     * The named nodes the shell layer's contract is written against. Their absence
     * is how a non-bash grammar is detected: the layer would otherwise produce
     * confident nonsense over an unrelated grammar.
     */
    private static final List<String> REQUIRED_NODES = List.of(
            "program", "command", "command_name", "word", "string", "string_content",
            "raw_string", "concatenation", "command_substitution", "simple_expansion",
            "expansion", "heredoc_redirect", "heredoc_start", "heredoc_body", "heredoc_end",
            "redirected_statement", "file_redirect", "variable_assignment");

    private static final String SHELL_LANGUAGE = "bash";

    protected ITreeSitterLanguageProvider languageProvider;

    @Inject
    public void setLanguageProvider(ITreeSitterLanguageProvider languageProvider) {
        this.languageProvider = languageProvider;
    }

    /**
     * Analyses a script and returns everything the layer found, including the proof
     * status.
     */
    @BizQuery
    public Map<String, Object> analyzeShell(@Name("source") String source,
                                            @Name("language") String language,
                                            IServiceContext ctx) {
        ShellAnalysis analysis = analysisFor(language);
        String script = source == null ? "" : source;
        ShellSummary summary = analysis.summarize(script);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("proofStatus", summary.proofStatus().name());
        result.put("unprovenReason", summary.unprovenReason());
        result.put("commandNames", summary.commandNames());
        result.put("connectors", summary.connectors());
        result.put("hasDynamicConstructs", summary.hasDynamicConstructs());
        result.put("dynamicConstructKinds", summary.dynamicConstructKinds().stream()
                .map(Enum::name).toList());
        result.put("paths", summary.paths().stream().map(ShellAnalysisBizModel::describePath).toList());
        result.put("dynamicConstructs", analysis.findDynamicConstructs(script).stream()
                .map(ShellAnalysisBizModel::describeDynamicConstruct).toList());
        return result;
    }

    /**
     * The proven command sequence, or an explicit refusal. Never an empty list for
     * "I could not prove it".
     */
    @BizQuery
    public Map<String, Object> splitProvenShellCommands(@Name("source") String source,
                                                        @Name("language") String language,
                                                        IServiceContext ctx) {
        ProvenSplit split = analysisFor(language).splitProven(source == null ? "" : source);
        Map<String, Object> result = new LinkedHashMap<>();
        if (split instanceof ProvenCommandSequence proof) {
            result.put("proven", true);
            result.put("commands", proof.commands().stream()
                    .map(ShellAnalysisBizModel::describeProvenCommand).toList());
        } else {
            UnprovenScript refusal = (UnprovenScript) split;
            result.put("proven", false);
            result.put("reason", refusal.reason().name());
            result.put("nodeType", refusal.nodeType());
            result.put("byteOffset", refusal.byteOffset());
            result.put("detail", refusal.detail());
        }
        return result;
    }

    /**
     * Literal commands found anywhere in the script. <b>Incomplete by
     * construction</b>; the name says "literal" and every row carries its own
     * {@code partial} flag rather than relying on the caller to remember.
     */
    @BizQuery
    public List<Map<String, Object>> listLiteralCommands(@Name("source") String source,
                                                         @Name("language") String language,
                                                         IServiceContext ctx) {
        LiteralHarvest harvest = analysisFor(language).harvestLiterals(source == null ? "" : source);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LiteralHarvest.Command command : harvest.commands()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", command.name());
            row.put("arguments", command.arguments());
            row.put("partial", command.partial());
            row.put("byteOffset", command.byteOffset());
            rows.add(row);
        }
        return rows;
    }

    /** The heredoc body, when the script is nothing but a heredoc statement. */
    @BizQuery
    public Map<String, Object> extractShellHeredoc(@Name("source") String source,
                                                   @Name("language") String language,
                                                   @Name("allowedCommands") List<String> allowedCommands,
                                                   IServiceContext ctx) {
        HeredocScript.Lookup lookup = analysisFor(language).extractHeredoc(
                source == null ? "" : source,
                allowedCommands == null ? java.util.Set.of() : java.util.Set.copyOf(allowedCommands));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", lookup.isFound());
        if (lookup.found() != null) {
            HeredocScript.Found found = lookup.found();
            result.put("delimiter", found.delimiter());
            result.put("body", found.body());
            result.put("quotedDelimiter", found.quotedDelimiter());
            result.put("bodyIsLiteral", found.bodyIsLiteral());
            result.put("commandName", found.commandName());
            result.put("bodyStartByte", found.bodyStartByte());
            result.put("bodyEndByte", found.bodyEndByte());
        } else {
            result.put("reason", lookup.reason());
        }
        return result;
    }

    private ShellAnalysis analysisFor(String language) {
        Language resolved = languageProvider == null ? null : languageProvider.getLanguage(language);
        if (resolved == null) {
            throw new NopException(ERR_TREE_SITTER_UNKNOWN_LANGUAGE).param(ARG_LANGUAGE, language);
        }
        List<String> missing = new ArrayList<>();
        for (String node : REQUIRED_NODES) {
            if (resolved.symbolId(node, true) < 0) {
                missing.add(node);
            }
        }
        if (!missing.isEmpty()) {
            throw new NopException(ERR_TREE_SITTER_UNSUPPORTED_SHELL_LANGUAGE)
                    .param(ARG_LANGUAGE, language)
                    .param(ARG_EXPECTED_LANGUAGE, SHELL_LANGUAGE)
                    .param(ARG_MISSING_NODES, String.join(",", missing));
        }
        return new ShellAnalysis(resolved);
    }

    private static Map<String, Object> describeProvenCommand(ProvenCommandSequence.Command command) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", command.name());
        row.put("arguments", command.arguments());
        row.put("separator", command.separator());
        return row;
    }

    private static Map<String, Object> describeDynamicConstruct(DynamicConstruct construct) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", construct.kind().name());
        row.put("startByte", construct.startByte());
        row.put("endByte", construct.endByte());
        row.put("text", construct.text());
        return row;
    }

    private static Map<String, Object> describePath(ShellPath path) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", path.path());
        row.put("access", path.access().name());
        row.put("confidence", path.confidence().name());
        return row;
    }
}
