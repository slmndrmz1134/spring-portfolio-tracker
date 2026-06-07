package com.example.demo.service;

import com.example.demo.entity.Asset;
import com.example.demo.entity.Portfolio;
import com.example.demo.repository.AssetRepository;
import com.example.demo.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

// Portföy işlemlerinin tüm iş mantığı burada
@Service
@RequiredArgsConstructor // Lombok: constructor injection otomatik oluşturur
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final StockService stockService;

    // Tüm portföyleri getir
    public List<Portfolio> getAllPortfolios() {
        return portfolioRepository.findAll();
    }

    // Yeni portföy oluştur
    public Portfolio createPortfolio(String name) {
        Portfolio portfolio = new Portfolio();
        portfolio.setName(name);
        return portfolioRepository.save(portfolio);
    }

    // Portföye varlık ekle
    public Asset addAsset(Long portfolioId, Asset asset) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));

        Asset saved = assetRepository.save(asset);
        portfolio.getAssets().add(saved);
        portfolioRepository.save(portfolio);
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

    // Varlık düzenle
    public void editAsset(Long assetId, Double newQuantity, Double newPurchasePrice) {
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new RuntimeException("Varlık bulunamadı"));
        asset.setQuantity(newQuantity);
        asset.setPurchasePrice(newPurchasePrice);
        assetRepository.save(asset);
    }

    // Varlık sat
    public void sellAsset(Long portfolioId, Long assetId, Double sellQuantity, Double sellPrice) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new RuntimeException("Portföy bulunamadı"));
        Asset asset = assetRepository.findById(assetId)
            .orElseThrow(() -> new RuntimeException("Varlık bulunamadı"));

        if (sellQuantity > asset.getQuantity()) {
            throw new RuntimeException("Sahip olduğunuzdan fazla satamazsınız!");
        }

        // Satış gelirini TRY'ye çevir
        double exchangeRate = stockService.getExchangeRateToTRY(asset.getCurrency());
        double proceedsTRY = sellQuantity * sellPrice * exchangeRate;

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
            
            Asset savedTry = assetRepository.save(newTryAsset);
            portfolio.getAssets().add(savedTry);
        }
        
        portfolioRepository.save(portfolio);
    }
}