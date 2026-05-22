package com.example.websocket_chat.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.websocket_chat.domain.chat.handler.ChatHandler;

@Configuration      // 서버 인프라 설정 파일임을 명시
@EnableWebSocket    // 웹소켓 아키텍쳐 기능 활성화
public class WebSocketConfig implements WebSocketConfigurer{

    private final ChatHandler chatHandler;
    // 생성자 주입
    public WebSocketConfig(ChatHandler chatHandler){
        this.chatHandler = chatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        // 누군가 브라우저에서 'ws://localhost:8080/ws/chat' 주소로 웹소켓 연결을 요청하면,
        // chatHandler가 그 연결을 낚아채서 처리

        registry.addHandler(chatHandler, "/ws/chat")
            .setAllowedOrigins("*");
    }
    
}
