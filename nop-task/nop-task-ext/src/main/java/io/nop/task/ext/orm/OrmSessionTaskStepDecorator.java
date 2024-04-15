package io.nop.task.ext.orm;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.orm.IOrmTemplate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ext.TaskExtConstants;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;
import jakarta.inject.Inject;

public class OrmSessionTaskStepDecorator implements ITaskStepDecorator {

    private IOrmTemplate ormTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Override
    public ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel) {
        boolean newSession = ConvertHelper.toPrimitiveBoolean(config.prop_get(TaskExtConstants.ATTR_ORM_NEW_SESSION));
        return new OrmSessionTaskStepWrapper(step, ormTemplate, newSession, stepModel.isSync());
    }
}