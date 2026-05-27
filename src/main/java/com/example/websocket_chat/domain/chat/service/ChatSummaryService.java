package com.example.websocket_chat.domain.chat.service;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.websocket_chat.domain.chat.entity.ChatLog;
import com.example.websocket_chat.domain.chat.entity.SummaryLog;
import com.example.websocket_chat.domain.chat.repository.ChatLogRepository;
import com.example.websocket_chat.domain.chat.repository.SummaryLogRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class ChatSummaryService {
    private final StringRedisTemplate redisTemplate;
    private final ChatLogRepository chatLogRepository;
    private final SummaryLogRepository summaryLogRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String redisKey = "streamer:chat:room";
    private final String dlqKey = "streamer:chat:dlq";

    public ChatSummaryService(StringRedisTemplate redisTemplate, 
                              ChatLogRepository chatLogRepository, 
                              SummaryLogRepository summaryLogRepository, 
                              GeminiService geminiService) {
        this.redisTemplate = redisTemplate;
        this.chatLogRepository = chatLogRepository;
        this.summaryLogRepository = summaryLogRepository;
        this.geminiService = geminiService;
    }

    @Transactional
    public void executeTask() {
        // 1. Redis 스캔 시작 지점 확인 및 고정
        Set<ZSetOperations.TypedTuple<String>> latestElement = redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, 0);

        if (latestElement == null || latestElement.isEmpty()) {
            log.info("Redis 버퍼가 비어있습니다. 작업을 스킵합니다.");
            return;
        }

        double maxScore = latestElement.iterator().next().getScore();
        Set<String> chatLogs = redisTemplate.opsForZSet().rangeByScore(redisKey, 0, maxScore);

        if (chatLogs == null || chatLogs.isEmpty()) {
            return;
        }

        List<ChatLog> dbInsertList = new ArrayList<>();
        List<String> dlqList = new ArrayList<>();
        int totalCount = chatLogs.size();
        int failedCount = 0;

        // 2. JSON 파싱 및 데이터 정제
        for (String chatStr : chatLogs) {
            try {
                JsonNode jsonNode = objectMapper.readTree(chatStr);
                String user = jsonNode.get("user").asString();
                String msg = jsonNode.get("msg").asString();
                long timestamp = jsonNode.get("time").asLong();

                LocalDateTime createdAt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), 
                    ZoneId.of("Asia/Seoul")
                );

                dbInsertList.add(new ChatLog(user, msg, createdAt));
            } catch (Exception e) {
                failedCount++;
                log.error("JSON 파싱 실패 (건너뜀): {}", e.getMessage());
                dlqList.add(chatStr);
            }
        }

        // 3. 깨진 데이터 DLQ 처리
        if (!dlqList.isEmpty()) {
            for (String errLog : dlqList) {
                redisTemplate.opsForList().rightPush(dlqKey, errLog);
            }
            log.info("깨진 데이터 {}건을 {}로 이전", dlqList.size(), dlqKey);
        }

        // 4. RDB 1차 백업 및 AI 파이프라인 진행
        if (!dbInsertList.isEmpty()) {
            LocalDateTime executionTime = LocalDateTime.now(); // 요약 및 클린업 기준 시간 고정
            
            try {
                chatLogRepository.saveAll(dbInsertList);
                log.info("'chat_log' 테이블에 {}건 백업 완료. (파싱 실패: {}건)", dbInsertList.size(), failedCount);    

                // ---- 안전장치 격리 구간: AI 요약 및 데이터 클린업 ----
                try {
                    log.info("제미나이 서버 요약 요청 중...");
                    String summaryResult = geminiService.analyze(dbInsertList); 
                    log.info("요약본 생성 성공.");

                    // 요약 데이터 요약 로그 테이블에 인서트
                    SummaryLog summaryLog = new SummaryLog(summaryResult);
                    summaryLogRepository.save(summaryLog);
                    log.info("'summary_logs' 테이블에 요약본 저장 완료. ID: {}", summaryLog.getId());

                    // 요약이 완벽하게 끝났으니 원본 데이터 벌크 삭제 (오타 수정 완료: createdAt)
                    log.info("{} 이전의 원본 채팅 로그 삭제 프로세스 가동...", executionTime);
                    chatLogRepository.deleteOldCatLogs(executionTime);
                    log.info("원본 채팅 로그 삭제 성공. 인프라 공간 최적화 완료.");

                    // TODO: 추후 치지직 공지 발송 API 연동 자리

                } catch (Exception ae) {
                    log.error("요약 중 에러 발생. 원본 데이터 유실 방지를 위해 지우지 않고 보존합니다. 에러: {}", ae.getMessage(), ae);
                }
                // ---------------------------------------------------
                
            } catch (Exception e) {
                log.error("데이터베이스 장애 발생: {}", e.getMessage(), e);
                emergencyDumpTofile(chatLogs);
                return; // RDB 저장 자체가 터졌으면 Redis 버퍼를 비우면 안 되므로 즉시 중단
            }
        }

        // 5. 파이프라인 정상 종료 시 복사해 갔던 지점까지 Redis 버퍼 청소
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, maxScore);
        log.info("Redis 버퍼 청소 완료 {}건. 전 과정 완료.", totalCount);
    }

    private void emergencyDumpTofile(Set<String> rawLogs) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dumpFileName = "logs/emergency_dump_" + timestamp + ".json";

        try (PrintWriter writer = new PrintWriter(new FileWriter(dumpFileName, true))) {
            for (String logStr : rawLogs) {
                writer.println(logStr);
            }
            log.info("디스크 유실 방지 파일 백업 성공 -> {}", dumpFileName);
        } catch (Exception e) {
            log.error("로컬 디스크 파일 쓰기 실패 {}", e.getMessage());
        }
    }
}
