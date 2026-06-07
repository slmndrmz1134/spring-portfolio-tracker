package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StockService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Yahoo Finance bazen bot sandığı istekleri blokluyor
    // Bu yüzden normal bir tarayıcı gibi görünmek için header ekliyoruz
    private HttpHeaders getBrowserHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");
        return headers;
    }

    // Yahoo Finance API'den meta bilgisini çeker (fiyat + para birimi ortak kullanır)
    private JsonNode getMetaNode(String symbol) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol;
        HttpEntity<String> entity = new HttpEntity<>(getBrowserHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("chart").path("result").get(0).path("meta");
    }

    // Verilen sembolün anlık fiyatını çeker
    public Double getPrice(String symbol) {
        try {
            // TRY (Türk Lirası) için kur her zaman 1.0
            if ("TRY".equals(symbol)) return 1.0;
            JsonNode meta = getMetaNode(symbol);
            return meta.path("regularMarketPrice").asDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Verilen sembolün para birimini çeker (USD, TRY, EUR vs.)
    public String getCurrencyForSymbol(String symbol) {
        try {
            // TRY sembolü için direkt TRY döndür
            if ("TRY".equals(symbol)) return "TRY";
            JsonNode meta = getMetaNode(symbol);
            String currency = meta.path("currency").asText();
            return currency.isEmpty() ? "TRY" : currency;
        } catch (Exception e) {
            return "TRY";
        }
    }

    // Herhangi bir para biriminin TRY karşılığını getirir
    public Double getExchangeRateToTRY(String currency) {
        if ("TRY".equals(currency) || currency == null || currency.isEmpty()) {
            return 1.0;
        }
        return getPrice(currency + "TRY=X");
    }

    // Hisse arama — kullanıcının yazdığı metne göre öneri getirir
    public String searchStock(String query) {
        try {
            String url = "https://query2.finance.yahoo.com/v1/finance/search?q=" + query + "&lang=tr&region=TR&quotesCount=6";
            HttpEntity<String> entity = new HttpEntity<>(getBrowserHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            return "{\"quotes\":[]}";
        }
    }
}