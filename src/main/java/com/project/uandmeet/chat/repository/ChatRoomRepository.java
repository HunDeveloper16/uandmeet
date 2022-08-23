package com.project.uandmeet.chat.repository;

import com.project.uandmeet.auth.UserDetailsImpl;
import com.project.uandmeet.chat.dto.ChatListMessageDto;
import com.project.uandmeet.chat.dto.ChatRoomResponseDto;
import com.project.uandmeet.chat.model.ChatMessage;
import com.project.uandmeet.chat.model.ChatRoom;
import com.project.uandmeet.chat.model.InvitedUsers;
import com.project.uandmeet.model.Board;
import com.project.uandmeet.model.Member;
import com.project.uandmeet.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//
//
//import com.project.uandmeet.auth.UserDetailsImpl;
//import com.project.uandmeet.chat.dto.ChatListMessageDto;
//import com.project.uandmeet.chat.dto.ChatRoomResponseDto;
//import com.project.uandmeet.chat.dto.UserDto;
//import com.project.uandmeet.chat.model.ChatMessage;
//import com.project.uandmeet.chat.model.ChatRoom;
//import com.project.uandmeet.chat.model.InvitedUsers;
//import com.project.uandmeet.chat.service.RedisSubscriber;
//import com.project.uandmeet.model.Board;
//import com.project.uandmeet.model.Member;
//import com.project.uandmeet.repository.BoardRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.redis.core.HashOperations;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//import org.springframework.data.redis.listener.ChannelTopic;
//import org.springframework.data.redis.listener.RedisMessageListenerContainer;
//import org.springframework.stereotype.Repository;
//import org.springframework.transaction.annotation.Transactional;
//
//import javax.annotation.PostConstruct;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Locale;
//import java.util.concurrent.TimeUnit;
//
//
//@RequiredArgsConstructor
//@Repository
//public class ChatRoomRepository {
//    // 채팅방(topic)에 발행되는 메시지를 처리할 Listner
//    private final RedisMessageListenerContainer redisMessageListener;
//    // 구독 처리 서비스
//    private final RedisSubscriber redisSubscriber;
//    private final ChatRoomJpaRepository chatRoomJpaRepository;
//    private final BoardRepository boardRepository;
//    private final InvitedUsersRepository invitedUsersRepository;
//    private final ChatMessageJpaRepository chatMessageJpaRepository;
//    private final StringRedisTemplate stringRedisTemplate; // StringRedisTemplate 사용
//    private static final String CHAT_ROOMS = "CHAT_ROOM";
//    private final RedisTemplate<String, Object> redisTemplate;
//    private HashOperations<String, String, ChatRoom> opsHashChatRoom;
//    private static ValueOperations<String, String> topics;
//
//    @PostConstruct
//    private void init() {
//        opsHashChatRoom = redisTemplate.opsForHash();
//        topics = stringRedisTemplate.opsForValue();
//    }
//
//    //내가 참여한 모든 채팅방 목록 조회
//    @Transactional
//    public ChatListMessageDto findAllRoom(Member member) {
//        List<InvitedUsers> invitedUsers = invitedUsersRepository.findAllByMemberId(member.getId());
//        List<ChatRoomResponseDto> chatRoomResponseDtoList = new ArrayList<>();
//        for (InvitedUsers invitedUser : invitedUsers) {
//            if (invitedUser.getReadCheck()) {
//                invitedUser.setReadCheck(false);
//                invitedUser.setReadCheckTime(LocalDateTime.now());
//            }
//            Board board = boardRepository.findById(invitedUser.getBoardId()).orElseThrow(
//                    () -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));
//            ChatMessage chatMessage = chatMessageJpaRepository.findTop1ByRoomIdOrderByCreatedAtDesc(invitedUser.getBoardId().toString());
//            ChatRoomResponseDto chatRoomResponseDto = new ChatRoomResponseDto();
//            if (chatMessage.getMessage().isEmpty()) {
//                chatRoomResponseDto.setLastMessage("파일이 왔어요😲");
//            } else {
//                chatRoomResponseDto.setLastMessage(chatMessage.getMessage());
//            }
//            LocalDateTime createdAt = chatMessage.getCreatedAt();
//            String createdAtString = createdAt.format(DateTimeFormatter.ofPattern("dd,MM,yyyy,HH,mm,ss", Locale.KOREA));
//
//            chatRoomResponseDto.setLastMessageTime(createdAtString);
//            chatRoomResponseDto.setBoardTitle(board.getTitle());
//            chatRoomResponseDto.setBoardId(board.getId());
//            chatRoomResponseDtoList.add(chatRoomResponseDto);
//
//        }
//        return new ChatListMessageDto(chatRoomResponseDtoList);
//    }
//
//    /**
//     * 채팅방 입장 : redis에 topic을 만들고 pub/sub 통신을 하기 위해 리스너를 설정한다.
//     */
//    public void enterChatRoom(String roomId) {
//        if (topics.get(roomId) == null) {
//            ChannelTopic topic = new ChannelTopic(roomId);
//            redisMessageListener.addMessageListener(redisSubscriber, topic);
//            topics.set(roomId, topic.toString());
//            redisTemplate.expire(roomId, 48, TimeUnit.HOURS);
//        } else {
//            String topicToString = topics.get(roomId);
//            ChannelTopic topic = new ChannelTopic(topicToString);
//            redisMessageListener.addMessageListener(redisSubscriber, topic);
//        }
//    }
//
//    /*
//     * 채팅방 생성 , 게시글 생성시 만들어진 postid를 받아와서 게시글 id로 사용한다.
//     */
//    @Transactional
//    public void createChatRoom(Board board, UserDetailsImpl userDetails) {
//        ChatRoom chatRoom = ChatRoom.create(board, userDetails);
//        opsHashChatRoom.put(CHAT_ROOMS, chatRoom.getRoomId(), chatRoom); // redis 저장
//        redisTemplate.expire(CHAT_ROOMS, 48, TimeUnit.HOURS);
//        chatRoomJpaRepository.save(chatRoom); // DB 저장
//    }
//
//    public static ChannelTopic getTopic(String roomId) {
//        String topicToString = topics.get(roomId);
//        return new ChannelTopic(topicToString);
//    }
//}
@RequiredArgsConstructor
@Service
public class ChatRoomRepository {
    // Redis
    private static final String CHAT_ROOMS = "CHAT_ROOM";
    private final RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, String, ChatRoom> opsHashChatRoom;

    @PostConstruct
    private void init() {
        opsHashChatRoom = redisTemplate.opsForHash();
    }

    // 모든 채팅방 조회
    public List<ChatRoom> findAllRoom() {
        return opsHashChatRoom.values(CHAT_ROOMS);
    }

    // 특정 채팅방 조회
    public ChatRoom roomInfo(String id) {
        return opsHashChatRoom.get(CHAT_ROOMS, id);
    }

    // 채팅방 생성 : 서버간 채팅방 공유를 위해 redis hash에 저장한다.
    public ChatRoom createChatRoom(Board board, UserDetailsImpl userDetails) {
        ChatRoom chatRoom = ChatRoom.create(board, userDetails);
        opsHashChatRoom.put(CHAT_ROOMS, chatRoom.getRoomId(), chatRoom);
        return chatRoom;
    }
}