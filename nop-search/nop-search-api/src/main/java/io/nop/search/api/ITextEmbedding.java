/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search.api;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Minimal interface for computing text embeddings (vectors).
 * Implementations can use any embedding model (OpenAI, local models, etc.)
 * without introducing dependencies on specific AI frameworks.
 * 
 * <p>This interface is intentionally minimal to keep nop-search independent
 * from nop-ai and other heavy dependencies.</p>
 */
public interface ITextEmbedding {
    
    /**
     * Compute embedding vector for a single text.
     * 
     * @param text the text to embed
     * @return embedding vector as float array, or null if embedding cannot be computed
     */
    float[] embed(@Name("text") String text);
    
    /**
     * Compute embedding vectors for multiple texts (batch operation).
     * Implementations can optimize batch processing for better performance.
     * 
     * @param texts list of texts to embed
     * @return list of embedding vectors (same order as input), null entries allowed if embedding fails
     */
    List<float[]> embedAll(@Name("texts") List<String> texts);
    
    /**
     * Async version of embed().
     * Default implementation wraps sync call.
     */
    default CompletionStage<float[]> embedAsync(@Name("text") String text) {
        return FutureHelper.futureCall(() -> embed(text));
    }
    
    /**
     * Async version of embedAll().
     * Default implementation wraps sync call.
     */
    default CompletionStage<List<float[]>> embedAllAsync(@Name("texts") List<String> texts) {
        return FutureHelper.futureCall(() -> embedAll(texts));
    }
    
    /**
     * Get the dimension of embedding vectors produced by this model.
     * 
     * @return vector dimension (e.g., 768, 1536), or -1 if unknown
     */
    default int getDimension() {
        return -1;
    }
}
