package com.project.uandmeet.chat.service;

import com.project.uandmeet.chat.dto.ChatMessageDto;
import com.project.uandmeet.chat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisPublisher {

    private static final String CHAT_MESSAGE = "CHAT_MESSAGE"; // 채팅룸에 메세지들을 저장
    private final RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, String, List<ChatMessageDto>> opsHashChatMessage; // Redis 의 Hashes 사용
    @PostConstruct
    private void init() {
        opsHashChatMessage = redisTemplate.opsForHash();
    }


    // websocket 에서 받아온 메세지를 convertAndsend를 통하여 Redis의 메세지 리스너로 발행
    // redisrepository 를 이용해 저장
    public void publishsave( ChatMessageDto messageDto){


        //chatMessageDto 를 redis 에 저장하기 위하여 직렬화 한다.
        redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(ChatMessage.class));
        String projectId = messageDto.getBoardId();
        //redis에 저장되어있는 리스트를 가져와, 새로 받아온 chatmessageDto를 더하여 다시 저장한다.
        List<ChatMessageDto> chatMessageList = opsHashChatMessage.get(CHAT_MESSAGE, projectId);

        //가져온 List가 null일때 새로운 리스트를 만든다 == 처음에 메세지를 저장할경우 리스트가 없기때문에.
        if (chatMessageList == null) {
            chatMessageList = new ArrayList<>();
        }

        chatMessageList.add(0,messageDto);

        //redis 의 hashes 자료구조 ---->> key : CHAT_MESSAGE , field : boardId, value : chatMessageList
        opsHashChatMessage.put(CHAT_MESSAGE, projectId, chatMessageList);


        redisTemplate.expire(CHAT_MESSAGE,10, TimeUnit.SECONDS);

        redisTemplate.convertAndSend("board", messageDto);


    }

}