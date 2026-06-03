# Dimension 19: Naming & Terminology Consistency — nop-stream

## 第 1 轮（初审）

### [维度19-01] CheckpointListener misplaced in common.state package

- **File**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/CheckpointListener.java:19`
- **Severity**: P2
- **Current state**: CheckpointListener lives in common.state but is conceptually a checkpoint concern. Should be in io.nop.stream.core.checkpoint package.
- **Risk**: Users looking for checkpoint APIs won't find it in the state package.
- **Recommendation**: Move to io.nop.stream.core.checkpoint package; add forwarding stub with @Deprecated.
- **Confidence**: Very likely
- **False positive exclusion**: Flink-origin placement preserved during porting; Nop project has cleaner checkpoint package.
- **Review status**: Unreviewed

### [维度19-02] Two overlapping checkpoint interfaces with unclear boundaries

- **File**: CheckpointListener (notifications) vs CheckpointParticipant (full lifecycle)
- **Severity**: P2
- **Current state**: Two unrelated checkpoint interfaces with no inheritance relationship and no cross-references in Javadoc.
- **Recommendation**: Add Javadoc cross-references; consider if CheckpointParticipant should extend CheckpointListener.
- **Confidence**: Very likely
- **False positive exclusion**: Both deal with checkpoint integration but have completely different contracts.
- **Review status**: Unreviewed

### Positive Finding

Error code prefixes are consistent: ERR_STREAM_* (47 codes) and ERR_CEP_* (9 codes) properly match module boundaries.
