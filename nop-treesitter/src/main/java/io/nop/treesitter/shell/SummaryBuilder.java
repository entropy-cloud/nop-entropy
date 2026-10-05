package io.nop.treesitter.shell;

import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Capability 6: one record carrying all six capabilities' findings.
 *
 * <p>The proof status is a <b>value</b> here, not an absence. A summary that said
 * "not proven" and a caller that received the original script text with no
 * analysis would look identical from the outside, and the second would be read as
 * the first — as if the script had been analysed and found acceptable. Making the
 * status a field is what separates them, and there is a test that separates them
 * too.</p>
 */
final class SummaryBuilder {

    private final Language language;
    private final ShellAnalysis analysis;

    SummaryBuilder(Language language, ShellAnalysis analysis) {
        this.language = language;
        this.analysis = analysis;
    }

    ShellSummary summarize(String script) {
        List<DynamicConstruct> dynamic = analysis.findDynamicConstructs(script);
        Set<DynamicConstruct.Kind> kinds = new LinkedHashSet<>();
        for (DynamicConstruct construct : dynamic) {
            kinds.add(construct.kind());
        }

        ProvenSplit proven = analysis.splitProven(script);
        if (proven instanceof ProvenCommandSequence sequence) {
            List<String> names = sequence.commandNames();
            List<String> connectors = new ArrayList<>();
            for (ProvenCommandSequence.Command command : sequence.commands()) {
                if (!command.separator().isEmpty()) {
                    connectors.add(command.separator());
                }
            }
            return new ShellSummary(ShellSummary.ProofStatus.PROVEN, "", names, connectors,
                    !kinds.isEmpty(), new ArrayList<>(kinds),
                    analysis.extractPaths(script));
        }

        UnprovenScript unproven = (UnprovenScript) proven;
        LiteralHarvest harvest = analysis.harvestLiterals(script);
        return new ShellSummary(ShellSummary.ProofStatus.NOT_PROVEN, unproven.reason().name(),
                harvest.commandNames(), List.of(), !kinds.isEmpty(), new ArrayList<>(kinds),
                analysis.extractPaths(script));
    }


}
