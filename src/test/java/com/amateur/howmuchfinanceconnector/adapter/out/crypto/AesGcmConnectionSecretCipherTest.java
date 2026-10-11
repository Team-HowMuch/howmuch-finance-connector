package com.amateur.howmuchfinanceconnector.adapter.out.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmConnectionSecretCipherTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsWithRandomNonceAndDecryptsWithConfiguredKey() {
        var cipher = new AesGcmConnectionSecretCipher(KEY);

        String first = cipher.encrypt("connection-secret");
        String second = cipher.encrypt("connection-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("connection-secret");
        assertThatThrownBy(() -> cipher.decrypt("invalid-envelope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AesGcmConnectionSecretCipher("c2hvcnQ="))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
