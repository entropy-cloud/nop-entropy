package io.nop.ai.coder.xdsl;

/**
 * DSL schema and document access used by the AI coder.
 * <p>
 * Provides read access to xdef schema definitions (by path or by file type) and
 * load/save of DSL documents with type conversion (e.g. between XML and JSON).
 */
public interface IDslTool {
    /**
     * Loads the xdef schema for the given schema path.
     *
     * @param schemaPath the xdef schema resource path
     * @return the schema definition text
     */
    String loadDslSchema(String schemaPath);

    /**
     * Loads the xdef schema registered for the given file type.
     *
     * @param fileType the file type (e.g. {@code xdsl}, {@code xmeta})
     * @return the schema definition text
     */
    String loadDslSchemaForFileType(String fileType);

    /**
     * Loads a DSL document and converts it to the given target type.
     *
     * @param filePath   the DSL document path
     * @param toFileType the target format (e.g. XML, JSON)
     * @return the converted document text
     */
    String loadDslFile(String filePath, String toFileType);

    /**
     * Saves a DSL document, converting it from the given source type.
     *
     * @param filePath     the DSL document path to write
     * @param fromFileType the source format of the content
     * @param content      the document content to save
     */
    void saveDslFile(String filePath, String fromFileType, String content);
}