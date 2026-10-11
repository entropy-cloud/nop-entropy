package io.nop.biz.decorator;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizTxnModel;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.reflect.impl.FunctionModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.10 / P1-2.6-001：mutation 默认事务装饰器（无 <txn> 元素时对齐 owner doc「默认有事务」契约）。
 */
public class TestTransactionActionDecorator {

    private BizActionModel mutationAction(BizTxnModel txn) {
        BizActionModel action = new BizActionModel();
        action.setName("test");
        action.setType("mutation");
        action.setTxn(txn);
        return action;
    }

    private List<IServiceActionDecorator> collect(BizActionModel action) {
        // collectDecorator 不解引用 transactionTemplate（仅装饰器构造存储引用），null 安全
        TransactionActionDecoratorCollector collector = new TransactionActionDecoratorCollector(null);
        List<IServiceActionDecorator> decorators = new ArrayList<>();
        collector.collectDecorator(action, decorators);
        return decorators;
    }

    @Test
    public void testMutationWithoutTxnGetsDefaultDecorator() {
        assertEquals(1, collect(mutationAction(null)).size(), "mutation 默认加事务装饰器");
    }

    @Test
    public void testQueryWithoutTxnGetsNoDecorator() {
        BizActionModel action = mutationAction(null);
        action.setType("query");
        assertEquals(0, collect(action).size(), "query 默认不加事务");
    }

    @Test
    public void testExplicitTxnFalseOptsOut() {
        BizTxnModel txn = new BizTxnModel();
        txn.setTransactional(false);
        assertEquals(0, collect(mutationAction(txn)).size(), "显式 transactional=false 关断");
    }

    @Test
    public void testExplicitTxnTrueKeepsDecorator() {
        BizTxnModel txn = new BizTxnModel();
        txn.setTransactional(true);
        assertEquals(1, collect(mutationAction(txn)).size(), "显式 transactional=true 保持装饰器");
    }
}
