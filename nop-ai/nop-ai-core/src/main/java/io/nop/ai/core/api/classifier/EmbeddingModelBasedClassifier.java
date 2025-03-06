package io.nop.ai.core.api.classifier;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.CosineSimilarity;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.embedding.RelevanceScore;
import io.nop.ai.core.api.support.VectorData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

// copy design from langchain4j
public class EmbeddingModelBasedClassifier implements ITextClassifier, IDocumentClassifier {
    private final IEmbeddingModel embeddingModel;
    private final Map<String, List<VectorData>> exampleEmbeddingsByLabel;
    private final int maxResults;
    private final double minScore;
    private final double meanToMaxScoreRatio;

    private final EmbeddingOptions embeddingOptions;

    public EmbeddingModelBasedClassifier(IEmbeddingModel embeddingModel, EmbeddingExampleConfig config) {
        this.embeddingModel = embeddingModel;
        this.exampleEmbeddingsByLabel = config.getExampleEmbeddings(embeddingModel);
        this.maxResults = config.getMaxResults();
        this.minScore = config.getMinScore();
        this.meanToMaxScoreRatio = config.getMeanToMaxScoreRatio();
        this.embeddingOptions = config.getEmbeddingOptions();
    }

    @Override
    public ClassificationResult classifyText(String text) {
        return classifyDocument(AiDocument.fromText(text));
    }

    @Override
    public ClassificationResult classifyDocument(AiDocument document) {
        VectorData textEmbedding = embeddingModel.embed(document, embeddingOptions);

        List<ScoredLabel> scoredLabels = new ArrayList<>();
        exampleEmbeddingsByLabel.forEach((label, exampleEmbeddings) -> {

            double meanScore = 0;
            double maxScore = 0;
            for (VectorData exampleEmbedding : exampleEmbeddings) {
                double score = calcRelevanceScore(textEmbedding, exampleEmbedding);
                meanScore += score;
                maxScore = Math.max(score, maxScore);
            }
            meanScore /= exampleEmbeddings.size();

            double aggregateScore = aggregatedScore(meanScore, maxScore);
            if (aggregateScore >= minScore) {
                scoredLabels.add(new ScoredLabel(label, aggregateScore));
            }
        });

        return new ClassificationResult(
                scoredLabels.stream()
                        // sorting in descending order to return highest score first
                        .sorted(comparingDouble(classificationResult -> 1 - classificationResult.getScore()))
                        .limit(maxResults)
                        .collect(toList())
        );
    }

    protected double calcRelevanceScore(VectorData d1, VectorData d2) {
        double cosineSimilarity = CosineSimilarity.between(d1, d2);
        double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);
        return score;
    }

    private double aggregatedScore(double meanScore, double maxScore) {
        return (meanToMaxScoreRatio * meanScore) + ((1 - meanToMaxScoreRatio) * maxScore);
    }
}