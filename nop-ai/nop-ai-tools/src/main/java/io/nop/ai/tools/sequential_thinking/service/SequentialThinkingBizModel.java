package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.ProcessThoughtRequest;
import io.nop.ai.tools.sequential_thinking.model.ThoughtAnalysis;
import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.ai.tools.sequential_thinking.model.ThoughtSummary;
import io.nop.ai.tools.utils.AiToolsHelper;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Objects;

@BizModel("SequentialThinking")
public class SequentialThinkingBizModel {
    private ThoughtStorage storage;
    private ThoughtAnalyzer analyzer;
    private String storageDirPath;

    public void setStorageDirPath(String storageDirPath) {
        this.storageDirPath = storageDirPath;
    }

    public void setThoughtStorage(ThoughtStorage storage) {
        this.storage = storage;
    }

    public void setThoughtAnalyzer(ThoughtAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @PostConstruct
    public void init() {
        if (storage == null)
            this.storage = new ThoughtStorage(storageDirPath);
        if (analyzer == null)
            this.analyzer = new ThoughtAnalyzer();
    }

    @BizMutation
    public ThoughtAnalysis processThought(@RequestBean ProcessThoughtRequest request, IServiceContext ctx) {
        Objects.requireNonNull(request, "request cannot be null");

        String thought = request.getThought();
        if (thought == null || thought.trim().isEmpty()) {
            throw new IllegalArgumentException("Thought cannot be empty");
        }

        if (request.getThoughtNumber() <= 0) {
            throw new IllegalArgumentException("Thought number must be positive");
        }

        if (request.getTotalThoughts() <= 0) {
            throw new IllegalArgumentException("Total thoughts must be positive");
        }

        if (request.getThoughtNumber() > request.getTotalThoughts()) {
            throw new IllegalArgumentException("Thought number cannot exceed total thoughts");
        }

        if (request.getStage() == null) {
            throw new IllegalArgumentException("Stage cannot be null");
        }

        String sessionId = AiToolsHelper.makeChatSessionId(ctx);

        ThoughtStage thoughtStage = ThoughtStage.fromString(request.getStage());

        ThoughtData thoughtData = new ThoughtData();
        thoughtData.setThought(thought);
        thoughtData.setThoughtNumber(request.getThoughtNumber());
        thoughtData.setTotalThoughts(request.getTotalThoughts());
        thoughtData.setNextThoughtNeeded(request.isNextThoughtNeeded());
        thoughtData.setStage(thoughtStage);
        thoughtData.setTags(request.getTags() != null ? request.getTags() : List.of());
        thoughtData.setAxiomsUsed(request.getAxiomsUsed() != null ? request.getAxiomsUsed() : List.of());
        thoughtData.setAssumptionsChallenged(
                request.getAssumptionsChallenged() != null ? request.getAssumptionsChallenged() : List.of());

        storage.addThought(sessionId, thoughtData);
        List<ThoughtData> allThoughts = storage.getAllThoughts(sessionId);

        return analyzer.analyzeThought(thoughtData, allThoughts);
    }

    @BizQuery
    public ThoughtSummary generateSummary(IServiceContext ctx) {
        String sessionId = AiToolsHelper.makeChatSessionId(ctx);
        List<ThoughtData> allThoughts = storage.getAllThoughts(sessionId);
        return analyzer.generateSummary(allThoughts);
    }

    @BizQuery
    public void clearHistory(IServiceContext ctx) {
        String sessionId = AiToolsHelper.makeChatSessionId(ctx);
        storage.clearHistory(sessionId);
    }
}