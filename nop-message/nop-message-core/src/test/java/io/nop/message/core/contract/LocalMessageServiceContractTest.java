package io.nop.message.core.contract;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;

/**
 * {@link LocalMessageService} as the first implementer of the broker-neutral message
 * contract (roadmap WI4). Local is a synchronous in-JVM broadcast bus: it does not
 * declare batch consumption (batchReceiveCount is warn-once per D1), and
 * {@code clearConsumers()} maps to the close hook.
 */
public class LocalMessageServiceContractTest extends AbstractMessageServiceContractTest {

    @Override
    protected IMessageService createService() {
        return new LocalMessageService();
    }

    @Override
    protected void closeService() {
        ((LocalMessageService) service).clearConsumers();
    }

    @Override
    protected boolean supportsBatchConsume() {
        return false;
    }

    @Override
    protected boolean supportsSuspendResume() {
        return true;
    }
}
