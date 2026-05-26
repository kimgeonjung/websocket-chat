package com.example.websocket_chat.domain.chat.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.websocket_chat.domain.chat.entity.ChatLog;

@Service
public class GeminiService {
    
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.url}") private String apiUrl;
    @Value("${gemini.api.key}") private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMepper = new ObjectMapper();

    public void analyze(List<ChatLog> chatLogs) {
        
        if(chatLogs == null || chatLogs.isEmpty()) return;

        try {
            StringBuilder sb = new StringBuilder();
            for(ChatLog logEntity : chatLogs){
                sb.append("[").append(logEntity.getUsername()).append("]")
                .append(logEntity.getMessage()).append("\n");
            }
            String rawChatData = sb.toString();

            String prompt = "너는 실시간 대용량 채팅방의 인프라 모니터링 AI 엔진이야.\n" +
                    "아래 텍스트는 지난 3분간 유저들이 실시간으로 쏟아낸 날것의 채팅 로그 데이터다.\n" +
                    "이 데이터를 정밀 분석해서 다음 3가지 항목을 칼같이 정리해서 한국어 반말로 대답해라.\n\n" +
                    "1. 주요 대화 주제 핵심 요약 (3줄 이내)\n" +
                    "2. 현재 채팅방 유저들의 주된 감정 상태 분석 (예: 평화로움, 화남, 흥분, 지루함 등)\n" +
                    "3. 가장 많이 언급된 핵심 핫 키워드 Top 3\n\n" +
                    "--- [실시간 채팅 로그] ---\n" +
                    rawChatData;

            ObjectNode rootNode = objectMepper.createObjectNode();
            ArrayNode contentsArray = rootNode.putArray("contents");
            ObjectNode contentNode = contentsArray.addObject();
            ArrayNode partsArray = contentNode.putArray("parts");
            ObjectNode partNode = partsArray.addObject();
            partNode.put("text", prompt);

            String requestBody = objectMepper.writeValueAsString(rootNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String finalUrl = apiUrl + "?key=" + apiKey;
            log.info("구글 Gemini 2.5 Flash 요약 분석 요청 송신 중...");
            log.info("현재 주입된 API 키의 총 길이: {}자", apiKey != null ? apiKey.length() : 0);
            log.info("호출하는 최종 URL: {}", finalUrl);

            ResponseEntity<String> response = restTemplate.exchange(finalUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK){
                String responseBody = response.getBody();
                var jsonResponse = objectMepper.readTree(responseBody);
                String aiResult = jsonResponse.path("candidates").get(0)
                .path("content").path("parts").get(0).path("text")
                .asString();

                log.info("\n=== [Gemini AI가 분석한 지난 3분간의 대화 내용] === \n{}", aiResult);
            } else {
                log.warn("구글 서버 상태 이상 응답 코드: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Gemini AI 연동 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
}
