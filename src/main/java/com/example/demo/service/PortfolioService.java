package com.example.demo.service;

import com.example.demo.entity.Asset;
import com.example.demo.entity.Portfolio;
import com.example.demo.entity.TradeHistory;
import com.example.demo.repository.AssetRepository;
import com.example.demo.repository.PortfolioRepository;
import com.example.demo.repository.TradeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Portföy işlemlerinin tüm iş mantığı burada
@Service
@RequiredArgsConstructor // Lombok: constructor injection otomatik oluşturur
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final StockService stockService;
    private final TradeHistoryRepository tradeHistoryRepository;

    // Tüm portföyleri getir
    public List<Portfolio> getAllPortfolios() {
        return portfolioRepository.findAll();
    }

    // Yeni portföy oluştur
    public Portfolio createPortfolio(String name, String platform) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setPlatform(platform);
        return portfolioRepository.save(portfolio);
    }

    // Portföye varlık ekle
    public Asset addAsset(Long portfolioId, Asset asset) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));

        // Alış tarihi yoksa bugünün tarihini set et
        if (asset.getPurchaseDate() == null) {
            asset.setPurchaseDate(LocalDate.now());
        }

        Asset saved = assetRepository.save(asset);
        portfolio.getAssets().add(saved);
        portfolioRepository.save(portfolio);

        // Alış işlemini trade history'ye kaydet
        recordTrade(portfolioId, asset, "BUY", asset.getQuantity(),
            asset.getPurchasePrice() != null ? asset.getPurchasePrice() : stockService.getPrice(asset.getSymbol()));

        return saved;
    }

    // Varlığı sil
    public void deleteAsset(Long assetId) {
        assetRepository.deleteById(assetId);
    }

    // Portföyün toplam değerini hesapla (anlık fiyat x adet)
    public Double calculateTotalValue(Portfolio portfolio) {
        return portfolio.getAssets().stream()
            .mapToDouble(asset -> {
                Double currentPrice = stockService.getPrice(asset.getSymbol());
                return currentPrice * asset.getQuantity();
            })
            .sum();
    }

    // Portföyü ID ile getir
    public Portfolio getPortfolioById(Long id) {
        return portfolioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));
    }

    // Portföyü sil
    public void deletePortfolio(Long id) {
        portfolioRepository.deleteById(id);
    }

    // Portföy ayarlarını güncelle
    public void updateSettings(Long id, Double manualYtd) {
        Portfolio portfolio = portfolioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));
        portfolio.setManualYtd(manualYtd);
        portfolioRepository.save(portfolio);
    }

    // Varlık düzenle
    public void editAsset(Long assetId, Double newQuantity, Double newPurchasePrice) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new RuntimeException("Varlık bulunamadı"));
        asset.setQuantity(newQuantity);
        asset.setPurchasePrice(newPurchasePrice);
        assetRepository.save(asset);
    }

    // Varlık sat — TradeHistory ile USD cinsinden kâr/zarar kaydı
    public void sellAsset(Long portfolioId, Long assetId, Double sellQuantity, Double sellPrice) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new RuntimeException("Varlık bulunamadı"));

        if (sellQuantity > asset.getQuantity()) {
            throw new RuntimeException("Sahip olduğunuzdan fazla satamazsınız!");
        }

        // Kur bilgileri
        double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
        double usdTryRate = stockService.getUsdTryRate();
        if (usdTryRate <= 0) usdTryRate = 1.0;

        // Satış gelirini TRY'ye çevir
        double proceedsTRY = sellQuantity * sellPrice * exchangeRate;
        double proceedsUSD = proceedsTRY / usdTryRate;

        // Alış maliyeti
        double purchaseP = asset.getPurchasePrice() != null ? asset.getPurchasePrice() : sellPrice;
        double purchaseCostTRY = sellQuantity * purchaseP * exchangeRate;
        double purchaseCostUSD = purchaseCostTRY / usdTryRate;

        // Kâr/Zarar hesabı
        double profitLossTRY = proceedsTRY - purchaseCostTRY;
        double profitLossUSD = proceedsUSD - purchaseCostUSD;

        // Trade History kaydı
        TradeHistory trade = new TradeHistory();
        trade.setPortfolioId(portfolioId);
        trade.setSymbol(asset.getSymbol());
        trade.setAssetName(asset.getName());
        trade.setTradeType("SELL");
        trade.setQuantity(sellQuantity);
        trade.setPricePerUnit(sellPrice);
        trade.setPricePerUnitUsd(sellPrice * stockService.getExchangeRateToUSD(asset.getCurrency()));
        trade.setTotalAmountUsd(proceedsUSD);
        trade.setTotalAmountTry(proceedsTRY);
        trade.setProfitLossUsd(profitLossUSD);
        trade.setProfitLossTry(profitLossTRY);
        trade.setExchangeRate(exchangeRate);
        trade.setUsdTryRate(usdTryRate);
        trade.setCurrency(asset.getCurrency());
        trade.setTradeDate(LocalDateTime.now());
        tradeHistoryRepository.save(trade);

        // Varlığın miktarını azalt veya sil
        double remainingQuantity = asset.getQuantity() - sellQuantity;
        if (remainingQuantity <= 0) {
            portfolio.getAssets().remove(asset);
            assetRepository.delete(asset);
        } else {
            asset.setQuantity(remainingQuantity);
            assetRepository.save(asset);
        }

        // TRY (Nakit) varlığını bul
        Asset tryAsset = portfolio.getAssets().stream()
            .filter(a -> "TRY".equals(a.getSymbol()) && "CURRENCY".equals(a.getType()))
            .findFirst()
            .orElse(null);

        if (tryAsset != null) {
            // Varsa miktarını artır
            tryAsset.setQuantity(tryAsset.getQuantity() + proceedsTRY);
            assetRepository.save(tryAsset);
        } else {
            // Yoksa yeni oluştur
            Asset newTryAsset = new Asset();
            newTryAsset.setSymbol("TRY");
            newTryAsset.setName("Türk Lirası");
            newTryAsset.setType("CURRENCY");
            newTryAsset.setCurrency("TRY");
            newTryAsset.setPurchasePrice(1.0);
            newTryAsset.setQuantity(proceedsTRY);
            newTryAsset.setPurchaseDate(LocalDate.now());
            
            Asset savedTry = assetRepository.save(newTryAsset);
            portfolio.getAssets().add(savedTry);
        }
        
        portfolioRepository.save(portfolio);
    }

    // Trade history'ye alış/satış kaydı
    private void recordTrade(Long portfolioId, Asset asset, String type, Double quantity, Double price) {
        try {
            double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
            double usdTryRate = stockService.getUsdTryRate();
            if (usdTryRate <= 0) usdTryRate = 1.0;

            TradeHistory trade = new TradeHistory();
            trade.setPortfolioId(portfolioId);
            trade.setSymbol(asset.getSymbol());
            trade.setAssetName(asset.getName());
            trade.setTradeType(type);
            trade.setQuantity(quantity);
            trade.setPricePerUnit(price);
            trade.setPricePerUnitUsd(price * stockService.getExchangeRateToUSD(asset.getCurrency()));
            trade.setTotalAmountTry(quantity * price * exchangeRate);
            trade.setTotalAmountUsd(trade.getTotalAmountTry() / usdTryRate);
            trade.setExchangeRate(exchangeRate);
            trade.setUsdTryRate(usdTryRate);
            trade.setCurrency(asset.getCurrency());
            trade.setTradeDate(LocalDateTime.now());
            tradeHistoryRepository.save(trade);
        } catch (Exception e) {
            // Trade history kaydı başarısız olsa bile ana işlem devam etsin
            System.err.println("Trade history kaydı başarısız: " + e.getMessage());
        }
    }
}