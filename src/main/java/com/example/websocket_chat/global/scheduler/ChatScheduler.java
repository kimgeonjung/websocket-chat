package com.example.websocket_chat.global.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.websocket_chat.domain.chat.service.ChatSummaryService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ChatScheduler {
        private final ChatSummaryService chatSummaryService;

    public ChatScheduler(ChatSummaryService chatSummaryService){
        this.chatSummaryService = chatSummaryService;
    }

    @Scheduled(cron = "0 */3 * * * *")
    public void flushChatToDb() { 
        log.info("3분 스케줄러 작동");
        
        // 실제 로직은 서비스에서 전부 처리함
        chatSummaryService.executeTask();
        
        log.info("3분 스케줄러 작업 종료");
    }
}
