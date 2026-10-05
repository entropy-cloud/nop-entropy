package io.nop.treesitter.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.treesitter.biz.dto.ShellAnalysisBean;
import io.nop.treesitter.biz.dto.ShellDynamicConstructBean;
import io.nop.treesitter.biz.dto.ShellHeredocBean;
import io.nop.treesitter.biz.dto.ShellLiteralCommandBean;
import io.nop.treesitter.biz.dto.ShellPathBean;
import io.nop.treesitter.biz.dto.ShellProvenCommandBean;
import io.nop.treesitter.biz.dto.ShellProvenSplitBean;
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
import java.util.List;
import java.util.Set;

import static io.nop.treesitter.biz.TreeSitterErrors.ARG_EXPECTED_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ARG_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ARG_MISSING_NODES;
import static io.nop.treesitter.biz.TreeSitterErrors.ERR_TREE_SITTER_UNKNOWN_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ERR_TREE_SITTER_UNSUPPORTED_SHELL_LANGUAGE;

/**
 * The GraphQL surface of the shell analysis layer.
 *
 * <p><b>Boundary decision.</b> The layer's proven split is a sealed two-case result,
 * and the type system is what stops a literal harvest being used as a proof inside
 * Java. That protection does not survive a serialized boundary, so across GraphQL
 * the distinction is carried as an explicit {@code proofStatus} /
 * {@code unprovenReason} field on every result rather than as a type. A caller that
 * reads it has the same guarantee a Java caller gets from {@code instanceof}; a
 * caller that ignores it has been warned by the field's name. For the same reason
 * the harvest query is named {@code listLiteralCommands} and every row carries its
 * own {@code partial} flag: the name and the row are the last places a consumer sees
 * before the type system stops helping them.</p>
 *
 * <p>Returns are {@code @DataBean} DTOs rather than {@code Map<String, Object>}, per
 * the API guide. Request failures use the framework's {@code ApiResponse} error
 * mechanism with {@code nop.err.treesitter.*} codes; a refusal to prove a script is
 * <em>not</em> a failure and is expressed as a field, never as an error code.</p>
 *
 * <p>Query names deliberately avoid the platform's reserved standard action names
 * ({@code get} / {@code findPage} / {@code findList} / {@code save} / ... ), which
 * {@code GraphQLBizModel} rejects at registration time.</p>
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

    /** Analyses a script and returns everything the layer found, including the proof status. */
    @BizQuery
    public ShellAnalysisBean analyzeShell(@Name("source") String source,
                                          @Name("language") String language,
                                          IServiceContext ctx) {
        ShellAnalysis analysis = analysisFor(language);
        String script = source == null ? "" : source;
        ShellSummary summary = analysis.summarize(script);

        ShellAnalysisBean bean = new ShellAnalysisBean();
        bean.setProofStatus(summary.proofStatus().name());
        bean.setUnprovenReason(summary.unprovenReason());
        bean.setCommandNames(summary.commandNames());
        bean.setConnectors(summary.connectors());
        bean.setHasDynamicConstructs(summary.hasDynamicConstructs());
        bean.setDynamicConstructKinds(summary.dynamicConstructKinds().stream()
                .map(Enum::name).toList());
        bean.setPaths(summary.paths().stream()
                .map(ShellAnalysisBizModel::describePath).toList());
        bean.setDynamicConstructs(analysis.findDynamicConstructs(script).stream()
                .map(ShellAnalysisBizModel::describeDynamicConstruct).toList());
        return bean;
    }

    /**
     * The proven command sequence, or an explicit refusal. Never an empty command
     * list for "I could not prove it" — the {@code commands} field is simply absent.
     */
    @BizQuery
    public ShellProvenSplitBean splitProvenShellCommands(@Name("source") String source,
                                                          @Name("language") String language,
                                                          IServiceContext ctx) {
        ProvenSplit split = analysisFor(language).splitProven(source == null ? "" : source);
        ShellProvenSplitBean bean = new ShellProvenSplitBean();
        if (split instanceof ProvenCommandSequence proof) {
            bean.setProven(true);
            bean.setCommands(proof.commands().stream()
                    .map(ShellAnalysisBizModel::describeProvenCommand).toList());
        } else {
            UnprovenScript refusal = (UnprovenScript) split;
            bean.setProven(false);
            bean.setReason(refusal.reason().name());
            bean.setNodeType(refusal.nodeType());
            bean.setByteOffset(refusal.byteOffset());
            bean.setDetail(refusal.detail());
        }
        return bean;
    }

    /**
     * Literal commands found anywhere in the script. Incomplete by construction; see
     * {@link ShellLiteralCommandBean#getArguments()}.
     */
    @BizQuery
    public List<ShellLiteralCommandBean> listLiteralCommands(@Name("source") String source,
                                                             @Name("language") String language,
                                                             IServiceContext ctx) {
        LiteralHarvest harvest = analysisFor(language)
                .harvestLiterals(source == null ? "" : source);
        List<ShellLiteralCommandBean> beans = new ArrayList<>();
        for (LiteralHarvest.Command command : harvest.commands()) {
            ShellLiteralCommandBean bean = new ShellLiteralCommandBean();
            bean.setName(command.name());
            bean.setArguments(command.arguments());
            bean.setPartial(command.partial());
            bean.setByteOffset(command.byteOffset());
            beans.add(bean);
        }
        return beans;
    }

    /** The heredoc body, when the script is nothing but a heredoc statement. */
    @BizQuery
    public ShellHeredocBean extractShellHeredoc(@Name("source") String source,
                                                 @Name("language") String language,
                                                 @Name("allowedCommands") List<String> allowedCommands,
                                                 IServiceContext ctx) {
        HeredocScript.Lookup lookup = analysisFor(language).extractHeredoc(
                source == null ? "" : source,
                allowedCommands == null ? Set.of() : Set.copyOf(allowedCommands));
        ShellHeredocBean bean = new ShellHeredocBean();
        bean.setFound(lookup.isFound());
        HeredocScript.Found found = lookup.found();
        if (found != null) {
            bean.setDelimiter(found.delimiter());
            bean.setBody(found.body());
            bean.setQuotedDelimiter(found.quotedDelimiter());
            bean.setBodyIsLiteral(found.bodyIsLiteral());
            bean.setCommandName(found.commandName());
            bean.setBodyStartByte(found.bodyStartByte());
            bean.setBodyEndByte(found.bodyEndByte());
        } else {
            bean.setReason(lookup.reason());
        }
        return bean;
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

    private static ShellProvenCommandBean describeProvenCommand(ProvenCommandSequence.Command command) {
        ShellProvenCommandBean bean = new ShellProvenCommandBean();
        bean.setName(command.name());
        bean.setArguments(command.arguments());
        bean.setSeparator(command.separator());
        return bean;
    }

    private static ShellDynamicConstructBean describeDynamicConstruct(DynamicConstruct construct) {
        ShellDynamicConstructBean bean = new ShellDynamicConstructBean();
        bean.setKind(construct.kind().name());
        bean.setStartByte(construct.startByte());
        bean.setEndByte(construct.endByte());
        bean.setText(construct.text());
        return bean;
    }

    private static ShellPathBean describePath(ShellPath path) {
        ShellPathBean bean = new ShellPathBean();
        bean.setPath(path.path());
        bean.setAccess(path.access().name());
        bean.setConfidence(path.confidence().name());
        return bean;
    }
}
