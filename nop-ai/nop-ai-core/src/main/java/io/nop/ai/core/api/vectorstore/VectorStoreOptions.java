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

import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.support.Metadata;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Store Options, Each store can have its own Options implementation.
 */
@DataBean
public class VectorStoreOptions extends Metadata {

    /**
     * store collection name
     */
    private String collectionName;

    /**
     * store index name
     */
    private String indexName;

    /**
     * store partition name
     */
    private List<String> partitionNames;

    /**
     * store embedding options
     */
    private EmbeddingOptions embeddingOptions;


    public String getCollectionName() {
        return collectionName;
    }

    public String getCollectionNameOrDefault(String other) {
        return StringHelper.hasText(collectionName) ? collectionName : other;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<String> getPartitionNames() {
        return partitionNames;
    }

    public String getPartitionName() {
        return partitionNames != null && !partitionNames.isEmpty() ? partitionNames.get(0) : null;
    }

    public List<String> getPartitionNamesOrEmpty() {
        return partitionNames == null ? Collections.emptyList() : partitionNames;
    }

    public void setPartitionNames(List<String> partitionNames) {
        this.partitionNames = partitionNames;
    }

    public VectorStoreOptions partitionName(String partitionName) {
        if (this.partitionNames == null) {
            this.partitionNames = new ArrayList<>(1);
        }
        this.partitionNames.add(partitionName);
        return this;
    }


    public EmbeddingOptions getEmbeddingOptions() {
        return embeddingOptions;
    }

    public void setEmbeddingOptions(EmbeddingOptions embeddingOptions) {
        this.embeddingOptions = embeddingOptions;
    }


    public static VectorStoreOptions ofCollectionName(String collectionName) {
        VectorStoreOptions storeOptions = new VectorStoreOptions();
        storeOptions.setCollectionName(collectionName);
        return storeOptions;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getIndexNameOrDefault(String other) {
        return StringHelper.hasText(indexName) ? indexName : other;
    }
}
