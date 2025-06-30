package io.nop.converter;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@GlobalInstance
public class DocumentConverterRegistry {
    static final Logger LOG = LoggerFactory.getLogger(DocumentConverterRegistry.class);

    static DocumentConverterRegistry _instance = new DocumentConverterRegistry();

    private final Map<String, Map<String, IDocumentConverter>> converters = new HashMap<>();

    private final Map<String, IDocumentObjectBuilder> documentObjectBuilders = new HashMap<>();

    public static DocumentConverterRegistry instance() {
        return _instance;
    }

    public static void registerInstance(DocumentConverterRegistry instance) {
        _instance = instance;
    }

    public Set<String> getFromFileTypes() {
        return new TreeSet<>(converters.keySet());
    }

    public Set<String> getToFileTypes(String fromFileType) {
        Map<String, IDocumentConverter> map = converters.get(fromFileType);
        if (map != null)
            return new TreeSet<>(map.keySet());
        return Set.of();
    }

    public void registerConverter(String fromFileType, String toFileType, IDocumentConverter converter) {
        Guard.checkArgument(!fromFileType.equals(toFileType), "fromFileType and toFileType must not be the same: ");

        Map<String, IDocumentConverter> map = converters.computeIfAbsent(fromFileType, k -> new HashMap<>());
        IDocumentConverter oldConverter = map.put(toFileType, converter);
        if (oldConverter != null && oldConverter != converter) {
            LOG.warn("Duplicate converter for fileType {} to {}: {} vs {}", fromFileType, toFileType,
                    oldConverter.getClass().getName(), converter.getClass().getName());
        }
    }

    public IDocumentConverter getConverter(String fromFileType, String toFileType) {
        Guard.checkArgument(!fromFileType.equals(toFileType), "fromFileType and toFileType must not be the same: ");

        Map<String, IDocumentConverter> map = converters.get(fromFileType);
        if (map != null) {
            IDocumentConverter converter = map.get(toFileType);
            if (converter != null)
                return converter;

            if (toFileType.indexOf('.') > 0) {
                converter = map.get(StringHelper.lastPart(toFileType, '.'));
                if (converter != null)
                    return converter;
            }
        }
        return null;
    }

    public void registerDocumentObjectBuilder(String fileType, IDocumentObjectBuilder builder) {
        IDocumentObjectBuilder oldBuilder = documentObjectBuilders.put(fileType, builder);
        if (oldBuilder != null && oldBuilder != builder) {
            LOG.warn("Duplicate document object builder for type {}: {} vs {}", fileType, oldBuilder.getClass().getName(), builder.getClass().getName());
        }
    }
}