package com.example.demo.controller;

import com.example.demo.entity.Asset;
import com.example.demo.entity.Portfolio;
import com.example.demo.entity.TradeHistory;
import com.example.demo.repository.TradeHistoryRepository;
import com.example.demo.service.PerformanceService;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Kullanıcının tarayıcıdan yaptığı istekleri karşılar, sayfaları döndürür
@Controller
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final StockService stockService;
    private final PerformanceService performanceService;
    private final TradeHistoryRepository tradeHistoryRepository;

    // ═══════════════════════════════════════════
    // ANA SAYFA — Dashboard
    // ═══════════════════════════════════════════

    @GetMapping("/")
    public String home(Model model) {
        // Snapshot al (günde 1)
        try {
            performanceService.takePeriodicSnapshot();
        } catch (Exception e) {
            // Snapshot hatası sayfayı bloklamasın
        }

        var portfolios = portfolioService.getAllPortfolios();
        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        Map<Long, Double> totalValues = new HashMap<>();
        Map<Long, Double> totalValuesUsd = new HashMap<>();
        Map<Long, Double> portfolioProfitLosses = new HashMap<>();
        Map<Long, Double> portfolioPercentages = new HashMap<>();
        
        double grandTotalValue = 0.0;
        double grandTotalProfitLoss = 0.0;
        double grandTotalPurchase = 0.0;

        for (Portfolio p : portfolios) {
            double pTotal = 0.0;
            double pProfitLoss = 0.0;
            double pPurchase = 0.0;
            
            for (Asset asset : p.getAssets()) {
                Double currentPrice = stockService.getPrice(asset.getSymbol());
                double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
                
                double currentPriceTRY = currentPrice * exchangeRate;
                pTotal += currentPriceTRY * asset.getQuantity();
                
                double purchaseP = asset.getPurchasePrice() != null ? asset.getPurchasePrice() : currentPrice;
                double purchasePTRY = purchaseP * exchangeRate;
                
                double assetPL = (currentPriceTRY - purchasePTRY) * asset.getQuantity();
                pProfitLoss += assetPL;
                pPurchase += purchasePTRY * asset.getQuantity();
                
                grandTotalProfitLoss += assetPL;
                grandTotalPurchase += purchasePTRY * asset.getQuantity();
            }
            totalValues.put(p.getId(), pTotal);
            totalValuesUsd.put(p.getId(), usdTryRate > 0 ? pTotal / usdTryRate : 0);
            portfolioProfitLosses.put(p.getId(), pProfitLoss);
            
            double pPercentage = 0.0;
            if (pPurchase > 0) {
                pPercentage = (pProfitLoss / pPurchase) * 100.0;
            }
            portfolioPercentages.put(p.getId(), pPercentage);
            
            grandTotalValue += pTotal;
        }
        
        double profitLossPercentage = 0.0;
        if (grandTotalPurchase > 0) {
            profitLossPercentage = (grandTotalProfitLoss / grandTotalPurchase) * 100.0;
        }

        // Grand total USD/TRY
        double grandTotalUsd = usdTryRate > 0 ? grandTotalValue / usdTryRate : 0;
        double grandTotalTry = grandTotalValue;

        // Bileşik performans (YTD varsayılan)
        Map<String, Object> compositePerf = performanceService.calculateCompositePerformance("YTD");

        // Realized P&L
        Double realizedPLUsd = performanceService.getTotalRealizedPLUsd();
        Double realizedPLTry = performanceService.getTotalRealizedPLTry();

        // Günlük değişim
        double dailyChange = performanceService.getDailyChange();

        // En iyi/kötü performanslar
        Map<String, Object> topPerformers = performanceService.getTopPerformers();

        model.addAttribute("portfolios", portfolios);
        model.addAttribute("totalValues", totalValues);
        model.addAttribute("totalValuesUsd", totalValuesUsd);
        model.addAttribute("portfolioProfitLosses", portfolioProfitLosses);
        model.addAttribute("portfolioPercentages", portfolioPercentages);
        model.addAttribute("grandTotalValue", grandTotalValue);
        model.addAttribute("grandTotalValueTry", grandTotalTry);
        model.addAttribute("grandTotalValueUsd", grandTotalUsd);
        model.addAttribute("grandTotalProfitLoss", grandTotalProfitLoss);
        model.addAttribute("profitLossPercentage", profitLossPercentage);
        model.addAttribute("usdTryRate", usdTryRate);
        model.addAttribute("compositePerformance", compositePerf.get("compositePerformance"));
        model.addAttribute("compositeBreakdown", compositePerf.get("breakdown"));
        model.addAttribute("realizedPLUsd", realizedPLUsd != null ? realizedPLUsd : 0.0);
        model.addAttribute("realizedPLTry", realizedPLTry != null ? realizedPLTry : 0.0);
        model.addAttribute("dailyChange", dailyChange);
        model.addAttribute("topPerformers", topPerformers);

        return "index";
    }

    // ═══════════════════════════════════════════
    // PORTFÖY CRUD
    // ═══════════════════════════════════════════

    @PostMapping("/portfolio/create")
    public String createPortfolio(@RequestParam String name, @RequestParam(required = false) String platform) {
        portfolioService.createPortfolio(name, platform);
        return "redirect:/";
    }

    @GetMapping("/portfolio/{id}")
    public String portfolioDetail(@PathVariable Long id, Model model) {
        Portfolio portfolio = portfolioService.getPortfolioById(id);
        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        Map<Long, Double> rawCurrentPrices = new HashMap<>();
        Map<Long, Double> currentPricesTRY = new HashMap<>();
        Map<Long, Double> purchasePricesTRY = new HashMap<>();
        Map<Long, Double> profitLossTRY = new HashMap<>();
        Map<Long, Double> profitLossUSD = new HashMap<>();
        Map<Long, Double> currentValuesUSD = new HashMap<>();
        
        double totalValueTRY = 0.0;
        double totalPurchaseTRY = 0.0;

        for (Asset asset : portfolio.getAssets()) {
            Double currentPrice = stockService.getPrice(asset.getSymbol());
            rawCurrentPrices.put(asset.getId(), currentPrice);
            
            double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
            
            double currentPTRY = currentPrice * exchangeRate;
            currentPricesTRY.put(asset.getId(), currentPTRY);
            
            double purchaseP = asset.getPurchasePrice() != null ? asset.getPurchasePrice() : currentPrice;
            double purchasePTRY = purchaseP * exchangeRate;
            purchasePricesTRY.put(asset.getId(), purchasePTRY);
            
            // Kar/zarar = (anlık fiyat - alış fiyatı) x adet (her ikisi de TRY cinsinden)
            Double plTRY = (currentPTRY - purchasePTRY) * asset.getQuantity();
            profitLossTRY.put(asset.getId(), plTRY);

            // USD cinsinden kâr/zarar
            Double plUSD = usdTryRate > 0 ? plTRY / usdTryRate : 0;
            profitLossUSD.put(asset.getId(), plUSD);

            // USD cinsinden güncel değer
            double valUSD = usdTryRate > 0 ? (currentPTRY * asset.getQuantity()) / usdTryRate : 0;
            currentValuesUSD.put(asset.getId(), valUSD);
            
            totalValueTRY += currentPTRY * asset.getQuantity();
            totalPurchaseTRY += purchasePTRY * asset.getQuantity();
        }

        double totalProfitLossTRY = profitLossTRY.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalProfitLossUSD = usdTryRate > 0 ? totalProfitLossTRY / usdTryRate : 0;
        
        double totalProfitLossPercentage = 0.0;
        if (totalPurchaseTRY > 0) {
            totalProfitLossPercentage = (totalProfitLossTRY / totalPurchaseTRY) * 100.0;
        }

        double totalValueUSD = usdTryRate > 0 ? totalValueTRY / usdTryRate : 0;

        model.addAttribute("portfolio", portfolio);
        model.addAttribute("rawCurrentPrices", rawCurrentPrices);
        model.addAttribute("currentPrices", currentPricesTRY);
        model.addAttribute("purchasePrices", purchasePricesTRY);
        model.addAttribute("profitLoss", profitLossTRY);
        model.addAttribute("profitLossUsd", profitLossUSD);
        model.addAttribute("currentValuesUsd", currentValuesUSD);
        model.addAttribute("totalProfitLoss", totalProfitLossTRY);
        model.addAttribute("totalProfitLossUsd", totalProfitLossUSD);
        model.addAttribute("totalProfitLossPercentage", totalProfitLossPercentage);
        model.addAttribute("totalValue", totalValueTRY);
        model.addAttribute("totalValueUsd", totalValueUSD);
        model.addAttribute("usdTryRate", usdTryRate);
        model.addAttribute("newAsset", new Asset());
        return "portfolio-detail";
    }

    @PostMapping("/portfolio/{id}/add-asset")
    public String addAsset(@PathVariable Long id,
                           @RequestParam String symbol,
                           @RequestParam String name,
                           @RequestParam String type,
                           @RequestParam Double quantity,
                           @RequestParam(required = false) Double purchasePrice) {
        Asset asset = new Asset();
        asset.setSymbol(symbol);
        asset.setName(name);
        asset.setType(type);
        asset.setQuantity(quantity);
        asset.setPurchasePrice(purchasePrice != null ? purchasePrice : stockService.getPrice(symbol));
        asset.setPurchaseDate(LocalDate.now());

        // Para birimini API'den otomatik tespit et
        String detectedCurrency = stockService.getCurrencyForSymbol(symbol);
        asset.setCurrency(detectedCurrency);

        portfolioService.addAsset(id, asset);
        return "redirect:/portfolio/" + id;
    }

    @PostMapping("/asset/{assetId}/delete")
    public String deleteAsset(@PathVariable Long assetId,
                              @RequestParam Long portfolioId) {
        portfolioService.deleteAsset(assetId);
        return "redirect:/portfolio/" + portfolioId;
    }

    @PostMapping("/asset/{assetId}/edit")
    public String editAsset(@PathVariable Long assetId,
                            @RequestParam Long portfolioId,
                            @RequestParam Double quantity,
                            @RequestParam(required = false) Double purchasePrice) {
        portfolioService.editAsset(assetId, quantity, purchasePrice);
        return "redirect:/portfolio/" + portfolioId;
    }

    @PostMapping("/asset/{assetId}/sell")
    public String sellAsset(@PathVariable Long assetId,
                            @RequestParam Long portfolioId,
                            @RequestParam Double sellQuantity,
                            @RequestParam Double sellPrice) {
        portfolioService.sellAsset(portfolioId, assetId, sellQuantity, sellPrice);
        return "redirect:/portfolio/" + portfolioId;
    }

    @PostMapping("/portfolio/{id}/delete")
    public String deletePortfolio(@PathVariable Long id) {
        portfolioService.deletePortfolio(id);
        return "redirect:/";
    }

    @PostMapping("/portfolio/{id}/settings")
    public String updatePortfolioSettings(@PathVariable Long id,
                                          @RequestParam(required = false) Double manualYtd) {
        portfolioService.updateSettings(id, manualYtd);
        return "redirect:/portfolio/" + id;
    }



    // ═══════════════════════════════════════════
    // İŞLEM GEÇMİŞİ
    // ═══════════════════════════════════════════

    @GetMapping("/trade-history")
    public String tradeHistory(Model model) {
        List<TradeHistory> trades = tradeHistoryRepository.findAllByOrderByTradeDateDesc();
        Double totalRealizedUsd = tradeHistoryRepository.getTotalRealizedProfitLossUsd();
        Double totalRealizedTry = tradeHistoryRepository.getTotalRealizedProfitLossTry();

        model.addAttribute("trades", trades);
        model.addAttribute("totalRealizedUsd", totalRealizedUsd != null ? totalRealizedUsd : 0.0);
        model.addAttribute("totalRealizedTry", totalRealizedTry != null ? totalRealizedTry : 0.0);
        return "trade-history";
    }

    // ═══════════════════════════════════════════
    // API ENDPOINTLERİ (JSON)
    // ═══════════════════════════════════════════

    @GetMapping("/search")
    @ResponseBody
    public String searchStock(@RequestParam String query) {
        return stockService.searchStock(query);
    }

    @GetMapping("/api/price")
    @ResponseBody
    public Map<String, Double> getPrice(@RequestParam String symbol) {
        Map<String, Double> result = new HashMap<>();
        result.put("price", stockService.getPrice(symbol));
        return result;
    }

    // Bileşik performans API (grafik için)
    @GetMapping("/api/performance/composite")
    @ResponseBody
    public Map<String, Object> getCompositePerformance(@RequestParam(defaultValue = "YTD") String period) {
        return performanceService.calculateCompositePerformance(period);
    }

    // Performans grafiği verileri
    @GetMapping("/api/performance/chart")
    @ResponseBody
    public Map<String, Object> getPerformanceChart(@RequestParam(defaultValue = "1M") String period) {
        return performanceService.getPerformanceChartData(period);
    }

    // Portföy dağılımı (pasta grafik)
    @GetMapping("/api/allocation")
    @ResponseBody
    public Map<String, Object> getAllocation() {
        return performanceService.getPortfolioAllocation();
    }

    // USD/TRY kuru
    @GetMapping("/api/usd-rate")
    @ResponseBody
    public Map<String, Double> getUsdRate() {
        Map<String, Double> result = new HashMap<>();
        result.put("rate", stockService.getUsdTryRate());
        return result;
    }
}