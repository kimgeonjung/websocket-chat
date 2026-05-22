package com.example.websocket_chat.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.websocket_chat.domain.chat.entity.ChatLog;

@Repository
public interface ChatRepository extends JpaRepository<ChatLog, Long>{
}