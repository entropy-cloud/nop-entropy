package io.nop.ai.coder;

import java.util.Set;

public interface AiCoderConstants {
    String SCHEMA_AI_ORM = "/nop/ai/schema/coder/orm.xdef";

    String SCHEMA_AI_API = "/nop/ai/schema/coder/api.xdef";

    String ATTR_ORM_REF_TABLE = "orm:ref-table";
    String ATTR_ORM_REF_PROP = "orm:ref-prop";

    String ATTR_ORM_REF_PROP_DISPLAY_NAME = "orm:ref-prop-display-name";

    Set<String> DEFAULT_POSITIONING_KEYS = Set.of("id", "name", "type");
}
