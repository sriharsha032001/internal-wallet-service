package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {

    /**
     * O(1) — backed by UNIQUE index on asset_types.name.
     */
    Optional<AssetType> findByName(String name);
}
