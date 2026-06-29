package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bileşik portföy performansı hesaplama motoru.
 * Tüm dahili portföyler + harici hesapların ağırlıklı performansını hesaplar.
 *
 * Periyotlar: Günlük, Haftalık, Aylık, 3 Aylık, 6 Aylık, 1 Yıllık, YTD, Tümü
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final StockService stockService;

    // ─── Snapshot Yönetimi ───

    /**
     * Günlük snapshot al — Tüm portföylerin + harici hesapların + bileşik değerin snapshot'ını kaydeder.
     * Sayfa yüklendiğinde çağrılır; günde 1 kez kaydeder.
     */
    // Her 15 dakikada bir otomatik çalışır (900000 ms)
    @Scheduled(fixedRate = 900000)
    public void takePeriodicSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        
        // Sadece piyasa açıkken (veya sürekli) snapshot alıyoruz.
        // Her 15 dakikada yeni bir nokta ekler
        takeFullSnapshot(now);
    }

    private void takeFullSnapshot(LocalDateTime date) {
        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        double compositeTotalUsd = 0.0;
        double compositeTotalTry = 0.0;

        // 1. Dahili portföyler
        List<Portfolio> portfolios = portfolioRepository.findAll();
        for (Portfolio portfolio : portfolios) {
            double portfolioTry = calculatePortfolioValueTry(portfolio);
            double portfolioUsd = usdTryRate > 0 ? portfolioTry / usdTryRate : 0;

            saveSnapshot(date, "PORTFOLIO_" + portfolio.getId(), portfolioUsd, portfolioTry);
            compositeTotalUsd += portfolioUsd;
            compositeTotalTry += portfolioTry;
        }

        // 2. Bileşik snapshot
        saveSnapshot(date, "COMPOSITE", compositeTotalUsd, compositeTotalTry);
    }

    private void saveSnapshot(LocalDateTime date, String source, double usd, double tryVal) {
        PortfolioSnapshot snapshot = new PortfolioSnapshot();
        snapshot.setSnapshotTimestamp(date);
        snapshot.setSource(source);
        snapshot.setTotalValueUsd(usd);
        snapshot.setTotalValueTry(tryVal);

        snapshotRepository.save(snapshot);
    }

    // ─── Portföy Değer Hesaplama ───

    public double calculatePortfolioValueTry(Portfolio portfolio) {
        double total = 0.0;
        for (Asset asset : portfolio.getAssets()) {
            Double currentPrice = stockService.getPrice(asset.getSymbol());
            double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
            total += currentPrice * exchangeRate * asset.getQuantity();
        }
        return total;
    }

    public double calculatePortfolioValueUsd(Portfolio portfolio) {
        double totalTry = calculatePortfolioValueTry(portfolio);
        double usdTryRate = stockService.getUsdTryRate();
        return usdTryRate > 0 ? totalTry / usdTryRate : 0;
    }

    // ─── Bileşik Performans Hesaplama ───

    /**
     * Belirli periyot için bileşik performans hesapla.
     * period: "1D", "1W", "1M", "3M", "6M", "1Y", "YTD", "ALL"
     */
    public Map<String, Object> calculateCompositePerformance(String period) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = getStartDateForPeriodTime(period, endDate);

        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        // Mevcut toplam değerleri hesapla
        double totalCurrentUsd = 0.0;
        double totalCurrentTry = 0.0;
        List<Map<String, Object>> breakdown = new ArrayList<>();

        // Dahili portföyler
        List<Portfolio> portfolios = portfolioRepository.findAll();
        for (Portfolio portfolio : portfolios) {
            double valTry = calculatePortfolioValueTry(portfolio);
            double valUsd = usdTryRate > 0 ? valTry / usdTryRate : 0;
            totalCurrentUsd += valUsd;
            totalCurrentTry += valTry;

            // Portföy bazında performans
            double portfolioPerf = calculateSourcePerformance("PORTFOLIO_" + portfolio.getId(), startDate, endDate, valUsd);

            if (("YTD".equals(period) || "ALL".equals(period)) && portfolio.getManualYtd() != null) {
                double compounded = ((1 + (portfolio.getManualYtd() / 100.0)) * (1 + (portfolioPerf / 100.0))) - 1.0;
                portfolioPerf = compounded * 100.0;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", portfolio.getName());
            item.put("type", "PORTFOLIO");
            item.put("valueUsd", valUsd);
            item.put("valueTry", valTry);
            item.put("performance", portfolioPerf);
            breakdown.add(item);
        }

        // Ağırlıklı bileşik performans = Σ (değer_i / toplam_değer) × performans_i
        double compositePerformance = 0.0;
        if (totalCurrentUsd > 0) {
            for (Map<String, Object> item : breakdown) {
                double weight = (Double) item.get("valueUsd") / totalCurrentUsd;
                double perf = (Double) item.get("performance");
                item.put("weight", weight * 100.0); // Ağırlık yüzdesi
                item.put("contribution", weight * perf); // Katkı
                compositePerformance += weight * perf;
            }
        }

        result.put("period", period);
        result.put("compositePerformance", compositePerformance);
        result.put("totalValueUsd", totalCurrentUsd);
        result.put("totalValueTry", totalCurrentTry);
        result.put("usdTryRate", usdTryRate);
        result.put("breakdown", breakdown);

        return result;
    }

    private double calculateSourcePerformance(String source, LocalDateTime start, LocalDateTime end, double currentValue) {
        List<PortfolioSnapshot> snapshots = snapshotRepository
            .findBySourceAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc(source, start, end);

        if (snapshots.isEmpty()) {
            return 0.0;
        }

        double startValue = snapshots.get(0).getTotalValueUsd();
        if (startValue <= 0) return 0.0;

        return ((currentValue - startValue) / startValue) * 100.0;
    }

    // ─── Grafik Verileri ───

    /**
     * Performans grafiği için zaman serisi verileri döndürür.
     * Her veri noktası: {date, value, returnPercent}
     */
    public Map<String, Object> getPerformanceChartData(String period) {
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = getStartDateForPeriodTime(period, endDate);

        List<PortfolioSnapshot> snapshots = snapshotRepository
            .findBySourceAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc("COMPOSITE", startDate, endDate);

        // Filtreleme (Aggregation): Zaman periyoduna göre verileri seyrelt
        List<PortfolioSnapshot> filteredSnapshots = filterSnapshotsByPeriod(snapshots, period);

        List<String> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Double> returns = new ArrayList<>();

        double firstValue = 0;
        if (!filteredSnapshots.isEmpty()) {
            firstValue = filteredSnapshots.get(0).getTotalValueUsd();
        }

        for (PortfolioSnapshot s : filteredSnapshots) {
            dates.add(s.getSnapshotTimestamp().toString());
            values.add(s.getTotalValueUsd());
            if (firstValue > 0) {
                returns.add(((s.getTotalValueUsd() - firstValue) / firstValue) * 100.0);
            } else {
                returns.add(0.0);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("values", values);
        result.put("returns", returns);
        result.put("period", period);
        return result;
    }

    // ─── Günlük Değişim ───

    public double getDailyChange() {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime yesterday = today.minusDays(1);

        Optional<PortfolioSnapshot> currentOpt = snapshotRepository
            .findTopBySourceOrderBySnapshotTimestampDesc("COMPOSITE");
        // Dünün en yakın snapshot'ını bul
        List<PortfolioSnapshot> yesterdaySnapshots = snapshotRepository
            .findBySourceAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc("COMPOSITE", yesterday.minusHours(12), yesterday.plusHours(12));

        if (currentOpt.isEmpty() || yesterdaySnapshots.isEmpty()) {
            return 0.0;
        }

        double currentVal = currentOpt.get().getTotalValueUsd();
        // Düne ait en son/yakın noktayı alalım (basitlik için ilkini alıyoruz)
        double yesterdayVal = yesterdaySnapshots.get(0).getTotalValueUsd();

        if (yesterdayVal <= 0) return 0.0;
        return ((currentVal - yesterdayVal) / yesterdayVal) * 100.0;
    }

    // ─── Realized P&L ───

    public Double getTotalRealizedPLUsd() {
        return tradeHistoryRepository.getTotalRealizedProfitLossUsd();
    }

    public Double getTotalRealizedPLTry() {
        return tradeHistoryRepository.getTotalRealizedProfitLossTry();
    }

    // ─── En İyi / En Kötü Performans ───

    public Map<String, Object> getTopPerformers() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<Map<String, Object>> allAssets = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            for (Asset asset : portfolio.getAssets()) {
                if ("CURRENCY".equals(asset.getType()) && "TRY".equals(asset.getSymbol())) continue;

                Double currentPrice = stockService.getPrice(asset.getSymbol());
                double purchasePrice = asset.getPurchasePrice() != null ? asset.getPurchasePrice() : currentPrice;

                double perfPercent = 0;
                if (purchasePrice > 0) {
                    perfPercent = ((currentPrice - purchasePrice) / purchasePrice) * 100.0;
                }

                Map<String, Object> assetInfo = new LinkedHashMap<>();
                assetInfo.put("symbol", asset.getSymbol());
                assetInfo.put("name", asset.getName());
                assetInfo.put("performance", perfPercent);
                assetInfo.put("currentPrice", currentPrice);
                assetInfo.put("currency", asset.getCurrency());
                allAssets.add(assetInfo);
            }
        }

        // Performansa göre sırala
        allAssets.sort((a, b) -> Double.compare((Double) b.get("performance"), (Double) a.get("performance")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("best", allAssets.stream().limit(3).collect(Collectors.toList()));
        result.put("worst", allAssets.stream()
            .sorted((a, b) -> Double.compare((Double) a.get("performance"), (Double) b.get("performance")))
            .limit(3).collect(Collectors.toList()));

        return result;
    }

    // ─── Portföy Dağılımı (Pasta Grafik) ───

    public Map<String, Object> getPortfolioAllocation() {
        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        List<Map<String, Object>> items = new ArrayList<>();
        double grandTotal = 0.0;

        // Dahili portföyler
        List<Portfolio> portfolios = portfolioRepository.findAll();
        for (Portfolio portfolio : portfolios) {
            double valTry = calculatePortfolioValueTry(portfolio);
            double valUsd = valTry / usdTryRate;
            grandTotal += valUsd;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", portfolio.getName());
            item.put("valueUsd", valUsd);
            item.put("type", "PORTFOLIO");
            items.add(item);
        }

        // Yüzdeleri hesapla
        for (Map<String, Object> item : items) {
            double val = (Double) item.get("valueUsd");
            item.put("percentage", grandTotal > 0 ? (val / grandTotal) * 100.0 : 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalUsd", grandTotal);
        result.put("totalTry", grandTotal * usdTryRate);
        return result;
    }

    // ─── Yardımcı Metotlar ───

    private LocalDateTime getStartDateForPeriodTime(String period, LocalDateTime endDate) {
        return switch (period) {
            case "1D" -> endDate.minusDays(1);
            case "1W" -> endDate.minusWeeks(1);
            case "1M" -> endDate.minusMonths(1);
            case "3M" -> endDate.minusMonths(3);
            case "6M" -> endDate.minusMonths(6);
            case "1Y" -> endDate.minusYears(1);
            case "YTD" -> LocalDateTime.of(endDate.getYear(), Month.JANUARY, 1, 0, 0);
            case "ALL" -> endDate.minusYears(10); // Tüm zamanlar
            default -> endDate.minusMonths(1);
        };
    }

    // 1D: tüm noktalar. 1W/1M: Günlük 1 nokta. 3M/6M/1Y: Haftalık/Aylık 1 nokta.
    private List<PortfolioSnapshot> filterSnapshotsByPeriod(List<PortfolioSnapshot> snapshots, String period) {
        if (snapshots.isEmpty()) return snapshots;
        
        if ("1D".equals(period)) {
            return snapshots; // 15 dakikada 1 nokta (günde ~96 nokta)
        }
        
        List<PortfolioSnapshot> filtered = new ArrayList<>();
        // Sadece gün kapanışlarını (veya her günün son noktasını) alalım
        String lastDateKey = "";
        
        for (PortfolioSnapshot s : snapshots) {
            String dateKey;
            if ("1W".equals(period) || "1M".equals(period)) {
                // Gün bazında filtrele
                dateKey = s.getSnapshotTimestamp().toLocalDate().toString();
            } else {
                // Ay bazında veya hafta bazında filtrele (basitçe her ayın ilk/son günü)
                dateKey = s.getSnapshotTimestamp().getYear() + "-" + s.getSnapshotTimestamp().getMonthValue();
            }
            
            if (!dateKey.equals(lastDateKey)) {
                filtered.add(s);
                lastDateKey = dateKey;
            }
        }
        
        // Son noktayı her zaman ekleyelim ki güncel fiyat grafikte görünsün
        if (!filtered.get(filtered.size()-1).getId().equals(snapshots.get(snapshots.size()-1).getId())) {
            filtered.add(snapshots.get(snapshots.size()-1));
        }
        
        return filtered;
    }
}
