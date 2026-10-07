package com.netbanking.card.api;

/** {@code publicKey} is SPKI/base64 for RSA-OAEP with SHA-256. */
public record PinKeyResponse(String keyId, String algorithm, String publicKey, long maxAgeSeconds) {}
