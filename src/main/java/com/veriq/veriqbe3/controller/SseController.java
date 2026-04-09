package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor

public class SseController {
    private final SseEmitterService sseEmitterService;

    // 프론트엔드가 연결을 맺는 API
    @GetMapping(value = "/api/scan/subscribe/{guestUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String guestUuid) {
        return sseEmitterService.subscribe(guestUuid);
    }
}
