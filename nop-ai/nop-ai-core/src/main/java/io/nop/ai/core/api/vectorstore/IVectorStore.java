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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Vector Store
 *
 * @param <T> The Vector Data
 */
public abstract class IVectorStore<T extends VectorData> {


    /**
     * Store Vector Data With Options
     *
     * @param vectorData The Vector Data
     * @param options    Store Options
     * @return Store Result
     */
    public VectorStoreResult store(T vectorData, VectorStoreOptions options) {
        return store(Collections.singletonList(vectorData), options);
    }

    /**
     * Store Vector Data list wit options
     *
     * @param vectorDataList vector data list
     * @param options        options
     * @return store result
     */
    public abstract VectorStoreResult store(List<T> vectorDataList, VectorStoreOptions options);


    /**
     * delete store data by ids with options
     *
     * @param ids     ids
     * @param options store options
     * @return store result
     */
    public abstract VectorStoreResult delete(Collection<Object> ids, VectorStoreOptions options);

    /**
     * update the vector data by id with options
     *
     * @param vectorData vector data
     * @param options    store options
     * @return store result
     */
    public VectorStoreResult update(T vectorData, VectorStoreOptions options) {
        return update(Collections.singletonList(vectorData), options);
    }

    /**
     * update store data list with options
     *
     * @param vectorDataList vector data list
     * @param options        store options
     * @return store result
     */
    public abstract VectorStoreResult update(List<T> vectorDataList, VectorStoreOptions options);

    /**
     * search vector data by SearchWrapper with options
     *
     * @param wrapper SearchWrapper
     * @param options Store Options
     * @return the vector data list
     */
    public abstract List<T> search(VectorQueryBean wrapper, VectorStoreOptions options);
}
