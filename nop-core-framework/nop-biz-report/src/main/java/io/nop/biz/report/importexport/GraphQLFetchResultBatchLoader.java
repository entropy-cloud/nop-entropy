package io.nop.biz.report.importexport;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.engine.IGraphQLEngine;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class GraphQLFetchResultBatchLoader implements IBatchLoaderProvider.IBatchLoader<Object> {
    private final IBatchLoaderProvider.IBatchLoader<?> loader;
    private final IGraphQLEngine graphQLEngine;
    private final String resultType;
    private final FieldSelectionBean selection;

    public GraphQLFetchResultBatchLoader(IBatchLoaderProvider.IBatchLoader<?> loader, IGraphQLEngine graphQLEngine,
                                         String resultType, FieldSelectionBean selection) {
        this.loader = loader;
        this.graphQLEngine = graphQLEngine;
        this.resultType = resultType;
        this.selection = selection;
    }

    @Override
    public List<Object> load(int batchSize, IBatchChunkContext context) {
        return FutureHelper.syncGet(loadAsync(batchSize, context));
    }

    @Override
    public CompletionStage<List<Object>> loadAsync(int batchSize, IBatchChunkContext context) {
        return loader.loadAsync(batchSize, context).thenCompose(list -> {
            CompletionStage<List<Object>> future = (CompletionStage) graphQLEngine
                    .fetchResultWithSelection(list, resultType, selection, IServiceContext.fromEvalContext(context));
            return future;
        });
    }
}