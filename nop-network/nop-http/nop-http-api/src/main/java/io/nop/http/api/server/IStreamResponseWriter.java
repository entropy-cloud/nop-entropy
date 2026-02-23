package io.nop.http.api.server;

import java.util.concurrent.CompletionStage;

/**
 * 流式响应写入器接口
 *
 * <p>用于在 Streaming 模式下逐块写入响应数据。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * context.sendStreamingResponse(200, "text/event-stream", writer -> {
 *     writer.write("data: first\n\n");
 *     writer.write("data: second\n\n");
 *     return writer.complete();
 * });
 * }</pre>
 */
public interface IStreamResponseWriter {

    /**
     * 写入一个数据块
     *
     * @param chunk 数据内容
     * @return 写入完成的Future
     */
    CompletionStage<Void> write(String chunk);

    /**
     * 完成响应
     *
     * @return 完成的Future
     */
    CompletionStage<Void> complete();

    /**
     * 因错误而终止响应
     *
     * @param error 错误信息
     * @return 完成的Future
     */
    CompletionStage<Void> fail(Throwable error);
}
