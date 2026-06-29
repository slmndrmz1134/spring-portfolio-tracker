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
            
            // Altın Çeşitleri
            if (symbol.startsWith("ALTIN_") || "GOLD_GRAM_TRY".equals(symbol)) {
                JsonNode meta = getMetaNode("GC=F");
                Double ounceUsd = meta.path("regularMarketPrice").asDouble();
                if ("ALTIN_ONS".equals(symbol)) return ounceUsd;

                Double usdTry = getUsdTryRate();
                // 1 Ons = 31.1034768 Gram
                Double gramTry = (ounceUsd / 31.1034768) * usdTry;

                if ("ALTIN_GRAM".equals(symbol) || "GOLD_GRAM_TRY".equals(symbol)) return gramTry;
                if ("ALTIN_CEYREK".equals(symbol)) return gramTry * 1.64; // Çeyrek altın (piyasa yaklaşık çarpanı)
                if ("ALTIN_YARIM".equals(symbol)) return gramTry * 3.28;
                if ("ALTIN_TAM".equals(symbol)) return gramTry * 6.56;
                
                return gramTry; // Fallback
            }

            JsonNode meta = getMetaNode(symbol);
            return meta.path("regularMarketPrice").asDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Verilen sembolün para birimini çeker (USD, TRY, EUR vs.)
    public String getCurrencyForSymbol(String symbol) {
        try {
            // TRY sembolü veya Altın için para birimi ayarı
            if ("TRY".equals(symbol)) return "TRY";
            if (symbol.startsWith("ALTIN_") || "GOLD_GRAM_TRY".equals(symbol)) {
                if ("ALTIN_ONS".equals(symbol)) return "USD";
                return "TRY";
            }
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

    // USD/TRY kurunu getirir
    public Double getUsdTryRate() {
        return getPrice("USDTRY=X");
    }

    // Herhangi bir para biriminin USD karşılığını getirir
    public Double getExchangeRateToUSD(String currency) {
        if ("USD".equals(currency)) {
            return 1.0;
        }
        if ("TRY".equals(currency) || currency == null || currency.isEmpty()) {
            Double usdTry = getUsdTryRate();
            return usdTry > 0 ? 1.0 / usdTry : 0.0;
        }
        // Diğer para birimleri için: önce TRY'ye sonra USD'ye çevir
        Double toTry = getExchangeRateToTRY(currency);
        Double usdTry = getUsdTryRate();
        return usdTry > 0 ? toTry / usdTry : 0.0;
    }

    // Hisse arama — kullanıcının yazdığı metne göre öneri getirir
    public String searchStock(String query) {
        try {
            // Altın aramasıysa özel sonuç döndür
            String q = query.toLowerCase();
            if (q.contains("altın") || q.contains("altin") || q.contains("gold") || q.contains("ceyrek") || q.contains("çeyrek") || q.contains("yarım")) {
                return "{\"quotes\":[" +
                    "{\"symbol\":\"ALTIN_GRAM\", \"shortname\":\"Gram Altın\", \"longname\":\"Saf Gram Altın (24 Ayar)\", \"exchange\":\"BIST\", \"typeDisp\":\"Emtia\"}," +
                    "{\"symbol\":\"ALTIN_CEYREK\", \"shortname\":\"Çeyrek Altın\", \"longname\":\"Çeyrek Altın\", \"exchange\":\"BIST\", \"typeDisp\":\"Emtia\"}," +
                    "{\"symbol\":\"ALTIN_YARIM\", \"shortname\":\"Yarım Altın\", \"longname\":\"Yarım Altın\", \"exchange\":\"BIST\", \"typeDisp\":\"Emtia\"}," +
                    "{\"symbol\":\"ALTIN_TAM\", \"shortname\":\"Tam Altın\", \"longname\":\"Tam Altın\", \"exchange\":\"BIST\", \"typeDisp\":\"Emtia\"}," +
                    "{\"symbol\":\"ALTIN_ONS\", \"shortname\":\"Ons Altın ($)\", \"longname\":\"Altın ONS (USD)\", \"exchange\":\"COMEX\", \"typeDisp\":\"Emtia\"}" +
                    "]}";
            }

            String url = "https://query2.finance.yahoo.com/v1/finance/search?q=" + query + "&lang=tr&region=TR&quotesCount=6";
            HttpEntity<String> entity = new HttpEntity<>(getBrowserHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            return "{\"quotes\":[]}";
        }
    }
}