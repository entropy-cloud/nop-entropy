package io.nop.batch.dsl.model;

import io.nop.batch.dsl.model._gen._BatchConsumerModel;

public class BatchConsumerModel extends _BatchConsumerModel implements Comparable<BatchConsumerModel> {
    public BatchConsumerModel() {

    }

    @Override
    public int compareTo(BatchConsumerModel o) {
        return Integer.compare(getOrder(), o.getOrder());
    }
}
