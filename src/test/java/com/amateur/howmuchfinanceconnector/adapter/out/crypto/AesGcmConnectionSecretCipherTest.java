package com.amateur.howmuchfinanceconnector.adapter.out.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmConnectionSecretCipherTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsWithRandomNonceAndDecryptsWithConfiguredKey() throws Exception {
        var cipher = new AesGcmConnectionSecretCipher(KEY);

        String first = cipher.encrypt("connection-secret");
        String second = cipher.encrypt("connection-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(decrypt(first)).isEqualTo("connection-secret");
        assertThatThrownBy(() -> new AesGcmConnectionSecretCipher("c2hvcnQ="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String decrypt(String envelope) throws Exception {
        String[] parts = envelope.split(":", 3);
        byte[] nonce = Base64.getDecoder().decode(parts[1]);
        assertThat(nonce).hasSize(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(Base64.getDecoder().decode(KEY), "AES"),
                new GCMParameterSpec(128, nonce));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    }
}
