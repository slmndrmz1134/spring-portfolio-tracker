package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "portfolio")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kullanıcının verdiği isim: "Ana Portföy", "Emeklilik" vs.
    @Column(nullable = false)
    private String name;

    // Platform adı (Midas, Akbank, vb. Opsiyonel)
    private String platform;

    // Kullanıcının manuel girdiği geçmiş YTD performansı (Opsiyonel)
    private Double manualYtd;

    // Bir portföyün içinde birden fazla varlık olabilir
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "portfolio_id")
    private List<Asset> assets = new ArrayList<>();
}