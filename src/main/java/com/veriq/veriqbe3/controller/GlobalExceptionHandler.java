package com.veriq.veriqbe3.controller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 🚨 1. JSON 형태가 깨졌거나, 날짜/타입 포맷이 안 맞을 때 (파싱 에러)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("==================================================");
        log.error("🔥 [JSON 파싱 에러] 파이썬이 보낸 데이터를 자바 DTO로 바꿀 수 없습니다!");
        log.error("범인(상세 이유): {}", e.getMessage()); // 👈 여기서 어떤 필드가 문제인지 알려줍니다!
        log.error("==================================================");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JSON 형식 오류");
    }

    // 🚨 2. @Valid 검증 실패 (예: @NotBlank 필드인 guestUuid가 안 넘어왔을 때)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(MethodArgumentNotValidException e) {
        log.error("==================================================");
        log.error("🔥 [DTO 검증 에러] 필수 값이 누락되었거나 조건에 맞지 않습니다!");
        log.error("범인(상세 이유): {}", e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        log.error("==================================================");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("필수 값 누락");
    }
}
