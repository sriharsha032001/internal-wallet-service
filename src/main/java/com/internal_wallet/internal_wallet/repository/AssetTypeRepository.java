package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.AssetType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {

    /**
     * O(1) — backed by UNIQUE index on asset_types.name.
     * Result is cached in-memory: asset types are seeded once and never mutated,
     * so the cache never needs eviction.
     */
    @Cacheable(value = "assetTypes", key = "#name")
    Optional<AssetType> findByName(String name);
}
