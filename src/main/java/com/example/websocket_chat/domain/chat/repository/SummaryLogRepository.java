package com.example.websocket_chat.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.websocket_chat.domain.chat.entity.SummaryLog;

public interface SummaryLogRepository extends JpaRepository<SummaryLog, Long>{
    
}
