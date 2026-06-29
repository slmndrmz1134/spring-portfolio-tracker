package com.example.demo.repository;

import com.example.demo.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    // Belirli kaynak ve tarih aralığı için snapshot'ları getir (grafik için)
    List<PortfolioSnapshot> findBySourceAndSnapshotTimestampBetweenOrderBySnapshotTimestampAsc(
        String source, LocalDateTime start, LocalDateTime end);

    // Belirli kaynak ve tarih için tek snapshot
    Optional<PortfolioSnapshot> findBySourceAndSnapshotTimestamp(String source, LocalDateTime timestamp);

    // Belirli kaynağın en son snapshot'ı
    Optional<PortfolioSnapshot> findTopBySourceOrderBySnapshotTimestampDesc(String source);

    // Tüm kaynakların belirli tarihteki snapshot'ları
    List<PortfolioSnapshot> findBySnapshotTimestamp(LocalDateTime timestamp);

    // Belirli kaynağın tüm snapshot'ları (eskiden yeniye)
    List<PortfolioSnapshot> findBySourceOrderBySnapshotTimestampAsc(String source);
}
