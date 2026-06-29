package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Portföy değerinin günlük snapshot'ı.
 * Performans grafiği ve geçmiş analiz için kullanılır.
 */
@Data
@Entity
@Table(name = "portfolio_snapshot_v2", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"snapshotTimestamp", "source"})
})
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Snapshot tarihi ve saati
    @Column(nullable = false)
    private LocalDateTime snapshotTimestamp;

    // Kaynak: "PORTFOLIO_1", "EXTERNAL_2", "COMPOSITE"
    @Column(nullable = false)
    private String source;

    // Toplam değer — USD cinsinden
    private Double totalValueUsd = 0.0;

    // Toplam değer — TRY cinsinden
    private Double totalValueTry = 0.0;

    // Günlük getiri yüzdesi
    private Double dailyReturnPercent = 0.0;

    // Kümülatif getiri yüzdesi (başlangıçtan itibaren)
    private Double cumulativeReturnPercent = 0.0;
}
