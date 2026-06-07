package com.example.demo.repository;

import com.example.demo.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository bize save, delete, findAll gibi metodları otomatik verir
public interface AssetRepository extends JpaRepository<Asset, Long> {
}
