package com.internal_wallet.internal_wallet.exception;

public class AssetTypeNotFoundException extends RuntimeException {

    public AssetTypeNotFoundException(String assetTypeName) {
        super("Asset type not found: " + assetTypeName);
    }
}
