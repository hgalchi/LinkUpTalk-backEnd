package LinkUpTalk.chat.infrastructor.jpa;

import LinkUpTalk.chat.domain.repository.ChatRoomDetailRepository;
import LinkUpTalk.chat.domain.ChatRoomDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaChatRoomDetailRepository extends ExtendedRepository<ChatRoomDetail, Long>, ChatRoomDetailRepository {
}
