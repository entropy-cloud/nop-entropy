package io.nop.core.context;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestTccContext {

    @Test
    public void testBuildFromRequestWithoutTxnIdReturnsNull() {
        ApiRequest<Object> request = new ApiRequest<>();
        assertNull(TccContext.buildFromRequest(request), "no txn header should yield null TccContext");
    }

    @Test
    public void testBuildFromRequestReadsAllTxnHeaders() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setHeader(ApiConstants.HEADER_TXN_ID, "txn-1");
        request.setHeader(ApiConstants.HEADER_TXN_GROUP, "group-1");
        request.setHeader(ApiConstants.HEADER_TXN_BRANCH_ID, "branch-1");
        request.setHeader(ApiConstants.HEADER_TXN_BRANCH_NO, "3");

        TccContext ctx = TccContext.buildFromRequest(request);
        assertEquals("txn-1", ctx.getTxnId());
        assertEquals("group-1", ctx.getTxnGroup());
        assertEquals("branch-1", ctx.getBranchId());
        assertEquals(3, ctx.getBranchNo());
    }

    @Test
    public void testBuildFromServiceContextReadsRequestHeaders() {
        ServiceContextImpl svcCtx = new ServiceContextImpl();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ApiConstants.HEADER_TXN_ID, "txn-2");
        headers.put(ApiConstants.HEADER_TXN_GROUP, "group-2");
        headers.put(ApiConstants.HEADER_TXN_BRANCH_ID, "branch-2");
        headers.put(ApiConstants.HEADER_TXN_BRANCH_NO, 5);
        svcCtx.setRequestHeaders(headers);

        TccContext ctx = TccContext.buildFromServiceContext(svcCtx);
        assertEquals("txn-2", ctx.getTxnId());
        assertEquals("group-2", ctx.getTxnGroup());
        assertEquals("branch-2", ctx.getBranchId());
        assertEquals(5, ctx.getBranchNo());

        // 无 txn header 时返回 null
        svcCtx.setRequestHeaders(new HashMap<>());
        assertNull(TccContext.buildFromServiceContext(svcCtx));
    }

    @Test
    public void testSettersRoundTrip() {
        TccContext ctx = new TccContext();
        ctx.setTxnId("t");
        ctx.setTxnGroup("g");
        ctx.setBranchId("b");
        ctx.setBranchNo(7);
        assertEquals("t", ctx.getTxnId());
        assertEquals("g", ctx.getTxnGroup());
        assertEquals("b", ctx.getBranchId());
        assertEquals(7, ctx.getBranchNo());
    }
}
