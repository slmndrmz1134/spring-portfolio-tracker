package com.example.demo.controller;

import com.example.demo.entity.Asset;
import com.example.demo.entity.Portfolio;
import com.example.demo.service.PortfolioService;
import com.example.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

// Kullanıcının tarayıcıdan yaptığı istekleri karşılar, sayfaları döndürür
@Controller
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final StockService stockService;

    // Ana sayfa → tüm portföyleri listeler
    @GetMapping("/")
    public String home(Model model) {
        var portfolios = portfolioService.getAllPortfolios();
        Map<Long, Double> totalValues = new HashMap<>();
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

        model.addAttribute("portfolios", portfolios);
        model.addAttribute("totalValues", totalValues);
        model.addAttribute("portfolioProfitLosses", portfolioProfitLosses);
        model.addAttribute("portfolioPercentages", portfolioPercentages);
        model.addAttribute("grandTotalValue", grandTotalValue);
        model.addAttribute("grandTotalProfitLoss", grandTotalProfitLoss);
        model.addAttribute("profitLossPercentage", profitLossPercentage);
        return "index";
    }

    // Yeni portföy oluştur
    @PostMapping("/portfolio/create")
    public String createPortfolio(@RequestParam String name) {
        portfolioService.createPortfolio(name);
        return "redirect:/";
    }

    // Portföy detay sayfası
    @GetMapping("/portfolio/{id}")
    public String portfolioDetail(@PathVariable Long id, Model model) {
        Portfolio portfolio = portfolioService.getPortfolioById(id);
        Map<Long, Double> rawCurrentPrices = new HashMap<>(); // Orijinal para birimindeki fiyat
        Map<Long, Double> currentPricesTRY = new HashMap<>();
        Map<Long, Double> purchasePricesTRY = new HashMap<>();
        Map<Long, Double> profitLossTRY = new HashMap<>();
        
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
            
            totalValueTRY += currentPTRY * asset.getQuantity();
            totalPurchaseTRY += purchasePTRY * asset.getQuantity();
        }

        double totalProfitLossTRY = profitLossTRY.values().stream().mapToDouble(Double::doubleValue).sum();
        
        double totalProfitLossPercentage = 0.0;
        if (totalPurchaseTRY > 0) {
            totalProfitLossPercentage = (totalProfitLossTRY / totalPurchaseTRY) * 100.0;
        }

        model.addAttribute("portfolio", portfolio);
        model.addAttribute("rawCurrentPrices", rawCurrentPrices);
        model.addAttribute("currentPrices", currentPricesTRY);
        model.addAttribute("purchasePrices", purchasePricesTRY);
        model.addAttribute("profitLoss", profitLossTRY);
        model.addAttribute("totalProfitLoss", totalProfitLossTRY);
        model.addAttribute("totalProfitLossPercentage", totalProfitLossPercentage);
        model.addAttribute("totalValue", totalValueTRY);
        model.addAttribute("newAsset", new Asset());
        return "portfolio-detail";
    }

    // Portföye varlık ekle
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

        // Para birimini API'den otomatik tespit et
        String detectedCurrency = stockService.getCurrencyForSymbol(symbol);
        asset.setCurrency(detectedCurrency);

        portfolioService.addAsset(id, asset);
        return "redirect:/portfolio/" + id;
    }

    // Varlık sil
    @PostMapping("/asset/{assetId}/delete")
    public String deleteAsset(@PathVariable Long assetId,
                              @RequestParam Long portfolioId) {
        portfolioService.deleteAsset(assetId);
        return "redirect:/portfolio/" + portfolioId;
    }

    // Varlık düzenle
    @PostMapping("/asset/{assetId}/edit")
    public String editAsset(@PathVariable Long assetId,
                            @RequestParam Long portfolioId,
                            @RequestParam Double quantity,
                            @RequestParam(required = false) Double purchasePrice) {
        portfolioService.editAsset(assetId, quantity, purchasePrice);
        return "redirect:/portfolio/" + portfolioId;
    }

    // Varlık sat
    @PostMapping("/asset/{assetId}/sell")
    public String sellAsset(@PathVariable Long assetId,
                            @RequestParam Long portfolioId,
                            @RequestParam Double sellQuantity,
                            @RequestParam Double sellPrice) {
        portfolioService.sellAsset(portfolioId, assetId, sellQuantity, sellPrice);
        return "redirect:/portfolio/" + portfolioId;
    }

    // Portföy sil
    @PostMapping("/portfolio/{id}/delete")
    public String deletePortfolio(@PathVariable Long id) {
        portfolioService.deletePortfolio(id);
        return "redirect:/";
    }

    // Hisse arama
    @GetMapping("/search")
    @ResponseBody
    public String searchStock(@RequestParam String query) {
        return stockService.searchStock(query);
    }

    // Anlık fiyat API'si (döviz kuru göstermek için frontend kullanır)
    @GetMapping("/api/price")
    @ResponseBody
    public Map<String, Double> getPrice(@RequestParam String symbol) {
        Map<String, Double> result = new HashMap<>();
        result.put("price", stockService.getPrice(symbol));
        return result;
    }
}