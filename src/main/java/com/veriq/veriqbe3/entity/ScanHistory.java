package com.veriq.veriqbe3.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_history")
@Getter
@Setter
public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String guestId;

    @Column(length = 1000)
    private String originalUrl;

    private LocalDateTime scannedAt;

    @PrePersist
    public void prePersist() {
        this.scannedAt = LocalDateTime.now();
    }
}