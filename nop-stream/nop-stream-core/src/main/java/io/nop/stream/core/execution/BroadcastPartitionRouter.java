package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamRecord;

class BroadcastPartitionRouter implements PartitionRouter {

    private final int numPartitions;

    BroadcastPartitionRouter(int numPartitions) {
        this.numPartitions = numPartitions;
    }

    @Override
    public int selectChannel(StreamRecord<?> record) {
        return 0;
    }

    @Override
    public int getNumberOfPartitions() {
        return numPartitions;
    }
}
