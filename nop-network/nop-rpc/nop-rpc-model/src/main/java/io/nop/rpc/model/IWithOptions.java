/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface IWithOptions {
    String getDescription();

    List<ApiOptionModel> getOptions();

    ApiOptionModel getOption(String name);

    void addOption(ApiOptionModel option);

    default Object getOptionValue(String name) {
        ApiOptionModel option = getOption(name);
        return option == null ? null : option.getValue();
    }

    default void setOptionValue(String name, Object value) {
        if (value == null) {
            removeOption(name);
        } else {
            ApiOptionModel option = ApiOptionModel.of(name, value);
            addOption(option);
        }
    }

    default void removeOption(String name) {
        ApiOptionModel option = getOption(name);
        if (option != null)
            getOptions().remove(option);
    }

    default Map<String, Object> getOptionMap() {
        List<ApiOptionModel> options = getOptions();
        Map<String, Object> ret = new LinkedHashMap<>();
        for (ApiOptionModel option : options) {
            ret.put(option.getName(), option.getValue());
        }
        return ret;
    }

    default void setOptionMap(Map<String, Object> options) {
        if (options != null) {
            options.forEach((name, value) -> {
                ApiOptionModel option = new ApiOptionModel();
                option.setName(name);
                option.setValue(value);
                addOption(option);
            });
        }
    }
}
