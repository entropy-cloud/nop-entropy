package io.nop.ai.agent.guardrail.test;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.Collections;
import java.util.List;

/**
 * {@link AttackPlugin} backed by declarative YAML corpus files loaded from a
 * virtual-filesystem directory (design {@code guardrail-contract.md} §增量 1,
 * Decision F). The shipped 60+ attack corpus lives under
 * {@code /nop/ai/agent/guardrail-test/corpus/} and is loaded via this plugin.
 */
public class CorpusAttackPlugin implements AttackPlugin {

    public static final String DEFAULT_NAME = "guardrail-redteam-corpus";
    public static final String DEFAULT_CORPUS_DIR = "/nop/ai/agent/guardrail-test/corpus";

    private final String name;
    private final String corpusDir;
    private final List<AttackCase> cases;

    public CorpusAttackPlugin() {
        this(DEFAULT_NAME, DEFAULT_CORPUS_DIR);
    }

    public CorpusAttackPlugin(String name, String corpusDir) {
        if (name == null || name.isEmpty()) {
            throw new NopAiAgentException("CorpusAttackPlugin: name must not be empty");
        }
        if (corpusDir == null || corpusDir.isEmpty()) {
            throw new NopAiAgentException("CorpusAttackPlugin: corpusDir must not be empty");
        }
        this.name = name;
        this.corpusDir = corpusDir;
        this.cases = Collections.unmodifiableList(CorpusLoader.loadDirectory(corpusDir));
    }

    @Override
    public String name() {
        return name;
    }

    public String getCorpusDir() {
        return corpusDir;
    }

    @Override
    public List<AttackCase> cases() {
        return cases;
    }
}
