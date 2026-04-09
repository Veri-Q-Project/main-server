package com.veriq.veriqbe3.service;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Service

public class SseEmitterService {
    // 유저의 guestUuid와 연결된 통로(Emitter)를 저장하는 맵
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 1. 프론트엔드가 처음 연결할 때 통로를 만들어줌
    public SseEmitter subscribe(String guestUuid) {
        SseEmitter emitter = new SseEmitter(60 * 1000L); // 60초 타임아웃
        emitters.put(guestUuid, emitter);

        // 통로가 끊기면 맵에서 삭제
        emitter.onCompletion(() -> emitters.remove(guestUuid));
        emitter.onTimeout(() -> emitters.remove(guestUuid));
        emitter.onError((e) -> emitters.remove(guestUuid));

        return emitter;
    }

    // 2. 파이썬 분석이 끝났을 때 프론트로 결과를 쏴주는 메서드!
    public void sendResultToClient(String guestUuid, Object resultData) {
        SseEmitter emitter = emitters.get(guestUuid);
        if (emitter != null) {
            try {
                // "analysis-complete"라는 이름으로 결과 데이터를 프론트로 발송!
                emitter.send(SseEmitter.event()
                        .name("analysis-complete")
                        .data(resultData));
            } catch (IOException e) {
                emitters.remove(guestUuid);
            }
        }
    }
}
