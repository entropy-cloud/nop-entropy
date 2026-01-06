package io.nop.converter;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.converter.impl.ChainedDocumentConverter;
import io.nop.converter.impl.SameTypeDocumentConverter;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.converter.DocConvertErrors.ARG_FILE_TYPE;
import static io.nop.converter.DocConvertErrors.ARG_FROM_FILE_TYPE;
import static io.nop.converter.DocConvertErrors.ARG_TO_FILE_TYPE;
import static io.nop.converter.DocConvertErrors.ERR_NO_CONVERTER_FROM_TYPE_TO_TYPE;
import static io.nop.converter.DocConvertErrors.ERR_NO_DOCUMENT_OBJECT_BUILDER;

@GlobalInstance
public class DocumentConverterManager implements IDocumentConverterManager {
    static final Logger LOG = LoggerFactory.getLogger(DocumentConverterManager.class);

    static DocumentConverterManager _instance = new DocumentConverterManager();

    private final Map<String, Map<String, IDocumentConverter>> converters = new HashMap<>();

    // 从toFileType到fromFileType的映射
    private final Map<String, Set<String>> fromFileTypeMap = new HashMap<>();

    private final Map<String, IDocumentObjectBuilder> documentObjectBuilders = new HashMap<>();

    public static DocumentConverterManager instance() {
        return _instance;
    }

    public static void registerInstance(DocumentConverterManager instance) {
        _instance = instance;
    }

    @Override
    public Set<String> getFromFileTypes() {
        return new TreeSet<>(converters.keySet());
    }

    /**
     * 获取可以从指定文件类型转换到的所有目标文件类型
     *
     * @param fromFileType 源文件类型
     * @param allowChained 是否包含通过链式转换可达的目标类型
     * @return 可转换到的目标文件类型集合
     */
    @Override
    public Set<String> getToFileTypes(String fromFileType, boolean allowChained) {
        // 获取直接可转换的目标类型
        Set<String> directTypes = getDirectToFileTypes(fromFileType);

        if (!allowChained) {
            return directTypes;
        }

        // 如果需要包含链式转换可达的类型
        Set<String> allTypes = new TreeSet<>(directTypes);

        // 遍历所有直接可转换的目标类型，递归查找它们能转换到的类型
        for (String directType : directTypes) {
            // 避免循环依赖导致的无限递归
            if (!directType.equals(fromFileType)) {
                Set<String> indirectTypes = getToFileTypes(directType, true);
                allTypes.addAll(indirectTypes);
            }
        }

        allTypes.remove(fromFileType);
        return allTypes;
    }

    /**
     * 获取可以直接从指定文件类型转换到的目标文件类型
     *
     * @param fromFileType 源文件类型
     * @return 直接可转换到的目标文件类型集合
     */
    protected Set<String> getDirectToFileTypes(String fromFileType) {
        Map<String, IDocumentConverter> map = converters.get(fromFileType);
        if (map != null) {
            return new TreeSet<>(map.keySet());
        }
        return Set.of();
    }

    @Override
    public String convertText(String path, String text, String fromFileType, String toFileType, DocumentConvertOptions options) {
        if (options == null)
            options = DocumentConvertOptions.create();

        IDocumentObjectBuilder builder = requireDocumentObjectBuilder(fromFileType);
        IDocumentObject doc = builder.buildFromText(fromFileType, path, text);
        if (fromFileType.equals(toFileType))
            return doc.getText(options);

        IDocumentConverter converter = requireConverter(fromFileType, toFileType, options.isAllowChained());
        return converter.convertToText(doc, toFileType, options);
    }

    @Override
    public void convertResource(IResource fromResource, IResource toResource, DocumentConvertOptions options) {
        String fromFileType = StringHelper.fileType(fromResource.getPath());
        String toFileType = StringHelper.fileType(toResource.getPath());

        convertResource(fromResource, toResource, fromFileType, toFileType, options);
    }

    @Override
    public void convertResource(IResource fromResource, IResource toResource,
                                String fromFileType, String toFileType, DocumentConvertOptions options) {
        if (options == null)
            options = DocumentConvertOptions.create();

        IDocumentObjectBuilder builder = requireDocumentObjectBuilder(fromFileType);
        IDocumentObject doc = builder.buildFromResource(fromFileType, fromResource);
//        if (toFileType.equals(fromFileType)) {
//            if (isKeepRaw(builder, fromFileType, options)) {
//                doc.saveToResource(toResource, options);
//                return;
//            }
//        }

        IDocumentConverter converter = requireConverter(fromFileType, toFileType, options.isAllowChained());
        converter.convertToResource(doc, toFileType, toResource, options);
    }

    boolean isKeepRaw(IDocumentObjectBuilder builder, String fileType, DocumentConvertOptions options) {
        if (builder.isBinaryOnly(fileType))
            return true;
        if (options.getDslNodeResolvePhase() == IResourceDslNodeLoader.ResolvePhase.raw)
            return true;
        return false;
    }

    @Override
    public void registerConverter(String fromFileType, String toFileType, IDocumentConverter converter) {
        Guard.checkArgument(!fromFileType.equals(toFileType), "fromFileType and toFileType must not be the same: ");

        Map<String, IDocumentConverter> map = converters.computeIfAbsent(fromFileType, k -> new HashMap<>());
        IDocumentConverter oldConverter = map.put(toFileType, converter);
        if (oldConverter != null && oldConverter != converter) {
            LOG.warn("Duplicate converter for fileType {} to {}: {} vs {}", fromFileType, toFileType,
                    oldConverter.getClass().getName(), converter.getClass().getName());
        }

        fromFileTypeMap.computeIfAbsent(toFileType, k -> new TreeSet<>()).add(fromFileType);
    }

    @Override
    public IDocumentConverter getConverter(String fromFileType, String toFileType, boolean allowChained) {
        // Guard.checkArgument(!fromFileType.equals(toFileType), "fromFileType and toFileType must not be the same: ");

        if (fromFileType.equals(toFileType))
            return SameTypeDocumentConverter.INSTANCE;

        // First try direct converter
        IDocumentConverter converter = getDirectConverter(fromFileType, toFileType, false);
        if (converter != null) {
            return converter;
        }

        // If no direct converter and chained is allowed, try to find a two-step converter
        if (allowChained) {
            converter = findChainedConverter(fromFileType, toFileType, false);
            if (converter != null)
                return converter;
        }

        converter = getDirectConverter(fromFileType, toFileType, true);
        if (converter != null)
            return converter;

        if (allowChained) {
            converter = findChainedConverter(fromFileType, toFileType, true);
        }

        return converter;
    }

    protected IDocumentConverter getDirectConverter(String fromFileType, String toFileType, boolean allowFileExt) {
        Guard.checkArgument(!fromFileType.equals(toFileType), "fromFileType and toFileType must not be the same: ");

        Map<String, IDocumentConverter> map = converters.get(fromFileType);
        if (map != null) {
            IDocumentConverter converter = map.get(toFileType);
            if (converter != null)
                return converter;

            if (allowFileExt && toFileType.indexOf('.') > 0) {
                converter = map.get(StringHelper.fileExtFromFileType(toFileType));
                if (converter != null)
                    return converter;
            }
        }

        if (allowFileExt && fromFileType.indexOf('.') > 0) {
            return getDirectConverter(StringHelper.fileExtFromFileType(fromFileType), toFileType, allowFileExt);
        }
        return null;
    }

    @Override
    public void registerDocumentObjectBuilder(String fileType, IDocumentObjectBuilder builder) {
        IDocumentObjectBuilder oldBuilder = documentObjectBuilders.put(fileType, builder);
        if (oldBuilder != null && oldBuilder != builder) {
            LOG.warn("Duplicate document object builder for type {}: {} vs {}", fileType, oldBuilder.getClass().getName(), builder.getClass().getName());
        }
    }

    public Set<String> getDocumentFileTypes() {
        return new TreeSet<>(documentObjectBuilders.keySet());
    }

    @Override
    public IDocumentObjectBuilder getDocumentObjectBuilder(String fileType) {
        IDocumentObjectBuilder builder = documentObjectBuilders.get(fileType);
        if (builder == null) {
            String fileExt = StringHelper.fileExtFromFileType(fileType);
            if (!fileExt.equals(fileType)) {
                builder = documentObjectBuilders.get(fileExt);
            }
        }
        return builder;
    }

    @Override
    public IDocumentObjectBuilder requireDocumentObjectBuilder(String fileType) {
        IDocumentObjectBuilder builder = getDocumentObjectBuilder(fileType);
        if (builder == null) {
            throw new NopException(ERR_NO_DOCUMENT_OBJECT_BUILDER).param(ARG_FILE_TYPE, fileType);
        }
        return builder;
    }

    @Override
    public IDocumentConverter requireConverter(String fromFileType, String toFileType, boolean allowChained) {
        IDocumentConverter converter = getConverter(fromFileType, toFileType, allowChained);
        if (converter == null) {
            throw new NopException(ERR_NO_CONVERTER_FROM_TYPE_TO_TYPE)
                    .param(ARG_FROM_FILE_TYPE, fromFileType)
                    .param(ARG_TO_FILE_TYPE, toFileType);
        }
        return converter;
    }

    private IDocumentConverter findChainedConverter(String fromFileType, String toFileType, boolean allowFileExt) {
        // 查找所有能转换到目标类型的中间类型
        Set<String> intermediateTypes = fromFileTypeMap.get(toFileType);
        if (intermediateTypes == null || intermediateTypes.isEmpty()) {
            return null;
        }

        // 尝试每个中间类型
        for (String intermediateType : intermediateTypes) {
            IDocumentConverter firstStep = getDirectConverter(fromFileType, intermediateType, allowFileExt);
            if (firstStep != null) {
                IDocumentConverter secondStep = getDirectConverter(intermediateType, toFileType, allowFileExt);
                if (secondStep != null) {
                    IDocumentObjectBuilder objectBuilder = requireDocumentObjectBuilder(intermediateType);
                    return new ChainedDocumentConverter(objectBuilder, firstStep, secondStep, intermediateType);
                }
            }
        }

        // 尝试使用文件扩展名
        if (allowFileExt && fromFileType.indexOf('.') > 0) {
            return findChainedConverter(StringHelper.fileExtFromFileType(fromFileType), toFileType, true);
        }

        return null;
    }
}