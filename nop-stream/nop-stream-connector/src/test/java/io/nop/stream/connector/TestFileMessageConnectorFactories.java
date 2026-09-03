/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.connector.file.FileSource;
import io.nop.stream.connector.file.FileSourceConnectorFactory;
import io.nop.stream.connector.file.FileTwoPhaseCommitSink;
import io.nop.stream.connector.file.FileTwoPhaseCommitSinkConnectorFactory;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorParallelism;
import io.nop.stream.core.connector.registry.ConnectorRecoverySemantic;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED;

/**
 * Factory + capability alignment tests for the file and message connector families
 * (item 19 / P-REQ-28). Single fact source: the declared descriptor consistency must equal
 * the constructed endpoint's instance-level declaration.
 */
public class TestFileMessageConnectorFactories {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // file source (FLIP-27 split source)
    // ------------------------------------------------------------------

    @Test
    public void testFileSourceFactoryConstructsEndpoint() {
        FileSourceConnectorFactory factory = new FileSourceConnectorFactory();
        FileSource source = assertInstanceOf(FileSource.class, factory.createSource(
                new StreamConnectorConfig("file", Map.of("directoryPath", tempDir.toString()))));
        assertEquals(tempDir.toString(), source.getDirectoryPath());
    }

    @Test
    public void testFileSourceFactoryRequiresDirectoryPath() {
        FileSourceConnectorFactory factory = new FileSourceConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSource(new StreamConnectorConfig("file")));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("directoryPath", ex.getParam("paramName"));
        assertEquals("file", ex.getParam("typeName"));
    }

    @Test
    public void testFileSourceDescriptorMatchesCapabilityMatrix() {
        ConnectorCapabilityDescriptor d = new FileSourceConnectorFactory().describeCapabilities();
        assertEquals("file", d.getTypeName());
        assertEquals(ConnectorParallelism.PARALLEL, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.SPLIT_CURSOR_CHECKPOINT, d.getRecoverySemantic());
        assertEquals(SourceConsistencyCapability.AT_LEAST_ONCE, d.getSourceConsistency());
    }

    // ------------------------------------------------------------------
    // file sink (2PC)
    // ------------------------------------------------------------------

    @Test
    public void testFileSinkFactoryConstructsEndpointWithSingleFactSourceAlignment() {
        FileTwoPhaseCommitSinkConnectorFactory factory = new FileTwoPhaseCommitSinkConnectorFactory();
        FileTwoPhaseCommitSink<?> sink = assertInstanceOf(FileTwoPhaseCommitSink.class,
                factory.createSink(new StreamConnectorConfig("file",
                        Map.of("outputDir", tempDir.toString()))));
        assertInstanceOf(TwoPhaseCommitSinkFunction.class, sink);
        assertEquals(SinkConsistencyCapability.TWO_PHASE_COMMIT, sink.getSinkConsistency());

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals(d.getSinkConsistency(), sink.getSinkConsistency());
        assertEquals(ConnectorParallelism.PLANNING_GATE_PARALLELISM_1, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.TWO_PHASE_PENDING_COMMITS, d.getRecoverySemantic());
    }

    @Test
    public void testFileSinkFactoryCharsetOptional() {
        FileTwoPhaseCommitSinkConnectorFactory factory = new FileTwoPhaseCommitSinkConnectorFactory();
        // ISO-8859-1 must be accepted through the optional charset param (constructor path)
        assertInstanceOf(FileTwoPhaseCommitSink.class, factory.createSink(
                new StreamConnectorConfig("file",
                        Map.of("outputDir", tempDir.toString(), "charset", "ISO-8859-1"))));
    }

    @Test
    public void testFileSinkFactoryRequiresOutputDir() {
        FileTwoPhaseCommitSinkConnectorFactory factory = new FileTwoPhaseCommitSinkConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSink(new StreamConnectorConfig("file", Map.of("charset", "UTF-8"))));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("outputDir", ex.getParam("paramName"));
    }

    // ------------------------------------------------------------------
    // message source
    // ------------------------------------------------------------------

    @Test
    public void testMessageSourceFactoryConstructsEndpointWithAlignment() {
        MessageSourceConnectorFactory factory = new MessageSourceConnectorFactory();
        IMessageService service = new LocalMessageService();
        MessageSourceFunction<?> source = assertInstanceOf(MessageSourceFunction.class,
                factory.createSourceFunction(new StreamConnectorConfig("message",
                        Map.of("topic", "t1", "messageService", service))));
        assertEquals(SourceConsistencyCapability.AT_LEAST_ONCE, source.getSourceConsistency());

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals("message", d.getTypeName());
        assertEquals(d.getSourceConsistency(), source.getSourceConsistency());
        assertEquals(ConnectorParallelism.PARALLEL, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.NONE, d.getRecoverySemantic());
    }

    @Test
    public void testMessageSourceFactoryRequiresMessageService() {
        MessageSourceConnectorFactory factory = new MessageSourceConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSourceFunction(new StreamConnectorConfig("message",
                        Map.of("topic", "t1"))));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("messageService", ex.getParam("paramName"));
    }

    @Test
    public void testMessageSourceFactoryRequiresTopic() {
        MessageSourceConnectorFactory factory = new MessageSourceConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSourceFunction(new StreamConnectorConfig("message",
                        Map.of("messageService", new LocalMessageService()))));
        assertEquals(ERR_STREAM_CONNECTOR_PARAM_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertEquals("topic", ex.getParam("paramName"));
    }

    // ------------------------------------------------------------------
    // message sink
    // ------------------------------------------------------------------

    @Test
    public void testMessageSinkFactoryConstructsEndpointWithAlignment() {
        MessageSinkConnectorFactory factory = new MessageSinkConnectorFactory();
        MessageSinkFunction<?> sink = assertInstanceOf(MessageSinkFunction.class,
                factory.createSink(new StreamConnectorConfig("message",
                        Map.of("topic", "t1", "messageService", new LocalMessageService()))));
        assertEquals(SinkConsistencyCapability.AT_LEAST_ONCE, sink.getSinkConsistency());

        ConnectorCapabilityDescriptor d = factory.describeCapabilities();
        assertEquals(d.getSinkConsistency(), sink.getSinkConsistency());
        assertEquals(ConnectorParallelism.PARALLEL, d.getParallelism());
        assertEquals(ConnectorRecoverySemantic.NONE, d.getRecoverySemantic());
    }

    @Test
    public void testMessageSinkFactoryRequiresBothParams() {
        MessageSinkConnectorFactory factory = new MessageSinkConnectorFactory();
        StreamException ex = assertThrows(StreamException.class,
                () -> factory.createSink(new StreamConnectorConfig("message", Map.of("topic", "t1"))));
        assertEquals("messageService", ex.getParam("paramName"));
        StreamException ex2 = assertThrows(StreamException.class,
                () -> factory.createSink(new StreamConnectorConfig("message",
                        Map.of("messageService", new LocalMessageService()))));
        assertEquals("topic", ex2.getParam("paramName"));
    }
}
