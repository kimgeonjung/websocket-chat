package com.example.websocket_chat.domain.chat.handler;

import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.websocket_chat.domain.chat.dto.ChatMessage;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class ChatHandler extends TextWebSocketHandler {
  
    // 사용자 세션. java 21 멀티스레드 환경을 위해 CopyOnWriteArrayList 사용
    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMepper = new ObjectMapper();
    private final StringRedisTemplate redisTemplate;

    public ChatHandler(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    // 사용자 입장 후 웹 소켓 연결 성공시 (Handshake 성공 시점. http -> ws 업그레이드 성공) 실행되는 메서드
    @Override
    public void afterConnectionEstablished(WebSocketSession session)throws Exception{
        sessions.add(session);
        log.info("새로운 사용자 입장. 현재 사용자 수: {}", sessions.size());
    }

    // 사용자가 채팅을 쳐서 서버로 메시지를 보냈을 때 실행되는 메서드
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception{
        String payload = message.getPayload(); // 사용자가 보낸 채팅 내용 글자 그대로 가져오기
        
        ChatMessage chatMessage = objectMepper.readValue(payload, ChatMessage.class);

        /*  Redis Ingestion Core
            Resid Key: "streamer:chat:room"
            Score: Epoch Timestamp (Long 타입의 밀리초 타임스탬프. 정렬 기준점)
            Value: 클라이언트가 보낸 순수 JSON 문자열 데이터 그대로 적재
         */
        String redisKey = "streamer:chat:room";
        double score = (double) chatMessage.getTime();

        redisTemplate.opsForZSet().add(redisKey, payload, score);
        log.info("Redis 캐싱 완료 >> Score: {}",chatMessage.getTime());

        log.info(String.format("%s: %s", 
            chatMessage.getUser(), 
            chatMessage.getMsg())
        );
        
        // 전 시청자 브로드캐스팅 파이프라인 가동 
        for(WebSocketSession webSocketSession : sessions){
            if(webSocketSession.isOpen()){ // 연결 살아있는 시청자에게만 보냄
                webSocketSession.sendMessage(new TextMessage(payload));
            }
        }
    }

    //  사용자가 브라우저를 닫거나 나가서 웹소켓 연결이 끊어지면 실행되는 메서드
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception{
        sessions.remove(session);
        log.info("사용자 퇴장. 현재 사용자 수: {}", sessions.size());
    }
}
