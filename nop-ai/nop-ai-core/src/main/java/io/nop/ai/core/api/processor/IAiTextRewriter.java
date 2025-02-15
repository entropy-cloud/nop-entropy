package io.nop.ai.core.api.processor;

public interface IAiTextRewriter {
    /**
     * 改写发送给AI模型的请求文本。
     *
     * @param originalText 原始请求文本
     * @return 改写后的请求文本
     */
    String rewriteRequestText(String originalText);

    /**
     * 修正AI模型返回的结果文本。
     *
     * @param responseText AI模型返回的原始文本
     * @return 修正后的结果文本
     */
    String correctResponseText(String responseText);
}
