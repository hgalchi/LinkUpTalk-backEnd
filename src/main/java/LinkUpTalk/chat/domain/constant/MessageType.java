package LinkUpTalk.chat.domain.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageType {
    GROUP_CHAT,
    DM_CHAT,
    JOIN,
    ERROR
}

