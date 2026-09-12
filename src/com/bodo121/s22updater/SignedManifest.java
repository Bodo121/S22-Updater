package com.bodo121.s22updater;

import org.json.JSONObject;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 manifest verification. Private keys never belong in repo or APK. */
final class SignedManifest {
    // Empty until an offline Ed25519 public key is provided. Verification fails closed.
    static final String PUBLIC_KEY_BASE64 = "";
    static JSONObject verified(String envelopeJson) throws Exception {
        JSONObject envelope = new JSONObject(envelopeJson);
        String manifest = envelope.getString("manifest");
        String sig = envelope.getString("signature");
        if (PUBLIC_KEY_BASE64.isEmpty())
            throw new SecurityException("Signed manifest public key is not configured; refusing remote trust");
        Signature verifier = Signature.getInstance("Ed25519");
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_BASE64)));
        verifier.initVerify(key);
        verifier.update(manifest.getBytes("UTF-8"));
        if (!verifier.verify(Base64.getDecoder().decode(sig)))
            throw new SecurityException("Signed manifest verification failed");
        return new JSONObject(manifest);
    }
}
