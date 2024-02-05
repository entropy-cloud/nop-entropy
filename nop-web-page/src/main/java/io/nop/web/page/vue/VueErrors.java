package io.nop.web.page.vue;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface VueErrors {
    String ARG_SLOT_NAME = "slotName";

    String ARG_TAG_NAME = "tagName";
    ErrorCode ERR_VUE_TEMPLATE_NO_SLOT_NAME =
            define("nop.err.vue.template-no-slot-name", "模板没有通过v-slot:[name]指定slot名称");

    ErrorCode ERR_VUE_SLOT_NOT_ALLOW_SLOT_CHILD =
            define("nop.err.vue.slot-no-allow-slot-child", "slot的子节点不允许再定义slot");

    ErrorCode ERR_VUE_V_CHILD_NOT_ALLOW_ATTR =
            define("nop.err.vue.v-child-not-allow-attr", "以v:为前缀的子节点是属性的扩展表示方式，它本身不能再具有属性");

    ErrorCode ERR_VUE_V_CHILD_NOT_ALLOW_SLOT =
            define("nop.err.vue.v-child-not-allow-slot", "以v:为前缀的子节点是属性的扩展表示方式，它本身不能具有slot");
}
