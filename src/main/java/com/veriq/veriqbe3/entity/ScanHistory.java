package com.veriq.veriqbe3.entity;

import com.veriq.veriqbe3.domain.SchemeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_history")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String guestUuid;
    @Column(length = 1000)
    private String originalUrl;
    @Column(length = 1000)
    private String extractedData;
    @Enumerated(EnumType.STRING)        //어노테이션으로 enum의 숫자가 아닌 String 저장
    private SchemeType schemeType;
    private LocalDateTime scannedAt;

    @PrePersist //첫 insert직전 실행
    public void prePersist() {
        this.scannedAt = LocalDateTime.now();
    }
}