package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.AnalysisContext;
import io.nop.ai.tools.sequential_thinking.model.AnalysisResult;
import io.nop.ai.tools.sequential_thinking.model.CurrentThoughtInfo;
import io.nop.ai.tools.sequential_thinking.model.RelatedThoughtSummary;
import io.nop.ai.tools.sequential_thinking.model.TagCount;
import io.nop.ai.tools.sequential_thinking.model.ThoughtAnalysis;
import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.ai.tools.sequential_thinking.model.ThoughtSummary;
import io.nop.ai.tools.sequential_thinking.model.TimelineEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ThoughtAnalyzer {

    public List<ThoughtData> findRelatedThoughts(
            ThoughtData currentThought,
            List<ThoughtData> allThoughts,
            int maxResults) {

        Objects.requireNonNull(currentThought, "currentThought cannot be null");
        Objects.requireNonNull(allThoughts, "allThoughts cannot be null");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }

        // 1. 同阶段的思考
        List<ThoughtData> sameStage = allThoughts.stream()
                .filter(t -> t.getStage() == currentThought.getStage()
                        && !t.getId().equals(currentThought.getId()))
                .collect(Collectors.toList());

        // 2. 相似标签的思考
        List<ThoughtData> tagRelated = new ArrayList<>();
        if (!currentThought.getTags().isEmpty()) {
            Map<ThoughtData, Integer> tagMatches = new HashMap<>();

            for (ThoughtData thought : allThoughts) {
                if (thought.getId().equals(currentThought.getId())) {
                    continue;
                }

                Set<String> matchingTags = new HashSet<>(currentThought.getTags());
                matchingTags.retainAll(thought.getTags());

                if (!matchingTags.isEmpty()) {
                    tagMatches.put(thought, matchingTags.size());
                }
            }

            tagRelated = tagMatches.entrySet().stream()
                    .sorted(Map.Entry.<ThoughtData, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        // 3. 合并和去重结果
        List<ThoughtData> combined = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // 先添加同阶段的思考
        for (ThoughtData thought : sameStage) {
            if (!seenIds.contains(thought.getId())) {
                combined.add(thought);
                seenIds.add(thought.getId());

                if (combined.size() >= maxResults) {
                    return combined;
                }
            }
        }

        // 然后添加标签相关的思考
        for (ThoughtData thought : tagRelated) {
            if (!seenIds.contains(thought.getId())) {
                combined.add(thought);
                seenIds.add(thought.getId());

                if (combined.size() >= maxResults) {
                    break;
                }
            }
        }

        return combined;
    }

    public ThoughtSummary generateSummary(List<ThoughtData> thoughts) {
        if (thoughts == null || thoughts.isEmpty()) {
            return ThoughtSummary.fromMessage("No thoughts recorded yet");
        }

        // 1. 按阶段分组
        Map<ThoughtStage, List<ThoughtData>> stages = thoughts.stream()
                .collect(Collectors.groupingBy(ThoughtData::getStage));

        // 2. 标签统计
        Map<String, Long> tagCounts = thoughts.stream()
                .flatMap(t -> t.getTags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        List<TagCount> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new TagCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // 3. 计算完成度
        int maxTotal = thoughts.stream()
                .mapToInt(ThoughtData::getTotalThoughts)
                .max()
                .orElse(0);

        double percentComplete = maxTotal > 0 ?
                (thoughts.size() * 100.0 / maxTotal) : 0;

        // 4. 时间线
        List<TimelineEntry> timeline = thoughts.stream()
                .sorted(Comparator.comparingInt(ThoughtData::getThoughtNumber))
                .map(t -> new TimelineEntry(t.getThoughtNumber(), t.getStage()))
                .collect(Collectors.toList());

        // 5. 检查是否包含所有阶段
        boolean allStagesPresent = Arrays.stream(ThoughtStage.values())
                .allMatch(stages::containsKey);

        // 6. 构建摘要
        Map<ThoughtStage, Integer> stageCounts = stages.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size()));

        return new ThoughtSummary(
                null,
                thoughts.size(),
                stageCounts,
                timeline,
                topTags,
                allStagesPresent,
                percentComplete
        );
    }

    public ThoughtAnalysis analyzeThought(ThoughtData thought, List<ThoughtData> allThoughts) {
        Objects.requireNonNull(thought, "thought cannot be null");
        Objects.requireNonNull(allThoughts, "allThoughts cannot be null");

        // 1. 查找相关思考
        List<ThoughtData> relatedThoughts = findRelatedThoughts(thought, allThoughts, 3);

        // 2. 检查是否是阶段中的第一个思考
        boolean isFirstInStage = allThoughts.stream()
                .filter(t -> t.getStage() == thought.getStage())
                .count() <= 1;

        // 3. 计算进度
        double progress = (thought.getThoughtNumber() * 100.0) /
                thought.getTotalThoughts();

        // 4. 构建相关思考摘要
        List<RelatedThoughtSummary> relatedSummaries = relatedThoughts.stream()
                .map(t -> new RelatedThoughtSummary(
                        t.getThoughtNumber(),
                        t.getStage(),
                        t.getThought().substring(0, Math.min(t.getThought().length(), 100))
                ))
                .collect(Collectors.toList());

        // 5. 返回分析结果
        return new ThoughtAnalysis(
                new CurrentThoughtInfo(
                        thought.getThoughtNumber(),
                        thought.getTotalThoughts(),
                        thought.isNextThoughtNeeded(),
                        thought.getStage(),
                        thought.getTags(),
                        thought.getTimestamp()
                ),
                new AnalysisResult(
                        relatedThoughts.size(),
                        relatedSummaries,
                        progress,
                        isFirstInStage
                ),
                new AnalysisContext(
                        allThoughts.size(),
                        thought.getStage()
                )
        );
    }
}