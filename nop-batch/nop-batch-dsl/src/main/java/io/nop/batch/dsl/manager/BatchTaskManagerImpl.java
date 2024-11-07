package io.nop.batch.dsl.manager;

import io.nop.api.core.ioc.IBeanProvider;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTaskBuilder;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.core.manager.IBatchTaskManager;
import io.nop.batch.dsl.BatchDslConstants;
import io.nop.batch.dsl.model.BatchTaskModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

public class BatchTaskManagerImpl implements IBatchTaskManager {

    private IOrmTemplate ormTemplate;

    private IJdbcTemplate jdbcTemplate;

    private IDaoProvider daoProvider;

    private IBatchStateStore stateStore;

    private ITransactionTemplate transactionTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setStateStore(@Nullable IBatchStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public IBatchTaskContext newBatchTaskContext(IServiceContext svcCtx, IEvalScope scope) {
        return new BatchTaskContextImpl(svcCtx, scope);
    }

    @Override
    public IBatchTaskBuilder newBatchTaskBuilder(String batchTaskName, Long batchTaskVersion, IBeanProvider beanProvider) {
        BatchTaskModel taskModel = loadBatchTaskModel(batchTaskName, batchTaskVersion);
        return new ModelBasedBatchTaskBuilderFactory(batchTaskName, taskModel, stateStore, transactionTemplate,
                ormTemplate, jdbcTemplate, daoProvider).newTaskBuilder(beanProvider);
    }

    @Override
    public IBatchTaskBuilder newBatchTaskBuilderFromModel(String batchTaskName, XNode node, IBeanProvider beanProvider) {
        BatchTaskModel taskModel = (BatchTaskModel) new DslModelParser().parseFromNode(node);
        return new ModelBasedBatchTaskBuilderFactory(batchTaskName, taskModel, stateStore, transactionTemplate,
                ormTemplate, jdbcTemplate, daoProvider).newTaskBuilder(beanProvider);
    }

    public BatchTaskModel loadBatchTaskModel(String batchTaskName, Long taskVersion) {
        String taskPath = ResourceVersionHelper.buildPath(BatchDslConstants.FILE_DIR_BATCH_TASK,
                batchTaskName, taskVersion, BatchDslConstants.FILE_TYPE_BATCH_TASK);
        return (BatchTaskModel) ResourceComponentManager.instance().loadComponentModel(taskPath);
    }
}
