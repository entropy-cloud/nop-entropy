# AI Core API Migration Guide

## Overview

The Nop Platform AI Core API has been refactored to provide a cleaner, more modern interface for AI chat functionality. This guide explains the migration path from the deprecated internal AI core API to the new public API.

## What's Changed

### 1. Deprecated Internal API

All classes in `io.nop.ai.core.api.*` have been marked with `@Deprecated` annotation:

**Interfaces:**
- `@Deprecated IAiChatService`
- `@Deprecated IAiChatSession`
- `@Deprecated IAiChatProgressListener`
- `@Deprecated IAiChatToolSet`
- `@Deprecated IAiChatFunctionTool`

**Classes:**
- `@Deprecated AiChatOptions`
- `@Deprecated AiChatExchange`
- `@Deprecated Prompt`
- `@Deprecated AiChatUsage`
- `@Deprecated AiMessageAttachment`
- `@Deprecated ToolCall`
- `@Deprecated ToolSpecification`
- `@Deprecated DefaultAiChatToolSet`
- `@Deprecated DefaultAiChatFunctionTool`
- `@Deprecated ToolSpecificationLoader`

**Messages:**
- `@Deprecated AiMessage` and all subclasses:
  - `@Deprecated AiUserMessage`
  - `@Deprecated AiAssistantMessage`
  - `@Deprecated AiSystemMessage`
  - `@Deprecated AiToolResponseMessage`
  - `@Deprecated AiToolMessage`
- `@Deprecated MessageStatus`

### 2. New Public API

The new public API is located in `io.nop.ai.api.*` and provides:

#### Core Interfaces
- `IChatService` - Main chat service interface
- `IChatStreamHandler` - Stream handler interface

#### Data Classes
- `ChatRequest` - Request model
- `ChatResponse` - Response model
- `ChatOptions` - Configuration options
- `ChatStreamChunk` - Streaming response chunks

#### Message Types
- `ChatMessage` - Base message interface
- `ChatUserMessage`, `ChatAssistantMessage`, `ChatSystemMessage`
- `ChatToolResponseMessage`, `ChatToolCall`
- `ChatToolDefinition` - Tool/function definitions

## Migration Path

### For Service Implementations

#### Old Way (Deprecated)
```java
@Deprecated
public class OldAiChatService implements IAiChatService {
    // Implementation using internal API
}
```

#### New Way (Recommended)
```java
public class NewChatService implements IChatService {
    @Inject
    private IHttpClient httpClient;
    
    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        // Implementation using HTTP client directly
        HttpRequest httpRequest = buildHttpRequest(request);
        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> parseChatResponse(response.getBodyAsText(), request.getOptions()));
    }
    
    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        // Implementation using Flow API for streaming
        SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
        // Stream implementation
        return publisher;
    }
}
```

### For Message Handling

#### Old Way (Deprecated)
```java
@Deprecated
AiMessage message = new AiUserMessage("Hello");
```

#### New Way (Recommended)
```java
ChatMessage message = new ChatUserMessage("Hello");
```

### For Tool/Function Support

#### Old Way (Deprecated)
```java
@Deprecated
IAiChatToolSet toolSet = new DefaultAiChatToolSet();
```

#### New Way (Recommended)
```java
List<ChatToolDefinition> tools = Arrays.asList(
    new ChatToolDefinition("function_name", "Description", parameters)
);
ChatOptions options = new ChatOptions();
options.setTools(tools);
```

## Key Benefits of the New API

1. **Simplicity**: Direct HTTP client usage eliminates internal API complexity
2. **Performance**: Flow-based streaming for better performance
3. **Flexibility**: No dependency on internal implementation details
4. **Modern Java**: Uses CompletionStage and Flow APIs
5. **Clean Separation**: Clear separation between public and internal APIs

## Migration Steps

1. **Update Dependencies**: Remove dependencies on `nop-ai-core` internal API
2. **Replace Interface Implementations**: Implement `IChatService` instead of `IAiChatService`
3. **Update Message Types**: Use new message classes from `io.nop.ai.api.chat.messages`
4. **Update Tool Handling**: Use `ChatToolDefinition` instead of `ToolSpecification`
5. **Update Streaming**: Use `Flow.Publisher<ChatStreamChunk>` for streaming responses
6. **Remove Deprecated Code**: Remove all `@Deprecated` API usage

## Example Migration

### Before (Deprecated)
```java
@Deprecated
public class MyAiService implements IAiChatService {
    private IAiChatSession session;
    
    public CompletionStage<AiChatExchange> chatAsync(List<AiMessage> messages) {
        // Using deprecated internal API
        return session.chatAsync(messages, new AiChatOptions());
    }
}
```

### After (Recommended)
```java
public class MyNewChatService implements IChatService {
    @Inject
    private IHttpClient httpClient;
    
    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        // Using new public API with direct HTTP calls
        HttpRequest httpRequest = buildHttpRequest(request);
        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> parseChatResponse(response.getBodyAsText(), request.getOptions()));
    }
}
```

## Timeline

- **Immediate**: All deprecated APIs are available but marked with `@Deprecated`
- **Next Version**: Deprecated APIs will be removed
- **Recommended**: Migrate to new API as soon as possible

## Support

For questions about the migration, please refer to:
- The updated `ChatServiceImpl` implementation
- Unit tests in `nop-ai-core/src/test/java/io/nop/ai/core/service/`
- API documentation in `io.nop.ai.api.*` packages