package io.nop.orm.metrics;

public class EmptyOrmMetricsImpl implements IOrmMetrics {
    @Override
    public void onSessionOpen() {

    }

    @Override
    public void onSessionClosed() {

    }

    @Override
    public void onFlush() {

    }

    @Override
    public void onLoadEntity(String entityName) {

    }

    @Override
    public void onFlushDeleteEntity(String entityName) {

    }

    @Override
    public void onFlushUpdateEntity(String entityName) {

    }

    @Override
    public void onFlushSaveEntity(String entityName) {

    }
}
