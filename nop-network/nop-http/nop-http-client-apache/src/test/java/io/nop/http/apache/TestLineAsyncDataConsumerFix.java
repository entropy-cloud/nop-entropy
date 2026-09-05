package io.nop.http.apache;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLineAsyncDataConsumerFix {

    static class CollectingConsumer extends LineAsyncDataConsumer {
        final List<String> lines = new ArrayList<>();

        CollectingConsumer() {
            super(1024, CharCodingConfig.DEFAULT, 1024);
        }

        @Override
        protected void onLine(String line) {
            lines.add(line);
        }

        @Override
        public void failed(Exception cause) {
        }
    }

    static EntityDetails entityDetails(String contentType) {
        return new EntityDetails() {
            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public String getContentEncoding() {
                return null;
            }

            @Override
            public boolean isChunked() {
                return true;
            }

            @Override
            public java.util.Set<String> getTrailerNames() {
                return java.util.Set.of();
            }
        };
    }

    @Test
    public void testResultCallbackCompleted() throws Exception {
        CollectingConsumer consumer = new CollectingConsumer();
        AtomicInteger completed = new AtomicInteger();

        consumer.onStreamBegin(new org.apache.hc.core5.http.message.BasicHttpResponse(200),
                ContentType.parse("text/event-stream"));
        consumer.streamStart(entityDetails("text/event-stream"), new FutureCallback<Void>() {
            @Override
            public void completed(Void result) {
                completed.incrementAndGet();
            }

            @Override
            public void failed(Exception ex) {
            }

            @Override
            public void cancelled() {
            }
        });

        consumer.consume(ByteBuffer.wrap("data: a\n\n".getBytes(StandardCharsets.UTF_8)));
        consumer.streamEnd(List.of());

        assertEquals(List.of("data: a", ""), consumer.lines);
        // AsyncEntityConsumer 契约：实体消费完成后必须完成 streamStart 传入的回调，
        // 否则 execute() 返回的 future 永远不会完成
        assertEquals(1, completed.get());
    }

    @Test
    public void testCrLfLineEndings() throws Exception {
        CollectingConsumer consumer = new CollectingConsumer();
        consumer.onStreamBegin(new org.apache.hc.core5.http.message.BasicHttpResponse(200),
                ContentType.parse("text/event-stream"));
        consumer.streamStart(entityDetails("text/event-stream"), null);

        // CRLF 行结尾（SSE 规范允许），且 \r\n 跨 chunk 分割
        consumer.consume(ByteBuffer.wrap("data: hello\r".getBytes(StandardCharsets.UTF_8)));
        consumer.consume(ByteBuffer.wrap("\n\r\n".getBytes(StandardCharsets.UTF_8)));
        consumer.streamEnd(List.of());

        assertEquals(List.of("data: hello", ""), consumer.lines);
        assertTrue(consumer.lines.stream().noneMatch(l -> l.endsWith("\r")), "lines must not contain trailing CR");
    }
}
