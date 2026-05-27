package com.example.websocket_chat.domain.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "summary_log")
@Getter
public class SummaryLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summaryContent;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public SummaryLog(){}

    public SummaryLog(String summaryContent){
        this.summaryContent = summaryContent;
        this.createdAt = LocalDateTime.now();
    }
}
