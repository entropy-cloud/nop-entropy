/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.ai.core.api.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.api.support.Media;
import io.nop.ai.core.api.support.Metadata;
import io.nop.commons.util.StringHelper;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({@JsonSubTypes.Type(value = AiUserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = AiAssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = AiSystemMessage.class, name = "system"),
})
public abstract class AiMessage extends Metadata {
    private String messageId;

    private List<Media> media;

    public static AiMessage create(String role) {
        if(AiCoreConstants.ROLE_USER.equals(role))
            return new AiUserMessage();
        if(AiCoreConstants.ROLE_ASSISTANT.equals(role))
            return new AiAssistantMessage();
        if(AiCoreConstants.ROLE_SYSTEM.equals(role))
            return new AiSystemMessage();
        throw new IllegalArgumentException("unknown role:" + role);
    }

    @JsonIgnore
    public boolean isSystemMessage(){
        return false;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public abstract String getRole();

    public abstract String getContent();

    public List<Media> getMedia() {
        return media;
    }

    public void setMedia(List<Media> media) {
        this.media = media;
    }

    public String getBlock(String blockBegin, String blockEnd, int blockIndex) {
        String message = getContent();
        if (message == null)
            return null;

        int[] pos = indexOfMark(message, 0, blockBegin, blockIndex);
        if (pos == null)
            return null;

        int[] pos2 = indexOfMark(message, pos[1], blockEnd);
        if (pos2 == null)
            return null;

        return message.substring(pos[1], pos2[0]);
    }



    public static int[] indexOfMark(String content, int start, String mark, int blockIndex) {
        int startPos = start;
        for (int i = 0; i < blockIndex; i++) {
            int[] pos = indexOfMark(content, startPos, mark);
            if (pos == null)
                return null;
            startPos = pos[1];
        }
        return indexOfMark(content, startPos, mark);
    }

    // 忽略无关紧要的空格
    public static int[] indexOfMark(String content, int start, String mark) {
        int pos = content.indexOf(mark, start);
        if (pos >= 0)
            return new int[]{pos, pos + mark.length()};
        String trimmedMark = mark.trim();
        pos = content.indexOf(trimmedMark, start);
        if (pos >= 0) {
            int pos0 = pos, pos1 = pos + trimmedMark.length();
            if (mark.startsWith("\n")) {
                int pos2 = content.lastIndexOf('\n', pos);
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos2 + 1, pos)))
                    return null;
                pos0 = pos2 + 1;
            }

            if (mark.endsWith("\n")) {
                int pos2 = content.indexOf('\n', pos1);
                if (pos2 < 0)
                    pos2 = content.length();
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos1, pos2)))
                    return null;
                pos1 = pos2;
            }

            return new int[]{pos0, pos1};
        }
        return null;
    }
}
