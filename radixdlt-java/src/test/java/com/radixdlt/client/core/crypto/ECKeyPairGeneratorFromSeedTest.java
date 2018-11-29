package com.radixdlt.client.core.crypto;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class ECKeyPairGeneratorFromSeedTest {

    @BeforeClass
    public static void beforeSuite() {
        // Ensure the BouncyCastle providers are loaded into memory
        // (because BouncyCastle SHA-256 is used in "seed" tests).
        ECKeyPairGenerator.newInstance();
    }

    @Test
    public void when_generating_two_default_key_pairs__they_should_have_different_private_keys() {
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
    public void when_generating_two_key_pairs_from_same_seed__they_should_have_same_private_keys() {
        byte[] seed = "seed".getBytes();
        byte[] privateKey1 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        byte[] privateKey2 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        assertThat(privateKey1, equalTo(privateKey2));
    }

}
