package io.nop.converter;

import io.nop.core.resource.IResource;

import java.util.Set;

/**
 * 文档转换核心能力接口
 */
public interface IDocumentConverterManager extends IDocumentObjectBuilder {

    // 获取所有支持的源文件格式
    Set<String> getFromFileTypes();

    // 查询从某格式能转到哪些目标格式（allowChained是否允许多步转换）
    Set<String> getToFileTypes(String fromFileType, boolean allowChained);

    // 文本内容直接转换（path用于错误追踪）
    String convertText(String path, String text, String fromFileType, String toFileType, DocumentConvertOptions options);

    // 资源文件转换（自动识别格式）
    void convertResource(IResource fromResource, IResource toResource, DocumentConvertOptions options);

    // 资源文件转换（手动指定格式）
    void convertResource(IResource fromResource, IResource toResource,
                         String fromFileType, String toFileType, DocumentConvertOptions options);

    // 注册转换器（from→to）
    void registerConverter(String fromFileType, String toFileType, IDocumentConverter converter);

    // 获取转换器（可返回null）
    IDocumentConverter getConverter(String fromFileType, String toFileType, boolean allowChained);

    // 注册格式解析器
    void registerDocumentObjectBuilder(String fileType, IDocumentObjectBuilder builder);

    Set<String> getDocumentFileTypes();

    // 获取格式解析器（可返回null）
    IDocumentObjectBuilder getDocumentObjectBuilder(String fileType);

    // 获取格式解析器（不存在时报错）
    IDocumentObjectBuilder requireDocumentObjectBuilder(String fileType);

    @Override
    default IDocumentObject buildFromResource(String fileType, IResource resource) {
        return requireDocumentObjectBuilder(fileType).buildFromResource(fileType, resource);
    }

    @Override
    default IDocumentObject buildFromText(String fileType, String path, String text) {
        return requireDocumentObjectBuilder(fileType).buildFromText(fileType, path, text);
    }

    // 获取转换器（不存在时报错）
    IDocumentConverter requireConverter(String fromFileType, String toFileType, boolean allowChained);

    default boolean isBinaryOnly(String fileType) {
        IDocumentObjectBuilder builder = getDocumentObjectBuilder(fileType);
        return builder != null && builder.isBinaryOnly(fileType);
    }


}