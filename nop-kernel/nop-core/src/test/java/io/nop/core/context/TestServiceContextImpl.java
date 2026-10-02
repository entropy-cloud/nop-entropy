package io.nop.core.context;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.ITreeBean;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServiceContextImpl {

    @Test
    public void testRequestResponseAttributes() {
        ServiceContextImpl ctx = new ServiceContextImpl();
        assertNull(ctx.getRequest());

        Object request = new Object();
        ctx.setRequest(request);
        assertSame(request, ctx.getRequest());

        Object response = new Object();
        ctx.setResponse(response);
        assertSame(response, ctx.getResponse());
    }

    @Test
    public void testHeaders() {
        ServiceContextImpl ctx = new ServiceContextImpl();
        assertNull(ctx.getRequestHeaders());

        ctx.setRequestHeader("x-a", "1");
        assertEquals("1", ctx.getRequestHeader("x-a"));
        assertNotNull(ctx.getRequestHeaders(), "setRequestHeader should lazily create headers map");

        ctx.setResponseHeader("x-b", "2");
        assertEquals("2", ctx.getResponseHeader("x-b"));
        ctx.clearResponseHeaders();
        assertNull(ctx.getResponseHeader("x-b"));
    }

    @Test
    public void testCacheLazilyCreatedAndReusable() {
        ServiceContextImpl ctx = new ServiceContextImpl();
        assertNotNull(ctx.getCache(), "getCache should lazily create a cache");
        ctx.getCache().put("k", "v");
        assertSame(ctx.getCache().get("k"), "v");
    }

    @Test
    public void testNewChildContextInheritsCheckers() {
        ServiceContextImpl parent = new ServiceContextImpl();

        IActionAuthChecker actionChecker = (permission, context) -> true;
        IDataAuthChecker dataChecker = new IDataAuthChecker() {
            @Override
            public boolean isPermitted(String bizObj, String action, Object entity, ISecurityContext context) {
                return true;
            }

            @Override
            public ITreeBean getFilter(String bizObj, String action, ISecurityContext context) {
                return null;
            }
        };

        parent.setActionAuthChecker(actionChecker);
        parent.setDataAuthChecker(dataChecker);

        ServiceContextImpl child = (ServiceContextImpl) parent.newChildContext();
        assertSame(parent, child.getParentContext());
        assertSame(actionChecker, child.getActionAuthChecker());
        assertSame(dataChecker, child.getDataAuthChecker());

        // 改动父上下文的 checker 不影响已创建的子上下文
        parent.setActionAuthChecker(null);
        assertSame(actionChecker, child.getActionAuthChecker());
    }

    @Test
    public void testEvalScopeExposesSvcCtxVariable() {
        ServiceContextImpl ctx = new ServiceContextImpl();
        // 构造时把自身注册为 eval scope 的 svcCtx 变量
        Object var = ctx.getEvalScope().getLocalValue(io.nop.core.CoreConstants.VAR_SVC_CTX);
        assertSame(ctx, var);
    }

    @Test
    public void testConstructorWithVars() {
        Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("k", 1);
        ServiceContextImpl ctx = new ServiceContextImpl(vars);
        assertEquals(1, ctx.getEvalScope().getLocalValue("k"));
    }
}
