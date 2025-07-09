package io.nop.spring.mcp.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfiguration {

    @Bean
    public ToolCallbackProvider toolCallbackProvider() {
        return new GraphQLToolCallbackProvider();
    }
}