package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Tüm alış/satış işlemlerinin geçmişini tutar.
 * Realized (gerçekleşmiş) kâr/zarar takibi için kullanılır.
 */
@Data
@Entity
@Table(name = "trade_history")
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Hangi portföye ait
    private Long portfolioId;

    // Varlık bilgileri
    @Column(nullable = false)
    private String symbol;

    private String assetName;

    // İşlem türü: "BUY", "SELL"
    @Column(nullable = false)
    private String tradeType;

    // Adet
    @Column(nullable = false)
    private Double quantity;

    // Birim fiyat — orijinal para biriminde
    private Double pricePerUnit;

    // Birim fiyat — USD cinsinden
    private Double pricePerUnitUsd;

    // Toplam tutar — USD cinsinden
    private Double totalAmountUsd;

    // Toplam tutar — TRY cinsinden
    private Double totalAmountTry;

    // Satıştan elde edilen kâr/zarar — USD ($)
    private Double profitLossUsd = 0.0;

    // Satıştan elde edilen kâr/zarar — TRY (₺)
    private Double profitLossTry = 0.0;

    // İşlem anındaki döviz kuru (para birimi → TRY)
    private Double exchangeRate;

    // İşlem anındaki USD/TRY kuru
    private Double usdTryRate;

    // Para birimi
    private String currency;

    // İşlem tarihi
    @Column(nullable = false)
    private LocalDateTime tradeDate;

    @PrePersist
    public void setTradeDate() {
        if (this.tradeDate == null) {
            this.tradeDate = LocalDateTime.now();
        }
    }
}
