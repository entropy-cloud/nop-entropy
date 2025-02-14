package io.nop.ai.translate.support;

import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class TextSplitHelper {
    public static String getProlog(List<String> parts, int index, int prologSize) {
        if (index == 0)
            return null;

        List<String> ret = new ArrayList<>();
        int size = 0;
        for (int i = index - 1; i >= 0; i--) {
            String line = parts.get(i);
            if (line.isEmpty()) {
                ret.add("");
                continue;
            }

            if (size + line.length() <= prologSize) {
                ret.add(line);
                size += line.length() + 1;
                if (size >= prologSize)
                    break;
            } else {
                int diff = size + line.length() - prologSize;
                ret.add(line.substring(line.length() - diff, line.length()));
                break;
            }
        }
        return StringHelper.join(CollectionHelper.reverseList(ret), "\n");
    }

    public static int findStartsWith(List<String> parts, int index, String prefix) {
        for (int i = index, n = parts.size(); i < n; i++) {
            if (parts.get(i).startsWith(prefix))
                return i;
        }
        return -1;
    }

    public static int sumLength(List<String> parts, int fromIndex, int endIndex) {
        int total = 0;
        for (int i = fromIndex; i < endIndex; i++) {
            String part = parts.get(i);
            total += part.length() + 1;
        }
        return total;
    }

    public static void append(StringBuilder sb, List<String> parts, int fromIndex, int endIndex) {
        for (int i = fromIndex; i < endIndex; i++) {
            String part = parts.get(i);
            sb.append(part).append('\n');
        }
    }
}
