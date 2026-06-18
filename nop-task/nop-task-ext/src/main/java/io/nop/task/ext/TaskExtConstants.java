package io.nop.task.ext;

public interface TaskExtConstants {
    String ATTR_TXN_TXN_GROUP = "txn:txnGroup";

    String ATTR_TXN_PROPAGATION = "txn:propagation";

    String ATTR_ORM_NEW_SESSION = "orm:newSession";

    String ATTR_RETRY_MAX_RETRY_COUNT = "retry:maxRetryCount";

    String ATTR_RETRY_RETRY_DELAY = "retry:retryDelay";

    String ATTR_RETRY_MAX_RETRY_DELAY = "retry:maxRetryDelay";

    String ATTR_RETRY_EXPONENTIAL_DELAY = "retry:exponentialDelay";

    String ATTR_TIMEOUT_TIMEOUT = "timeout:timeout";

    String ATTR_RATE_LIMIT_REQUEST_PER_SECOND = "rateLimit:requestPerSecond";

    String ATTR_RATE_LIMIT_GLOBAL = "rateLimit:global";

    String ATTR_RATE_LIMIT_MAX_WAIT = "rateLimit:maxWait";
}
