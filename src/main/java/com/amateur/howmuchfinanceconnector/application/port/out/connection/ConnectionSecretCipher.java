package com.amateur.howmuchfinanceconnector.application.port.out.connection;

public interface ConnectionSecretCipher {
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
