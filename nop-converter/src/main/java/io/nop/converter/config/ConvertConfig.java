package io.nop.converter.config;

import io.nop.converter.config._gen._ConvertConfig;

import java.util.List;

public class ConvertConfig extends _ConvertConfig {
    public ConvertConfig() {

    }

    public void merge(ConvertConfig config) {
        List<ConvertBuilderConfig> builders = config.getBuilders();
        if (builders != null && !builders.isEmpty()) {
            builders.forEach(builder -> {
                if (config.hasBuilder(builder.getFileType()))
                    throw new IllegalArgumentException("Duplicate builder for file type: " + builder.getFileType());
                this.addBuilder(builder);
            });
        }

        List<ConvertConverterConfig> converters = config.getConverters();
        if (converters != null && !converters.isEmpty()) {
            converters.forEach(converter -> {
                if (config.hasConverter(converter.getId()))
                    throw new IllegalArgumentException("Duplicate converter : " + converter.getId());
                this.addConverter(converter);
            });
        }
    }
}
