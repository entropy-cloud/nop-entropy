package io.nop.ai.core.utils;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.util.Guard;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdsl.XDslValidateHelper;
import io.nop.xlang.xdsl.action.BizActionGenHelper;

public class AiGenHelper {
    public static XNode buildBizActionFromPromptTemplate(XNode actionNode, IPromptTemplate promptTemplate) {
        String promptName = actionNode.attrText(AiCoreConstants.ATTR_AI_PROMPT_NAME);
        Guard.notEmpty(promptName, AiCoreConstants.ATTR_AI_PROMPT_NAME);

        Integer retryTimesPerRequest = actionNode.attrInt(AiCoreConstants.ATTR_AI_RETRY_TIMES_PER_REQUEST, 1);

        XNode executeNode = XNode.make("ai:Execute");
        executeNode.setAttr(XLangConstants.ATTR_XPL_LIB, "/nop/ai/xlib/ai.xlib");
        executeNode.setAttr(AiCoreConstants.ATTR_AI_PROMPT_NAME, promptName);
        executeNode.setAttr(XLangConstants.ATTR_XPL_RETURN, "chatResponsePromise");
        if (retryTimesPerRequest != null)
            executeNode.setAttr("retryTimesPerRequest", retryTimesPerRequest);
        executeNode.setAttr("asyncExec", true);
        executeNode.setAttr("cancelToken", "${svcCtx}");

        XNode chatOptionsNode = actionNode.childByTag(AiCoreConstants.TAG_AI_CHAT_OPTIONS);
        if (chatOptionsNode != null) {
            XDslValidateHelper.validateForClassProps(chatOptionsNode, AiChatOptions.class);
            executeNode._assignAttrs(chatOptionsNode.attrValueLocs());
        }

        return BizActionGenHelper.buildBizActionFromActionModel(actionNode, promptTemplate, (argNames, useResult) -> {
            String source = executeNode.xml();
            if (useResult) {
                source += "<c:script>chatResponsePromise.thenApply(res=> res.validate().resultValue)</c:script>";
            } else {
                source += "<c:script>chatResponsePromise.thenApply(res=> res.validate().outputs)</c:script>";
            }
            return source;
        });
    }
}
