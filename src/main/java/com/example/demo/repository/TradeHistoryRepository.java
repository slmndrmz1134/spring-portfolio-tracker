package com.example.demo.repository;

import com.example.demo.entity.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {

    // Portföye ait işlem geçmişi (en yeniden eskiye)
    List<TradeHistory> findByPortfolioIdOrderByTradeDateDesc(Long portfolioId);

    // Tüm işlem geçmişi (en yeniden eskiye)
    List<TradeHistory> findAllByOrderByTradeDateDesc();

    // Sadece satış işlemleri
    List<TradeHistory> findByTradeTypeOrderByTradeDateDesc(String tradeType);

    // Toplam realized P&L (USD)
    @Query("SELECT COALESCE(SUM(t.profitLossUsd), 0) FROM TradeHistory t WHERE t.tradeType = 'SELL'")
    Double getTotalRealizedProfitLossUsd();

    // Toplam realized P&L (TRY)
    @Query("SELECT COALESCE(SUM(t.profitLossTry), 0) FROM TradeHistory t WHERE t.tradeType = 'SELL'")
    Double getTotalRealizedProfitLossTry();
}
