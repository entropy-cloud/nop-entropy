package io.nop.task.ext.dao;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.reflect.utils.BeanReflectHelper;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ext.TaskExtConstants;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;
import jakarta.inject.Inject;

public class TransactionTaskStepDecorator implements ITaskStepDecorator {

    private ITransactionTemplate transactionTemplate;

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel) {
        String txnGroup = ConvertHelper.toString(config.prop_get(TaskExtConstants.ATTR_TXN_TXN_GROUP));
        TransactionPropagation propagation = BeanReflectHelper.getValueByFactoryMethod(TransactionPropagation.class, config,
                TaskExtConstants.ATTR_TXN_PROPAGATION);
        return new TransactionalTaskStepWrapper(step, transactionTemplate, txnGroup, propagation, stepModel.isSync());
    }
}