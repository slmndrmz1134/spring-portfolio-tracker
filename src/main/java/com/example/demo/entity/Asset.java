package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

// Bu sınıf veritabanındaki "asset" tablosunu temsil eder.
// Her nesne = portföydeki bir varlık (hisse, döviz, nakit)
@Data           // Lombok: getter, setter, toString otomatik oluşturur
@Entity         // JPA: bu sınıf bir veritabanı tablosudur
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // ID otomatik artar: 1, 2, 3...
    private Long id;

    // Hissenin borsa kodu: THYAO, AAPL, USD vs.
    @Column(nullable = false)
    private String symbol;

    // Kullanıcının göreceği isim: "Türk Hava Yolları"
    @Column(nullable = false)
    private String name;

    // Varlık türü: STOCK (hisse), CURRENCY (döviz), CASH (nakit TL)
    @Column(nullable = false)
    private String type;
    
    // Para birimi: TRY, USD, EUR
    @Column(nullable = false)
    private String currency = "TRY";

    // Kullanıcının elinde kaç adet var
    @Column(nullable = false)
    private Double quantity;

    // Kullanıcının aldığı fiyat (maliyet takibi için)
    private Double purchasePrice;

    // Alış tarihi (performans hesaplama için)
    private LocalDate purchaseDate;
}