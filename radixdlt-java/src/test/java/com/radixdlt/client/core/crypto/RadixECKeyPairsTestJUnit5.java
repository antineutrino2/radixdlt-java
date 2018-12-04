package com.radixdlt.client.core.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class RadixECKeyPairsTestJUnit5 {

    @BeforeEach
    public void beforeSuite() {
        // Ensure the BouncyCastle providers are loaded into memory
        // (because BouncyCastle SHA-256 is used in "seed" tests).
        ECKeyPairGenerator.install();
    }

    @Test
    @DisplayName("Given privateKey1 and privateKey2 When we compare them Then they should be different")
    public void generateAndCompareTwoPrivateKeys() {
        byte[] privateKey1 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPair()
                .getPrivateKey();

        byte[] privateKey2 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPair()
                .getPrivateKey();

        assertThat(privateKey1, not(equalTo(privateKey2)));
    }

    @Test
    @DisplayName("Given privateKey1 and privateKey2 with same seed When we compare them Then they should be the same")
    public void generateAndCompareTwoPrivateKeysWithSameSeedInput() {
        byte[] seed = "seed".getBytes();
        byte[] privateKey1 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        byte[] privateKey2 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        assertThat(privateKey1, equalTo(privateKey2));
    }
}
