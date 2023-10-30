package io.nop.web.page.vue;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface VueErrors {
    String ARG_SLOT_NAME = "slotName";
    ErrorCode ERR_VUE_TEMPLATE_NO_SLOT_NAME =
            define("nop.err.vue.template-no-slot-name", "模板没有通过v-slot:[name]指定slot名称");

    ErrorCode ERR_VUE_SLOT_NOT_ALLOW_SLOT_CHILD =
            define("nop.err.vue.slot-no-allow-slot-child", "slot的子节点不允许再定义slot");

}
