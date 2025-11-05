/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.interceptor;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.OrmConstants;
import io.nop.xlang.api.XLang;

import java.util.List;
import java.util.Map;

public class XplOrmInterceptor implements IOrmInterceptor {
    private Map<String, List<IEvalAction>> preSaveActions;
    private Map<String, List<IEvalAction>> preUpdateActions;
    private Map<String, List<IEvalAction>> preDeleteActions;
    private Map<String, List<IEvalAction>> preResetActions;
    private Map<String, List<IEvalAction>> postSaveActions;
    private Map<String, List<IEvalAction>> postUpdateActions;
    private Map<String, List<IEvalAction>> postDeleteActions;
    private Map<String, List<IEvalAction>> postLoadActions;

    private List<IEvalAction> preFlushActions;
    private List<IEvalAction> postFlushActions;

    public boolean isEmpty() {
        return !hasActions(preSaveActions) &&
                !hasActions(preUpdateActions) &&
                !hasActions(preDeleteActions) &&
                !hasActions(preResetActions) &&
                !hasActions(postSaveActions) &&
                !hasActions(postUpdateActions) &&
                !hasActions(postDeleteActions) &&
                !hasActions(postLoadActions) &&
                !hasActions(preFlushActions) &&
                !hasActions(postFlushActions);
    }

    private boolean hasActions(Map<String, List<IEvalAction>> actions) {
        return actions != null && !actions.isEmpty();
    }

    private boolean hasActions(List<IEvalAction> actions) {
        return actions != null && !actions.isEmpty();
    }

    public void setActions(String event, Map<String, List<IEvalAction>> actions) {
        if (OrmConstants.PRE_SAVE.equals(event)) {
            setPreSaveActions(actions);
        } else if (OrmConstants.PRE_UPDATE.equals(event)) {
            setPreUpdateActions(actions);
        } else if (OrmConstants.PRE_DELETE.equals(event)) {
            setPreDeleteActions(actions);
        } else if (OrmConstants.PRE_RESET.equals(event)) {
            setPreResetActions(actions);
        } else if (OrmConstants.POST_SAVE.equals(event)) {
            setPostSaveActions(actions);
        } else if (OrmConstants.POST_UPDATE.equals(event)) {
            setPostUpdateActions(actions);
        } else if (OrmConstants.POST_DELETE.equals(event)) {
            setPostDeleteActions(actions);
        } else if (OrmConstants.POST_LOAD.equals(event)) {
            setPostLoadActions(actions);
        } else {
            throw new IllegalArgumentException("nop.err.orm.unsupported-event:" + event);
        }
    }

    public void setPreSaveActions(Map<String, List<IEvalAction>> preSaveActions) {
        this.preSaveActions = preSaveActions;
    }

    public void setPreUpdateActions(Map<String, List<IEvalAction>> preUpdateAction) {
        this.preUpdateActions = preUpdateAction;
    }

    public void setPreDeleteActions(Map<String, List<IEvalAction>> preDeleteActions) {
        this.preDeleteActions = preDeleteActions;
    }

    public void setPreResetActions(Map<String, List<IEvalAction>> preResetActions) {
        this.preResetActions = preResetActions;
    }

    public void setPostSaveActions(Map<String, List<IEvalAction>> postSaveActions) {
        this.postSaveActions = postSaveActions;
    }

    public void setPostUpdateActions(Map<String, List<IEvalAction>> postUpdateActions) {
        this.postUpdateActions = postUpdateActions;
    }

    public void setPostDeleteActions(Map<String, List<IEvalAction>> postDeleteActions) {
        this.postDeleteActions = postDeleteActions;
    }

    public void setPostLoadActions(Map<String, List<IEvalAction>> postLoadActions) {
        this.postLoadActions = postLoadActions;
    }

    public void setPreFlushActions(List<IEvalAction> preFlushActions) {
        this.preFlushActions = preFlushActions;
    }

    IEvalScope newScope(IOrmEntity entity) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, OrmConstants.VAR_ENTITY, entity);
        return scope;
    }

    List<IEvalAction> getActions(Map<String, List<IEvalAction>> map, IOrmEntity entity) {
        if (map == null || map.isEmpty())
            return null;
        return map.get(entity.orm_entityName());
    }

    ProcessResult invokeActions(List<IEvalAction> actions, IOrmEntity entity) {
        if (actions == null || actions.isEmpty())
            return ProcessResult.CONTINUE;

        IEvalScope scope = newScope(entity);
        for (IEvalAction action : actions) {
            Object ret = action.invoke(scope);
            if (ret instanceof ProcessResult) {
                ProcessResult result = (ProcessResult) ret;
                if (result != ProcessResult.CONTINUE)
                    return result;
            }
        }

        return ProcessResult.CONTINUE;
    }

    void runActions(List<IEvalAction> actions, IOrmEntity entity) {
        if (actions == null || actions.isEmpty())
            return;

        IEvalScope scope = newScope(entity);
        for (IEvalAction action : actions) {
            action.invoke(scope);
        }
    }

    @Override
    public ProcessResult preSave(IOrmEntity entity) {
        return invokeActions(getActions(preSaveActions, entity), entity);
    }

    @Override
    public ProcessResult preUpdate(IOrmEntity entity) {
        return invokeActions(getActions(preUpdateActions, entity), entity);
    }

    @Override
    public ProcessResult preDelete(IOrmEntity entity) {
        return invokeActions(getActions(preDeleteActions, entity), entity);
    }

    @Override
    public void postReset(IOrmEntity entity) {
        runActions(getActions(preResetActions, entity), entity);
    }

    @Override
    public void postSave(IOrmEntity entity) {
        runActions(getActions(postSaveActions, entity), entity);
    }

    @Override
    public void postUpdate(IOrmEntity entity) {
        runActions(getActions(postUpdateActions, entity), entity);
    }

    @Override
    public void postDelete(IOrmEntity entity) {
        runActions(getActions(postDeleteActions, entity), entity);
    }

    @Override
    public void postLoad(IOrmEntity entity) {
        invokeActions(getActions(postLoadActions, entity), entity);
    }

    @Override
    public void preFlush() {
        if (preFlushActions == null || preFlushActions.isEmpty())
            return;

        IEvalScope scope = XLang.newEvalScope();

        for (IEvalAction action : preFlushActions) {
            action.invoke(scope);
        }
    }

    @Override
    public void postFlush(Throwable exception) {
        if (postFlushActions == null || postFlushActions.isEmpty())
            return;

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, OrmConstants.VAR_EXCEPTION, exception);
        for (IEvalAction action : postFlushActions) {
            action.invoke(scope);
        }
    }
}