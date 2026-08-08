package io.nop.ai.gateway.channel;

/**
 * Self-description of a channel's capabilities (design §8). A connector
 * declares what its underlying transport supports so that message formatting,
 * permission decisions and rate-limit handling can adapt per channel.
 *
 * <p>Defaults follow the most restrictive reasonable posture: a freshly
 * constructed capabilities object declares support for nothing and zero size
 * limits. Concrete connectors populate the fields that match their transport.
 */
public class ChannelCapabilities {

    private boolean supportsMarkdown;
    private boolean supportsFileUpload;
    private boolean supportsFileDownload;
    private boolean supportsStreaming;
    private boolean supportsGroupChat;
    private boolean supportsMentions;
    private boolean supportsTypingIndicator;

    private int maxMessageLength;
    private int maxFileSize;
    private int rateLimitPerMinute;

    public boolean isSupportsMarkdown() {
        return supportsMarkdown;
    }

    public void setSupportsMarkdown(boolean supportsMarkdown) {
        this.supportsMarkdown = supportsMarkdown;
    }

    public boolean isSupportsFileUpload() {
        return supportsFileUpload;
    }

    public void setSupportsFileUpload(boolean supportsFileUpload) {
        this.supportsFileUpload = supportsFileUpload;
    }

    public boolean isSupportsFileDownload() {
        return supportsFileDownload;
    }

    public void setSupportsFileDownload(boolean supportsFileDownload) {
        this.supportsFileDownload = supportsFileDownload;
    }

    public boolean isSupportsStreaming() {
        return supportsStreaming;
    }

    public void setSupportsStreaming(boolean supportsStreaming) {
        this.supportsStreaming = supportsStreaming;
    }

    public boolean isSupportsGroupChat() {
        return supportsGroupChat;
    }

    public void setSupportsGroupChat(boolean supportsGroupChat) {
        this.supportsGroupChat = supportsGroupChat;
    }

    public boolean isSupportsMentions() {
        return supportsMentions;
    }

    public void setSupportsMentions(boolean supportsMentions) {
        this.supportsMentions = supportsMentions;
    }

    public boolean isSupportsTypingIndicator() {
        return supportsTypingIndicator;
    }

    public void setSupportsTypingIndicator(boolean supportsTypingIndicator) {
        this.supportsTypingIndicator = supportsTypingIndicator;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(int maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }
}
