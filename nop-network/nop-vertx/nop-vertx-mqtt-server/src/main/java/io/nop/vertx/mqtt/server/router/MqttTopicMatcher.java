/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.mqtt.server.router;

/**
 * MQTT 3.1.1 topic 过滤器匹配：'+' 匹配单层，'#' 匹配多层尾部，分隔符 '/'。
 * 无状态纯函数，入站路由与出站路由共用。
 */
public class MqttTopicMatcher {
    private static final char SEPARATOR = '/';
    private static final char SINGLE_WILDCARD = '+';
    private static final char MULTI_WILDCARD = '#';

    /**
     * @param filter 订阅过滤器，如 "a/b/+"、"a/#"、"$share/g/a/b" 已剥离后
     * @param topic  具体 topic，如 "a/b/c"
     */
    public static boolean matches(String filter, String topic) {
        if (filter == null || topic == null)
            return false;
        return doMatch(filter, 0, topic, 0);
    }

    private static boolean doMatch(String filter, int fPos, String topic, int tPos) {
        while (true) {
            int fEnd = nextTokenEnd(filter, fPos);
            int tEnd = nextTokenEnd(topic, tPos);

            boolean fLast = fEnd == filter.length();
            boolean tLast = tEnd == topic.length();

            String fToken = filter.substring(fPos, fEnd);
            String tToken = topic.substring(tPos, tEnd);

            if (fToken.length() == 1 && fToken.charAt(0) == MULTI_WILDCARD) {
                // '#' 必须是最后一个 token（非法过滤器按"匹配一切"处理，与多数 broker 一致）
                return fLast;
            }

            if (!(fToken.length() == 1 && fToken.charAt(0) == SINGLE_WILDCARD) && !fToken.equals(tToken)) {
                return false;
            }

            if (fLast && tLast)
                return true;
            // topic 已到最后一个 token，而过滤器剩余部分恰为 "#"（含父层）时匹配
            if (tLast && !fLast && filter.length() == fEnd + 2 && filter.charAt(fEnd + 1) == MULTI_WILDCARD)
                return true;
            if (fLast != tLast)
                return false;

            fPos = fEnd + 1;
            tPos = tEnd + 1;
        }
    }

    private static int nextTokenEnd(String s, int from) {
        int pos = s.indexOf(SEPARATOR, from);
        return pos < 0 ? s.length() : pos;
    }
}
