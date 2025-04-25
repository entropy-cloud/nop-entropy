package io.nop.biz.report.importexport;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;

import java.util.concurrent.Executor;

public abstract class AbstractBizEntityService {
    protected IGraphQLEngine graphQLEngine;
    protected IDaoProvider daoProvider;
    protected IBizObjectManager bizObjManager;
    protected String executorBean;
    protected int maxConcurrency = 1;
    protected int maxBatchSize = 1000;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setBizObjManager(IBizObjectManager bizObjManager) {
        this.bizObjManager = bizObjManager;
    }

    public void setExecutorBean(String executorBean) {
        this.executorBean = executorBean;
    }

    @InjectValue("@cfg:nop.biz.report.export.max-concurrency|1")
    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    @InjectValue("@cfg:nop.biz.report.export.max-batch-size|1000")
    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }


    protected Executor getExecutor(IBizEntityExecutorConfig config) {
        if (executorBean == null) {
            if (config.getConcurrency() > 1)
                return GlobalExecutors.globalWorker();
            return GlobalExecutors.syncExecutor();
        }
        if (BeanContainer.instance().containsBean(executorBean))
            return (Executor) BeanContainer.instance().getBean(executorBean);
        return GlobalExecutors.requireExecutor(executorBean);
    }
}
