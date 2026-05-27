package com.example.websocket_chat.domain.chat.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.websocket_chat.domain.chat.entity.ChatLog;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, Long>{

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ChatLog c WHERE c.createdAt <= :targetTime")
    void deleteOldCatLogs(@Param("targetTime") LocalDateTime targetTime);
}