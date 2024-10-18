/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.ai.core.api.vectorstore;

import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@DataBean
public class VectorQueryBean extends VectorData {

    /**
     * the default value of search data count
     */
    public static final int DEFAULT_MAX_RESULTS = 4;

    /**
     * search text, Vector store will convert the text to vector data
     */
    private String text;

    /**
     * search max result, like the sql "limit" in mysql
     */
    private Integer maxResults = DEFAULT_MAX_RESULTS;

    /**
     * The lowest correlation score, ranging from 0 to 1 (including 0 and 1). Only embeddings with a score of this value or higher will be returned.
     * 0.0 indicates accepting any similarity or disabling similarity threshold filtering. A threshold of 1.0 indicates the need for a perfect match.
     */
    private Double minScore;

    /**
     * The flag of include vector data queries. If the current value is true and the vector content is null,
     * the query text will be automatically converted into vector data through the vector store.
     */
    private boolean withVector = true;

    /**
     * query condition
     */
    private TreeBean condition;

    /**
     * query fields
     */
    private List<String> outputFields;

    /**
     * whether to output vector data
     */
    private boolean outputVector = false;


    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public VectorQueryBean text(String text) {
        setText(text);
        return this;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public VectorQueryBean maxResults(Integer maxResults) {
        setMaxResults(maxResults);
        return this;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public VectorQueryBean minScore(Double minScore) {
        setMinScore(minScore);
        return this;
    }

    public boolean isWithVector() {
        return withVector;
    }

    public void setWithVector(boolean withVector) {
        this.withVector = withVector;
    }

    public VectorQueryBean withVector(Boolean withVector) {
        setWithVector(withVector);
        return this;
    }

    public TreeBean getCondition() {
        return condition;
    }

    public void setCondition(TreeBean condition) {
        this.condition = condition;
    }

    public List<String> getOutputFields() {
        return outputFields;
    }

    public void setOutputFields(List<String> outputFields) {
        this.outputFields = outputFields;
    }

    public VectorQueryBean outputFields(Collection<String> outputFields) {
        setOutputFields(new ArrayList<>(outputFields));
        return this;
    }

    public VectorQueryBean outputFields(String... outputFields) {
        setOutputFields(Arrays.asList(outputFields));
        return this;
    }

    public boolean isOutputVector() {
        return outputVector;
    }

    public void setOutputVector(boolean outputVector) {
        this.outputVector = outputVector;
    }

    public VectorQueryBean outputVector(boolean outputVector) {
        setOutputVector(outputVector);
        return this;
    }
}