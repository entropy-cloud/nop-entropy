package io.nop.ai.coder.orm;

import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.io.IOException;

public class DictGenHelper {
    public static void generateOptions(DictBean dict, String dictName, boolean useDictCode, Appendable sb){
        try {
            if (dict != null && dict.getOptions() != null) {
                if (dictName != null) {
                    sb.append(" in ");
                    sb.append(AiCoderHelper.camelCaseName(dictName, true));
                }
                sb.append(" Options: ");
                for (DictOptionBean option : dict.getOptions()) {
                    String value = option.getStringValue();
                    if (useDictCode && !StringHelper.isEmpty(option.getCode())
                            && StringHelper.isAllDigit(value))
                        value = option.getCode();
                    sb.append(value).append("[");
                    sb.append(option.getLabel()).append("],");
                }
            }
        }catch (IOException e){
            throw NopException.adapt(e);
        }
    }
}
