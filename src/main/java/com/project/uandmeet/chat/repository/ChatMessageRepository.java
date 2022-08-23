package com.project.uandmeet.chat.repository;


import com.project.uandmeet.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    ChatMessage findByRoomId(String roomId);
}
