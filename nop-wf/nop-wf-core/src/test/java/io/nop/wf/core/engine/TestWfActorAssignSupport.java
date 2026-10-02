package io.nop.wf.core.engine;

import io.nop.api.core.auth.IUserDelegateService;
import io.nop.api.core.exceptions.NopException;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.IWfActorResolver;
import io.nop.wf.api.actor.WfUserActorBean;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.store.beans.WorkflowRecordBean;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_USER_ID;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_USER_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI5: WfActorAssignSupport 参与者分配支撑语义测试。
 * 委派判定透传 userDelegateService 语义；requireUser 对未知用户的错误路径。
 */
public class TestWfActorAssignSupport {
    private static final String SCOPE = "all";

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private static IWorkflowStepImplementor stepWithOwner(String ownerId) {
        WorkflowStepRecordBean stepRecord = new WorkflowStepRecordBean();
        stepRecord.setOwnerId(ownerId);
        WorkflowRecordBean wfRecord = new WorkflowRecordBean();
        wfRecord.setWorkScope(SCOPE);
        IWorkflowImplementor workflow = proxy(IWorkflowImplementor.class, (proxy, method, args) ->
                "getRecord".equals(method.getName()) ? wfRecord : null);
        return proxy(IWorkflowStepImplementor.class, (proxy, method, args) -> {
            if ("getRecord".equals(method.getName()))
                return stepRecord;
            if ("getWorkflow".equals(method.getName()))
                return workflow;
            return null;
        });
    }

    private WfActorAssignSupport newSupport(boolean canDelegate) {
        WfActorAssignSupport support = new WfActorAssignSupport();
        support.setUserDelegateService(new IUserDelegateService() {
            @Override
            public boolean canDelegate(String userId, String ownerId, String scope) {
                assertEquals(SCOPE, scope, "委派范围应来自工作流记录");
                return canDelegate;
            }

            @Override
            public Set<String> getDelegateOwnerIds(String userId, String scope) {
                return java.util.Collections.emptySet();
            }
        });
        support.setWfActorResolver(new IWfActorResolver() {
            @Override
            public IWfActor resolveUser(String userId) {
                // 模拟仅用户 u1 存在
                if (!"u1".equals(userId))
                    return null;
                WfUserActorBean user = new WfUserActorBean();
                user.setActorId(userId);
                user.setActorName(userId);
                return user;
            }

            @Override
            public IWfActor resolveActor(String actorType, String actorId, String deptId) {
                return resolveUser(actorId);
            }

            @Override
            public IWfActor getManager(IWfActor actor, int upLevel) {
                return null;
            }

            @Override
            public IWfActor getDeptManager(IWfActor actor, int upLevel) {
                return null;
            }
        });
        return support;
    }

    private static IWfRuntime runtime() {
        // newError 需要构造真实 NopException，其余方法无需实现
        return proxy(IWfRuntime.class, (proxy, method, args) -> {
            if ("newError".equals(method.getName()))
                return new NopException((io.nop.api.core.exceptions.ErrorCode) args[0]);
            return null;
        });
    }

    @Test
    public void testCanBeDelegatedByDelegatesToUserService() {
        IWorkflowStepImplementor step = stepWithOwner("owner1");

        assertTrue(newSupport(true).canBeDelegatedBy(step, "u1"));
        assertFalse(newSupport(false).canBeDelegatedBy(step, "u1"));
    }

    @Test
    public void testCannotDelegateWhenStepHasNoOwner() {
        // 步骤无 owner 时直接 false，不触发 delegate 服务
        IWorkflowStepImplementor step = stepWithOwner(null);
        assertFalse(newSupport(true).canBeDelegatedBy(step, "u1"));
    }

    @Test
    public void testResolveUserPassthrough() {
        IWfActor actor = newSupport(true).resolveUser("u1");
        assertNotNull(actor);
        assertEquals("u1", actor.getActorId());
        assertNull(newSupport(true).resolveUser("ghost"));
    }

    @Test
    public void testRequireUserThrowsForUnknownUser() {
        WfActorAssignSupport support = newSupport(true);
        NopException e = assertThrows(NopException.class,
                () -> support.requireUser("ghost", runtime()));
        assertEquals(ERR_WF_USER_NOT_EXISTS.getErrorCode(), e.getErrorCode());
        assertEquals("ghost", e.getParam(ARG_USER_ID));

        // 存在的用户直接返回
        IWfActor actor = support.requireUser("u1", runtime());
        assertEquals("u1", actor.getActorId());
    }

    @Test
    public void testManagerDelegation() {
        // resolver 未提供 manager 时透传返回 null
        IWfActor actor = newSupport(true).resolveUser("u1");
        assertNull(newSupport(true).getManager(actor, 1));
        assertNull(newSupport(true).getDeptManager(actor, 1));
    }
}
