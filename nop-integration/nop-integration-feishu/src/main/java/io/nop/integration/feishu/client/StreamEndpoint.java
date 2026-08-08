package io.nop.integration.feishu.client;

/**
 * Stream gateway connection descriptor returned by
 * {@link IFeishuHttpApi#getStreamEndpoint(String, String)}.
 *
 * <p>Package-private: internal model.
 */
final class StreamEndpoint {

    private final String uri;
    private final String ticket;

    StreamEndpoint(String uri, String ticket) {
        this.uri = uri;
        this.ticket = ticket;
    }

    String getUri() {
        return uri;
    }

    String getTicket() {
        return ticket;
    }
}
