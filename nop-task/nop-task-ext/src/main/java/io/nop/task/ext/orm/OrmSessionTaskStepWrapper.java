package io.nop.task.ext.orm;

import io.nop.orm.IOrmTemplate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.DelegateTaskStep;

public class OrmSessionTaskStepWrapper extends DelegateTaskStep {
    private final IOrmTemplate ormTemplate;
    private final boolean newSession;
    private final boolean sync;

    public OrmSessionTaskStepWrapper(ITaskStep taskStep, IOrmTemplate ormTemplate, boolean newSession, boolean sync) {
        super(taskStep);
        this.ormTemplate = ormTemplate;
        this.newSession = newSession;
        this.sync = sync;
    }

    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        if (sync) {
            if (newSession) {
                return ormTemplate.runInNewSession(session -> {
                    return getTaskStep().execute(stepRt).sync();
                });
            } else {
                return ormTemplate.runInSession(session -> {
                    return getTaskStep().execute(stepRt).sync();
                });
            }
        }

        if (newSession) {
            return TaskStepReturn.ASYNC(null, ormTemplate.runInNewSessionAsync(
                    session -> {
                        return getTaskStep().execute(stepRt).getReturnPromise();
                    }));
        } else {
            return TaskStepReturn.ASYNC(null, ormTemplate.runInSessionAsync(
                    session -> {
                        return getTaskStep().execute(stepRt).getReturnPromise();
                    }));
        }
    }
}
