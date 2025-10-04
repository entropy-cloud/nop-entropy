package io.nop.ai.coder.xdsl;

public interface IDslTool {
    String loadDslSchema(String schemaPath);

    String loadDslSchemaForFileType(String fileType);

    String loadDslFile(String filePath, String toFileType);

    void saveDslFile(String filePath, String fromFileType, String content);
}