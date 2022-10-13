/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.keystore.cts;

import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_SOFTWARE;
import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_STRONG_BOX;
import static android.keystore.cts.Attestation.KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_EC;
import static android.keystore.cts.AuthorizationList.KM_ALGORITHM_RSA;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_NONE;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_256;
import static android.keystore.cts.AuthorizationList.KM_DIGEST_SHA_2_512;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_GENERATED;
import static android.keystore.cts.AuthorizationList.KM_ORIGIN_UNKNOWN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_DECRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_ENCRYPT;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_SIGN;
import static android.keystore.cts.AuthorizationList.KM_PURPOSE_VERIFY;
import static android.keystore.cts.RootOfTrust.KM_VERIFIED_BOOT_VERIFIED;
import static android.security.keystore.KeyProperties.DIGEST_NONE;
import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.DIGEST_SHA512;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA;
import static android.security.keystore.KeyProperties.PURPOSE_DECRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_ENCRYPT;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.PURPOSE_VERIFY;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PSS;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertNotEquals;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.RestrictedBuildTest;
import android.security.KeyStoreException;
import android.security.keystore.AttestationUtils;
import android.security.keystore.DeviceIdAttestationException;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.AndroidTestCase;
import android.util.ArraySet;
import android.util.Log;

import com.google.common.collect.ImmutableSet;

import androidx.test.filters.RequiresDevice;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.File;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.KeyGenerator;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

/**
 * Tests for Android KeysStore attestation.
 */
public class KeyAttestationTest extends AndroidTestCase {

    private static final String TAG = AndroidKeyStoreTest.class.getSimpleName();

    private static final int ORIGINATION_TIME_OFFSET = 1000000;
    private static final int CONSUMPTION_TIME_OFFSET = 2000000;

    private static final int KEY_USAGE_BITSTRING_LENGTH = 9;
    private static final int KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET = 0;
    private static final int KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET = 2;
    private static final int KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET = 3;

    private static final int OS_MAJOR_VERSION_MATCH_GROUP_NAME = 1;
    private static final int OS_MINOR_VERSION_MATCH_GROUP_NAME = 2;
    private static final int OS_SUBMINOR_VERSION_MATCH_GROUP_NAME = 3;
    private static final Pattern OS_VERSION_STRING_PATTERN = Pattern
            .compile("([0-9]{1,2})(?:\\.([0-9]{1,2}))?(?:\\.([0-9]{1,2}))?(?:[^0-9.]+.*)?");

    private static final int OS_PATCH_LEVEL_YEAR_GROUP_NAME = 1;
    private static final int OS_PATCH_LEVEL_MONTH_GROUP_NAME = 2;
    private static final Pattern OS_PATCH_LEVEL_STRING_PATTERN = Pattern
            .compile("([0-9]{4})-([0-9]{2})-[0-9]{2}");

    private static final int KM_ERROR_CANNOT_ATTEST_IDS = -66;
    private static final int KM_ERROR_INVALID_INPUT_LENGTH = -21;
    private static final int KM_ERROR_PERMISSION_DENIED = 6;

    public void testVersionParser() throws Exception {
        // Non-numerics/empty give version 0
        assertEquals(0, parseSystemOsVersion(""));
        assertEquals(0, parseSystemOsVersion("N"));

        // Should support one, two or three version number values.
        assertEquals(10000, parseSystemOsVersion("1"));
        assertEquals(10200, parseSystemOsVersion("1.2"));
        assertEquals(10203, parseSystemOsVersion("1.2.3"));

        // It's fine to append other stuff to the dotted numeric version.
        assertEquals(10000, parseSystemOsVersion("1stuff"));
        assertEquals(10200, parseSystemOsVersion("1.2garbage.32"));
        assertEquals(10203, parseSystemOsVersion("1.2.3-stuff"));

        // Two digits per version field are supported
        assertEquals(152536, parseSystemOsVersion("15.25.36"));
        assertEquals(999999, parseSystemOsVersion("99.99.99"));
        assertEquals(0, parseSystemOsVersion("100.99.99"));
        assertEquals(0, parseSystemOsVersion("99.100.99"));
        assertEquals(0, parseSystemOsVersion("99.99.100"));
    }

    @RequiresDevice
    public void testEcAttestation() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        // Note: Curve and key sizes arrays must correspond.
        String[] curves = {
                "secp224r1", "secp256r1", "secp384r1", "secp521r1"
        };
        int[] keySizes = {
                224, 256, 384, 521
        };
        byte[][] challenges = {
                new byte[0], // empty challenge
                "challenge".getBytes(), // short challenge
                new byte[128], // long challenge
        };
        int[] purposes = {
                KM_PURPOSE_SIGN, KM_PURPOSE_VERIFY, KM_PURPOSE_SIGN | KM_PURPOSE_VERIFY
        };
        boolean[] devicePropertiesAttestationValues = {true, false};
        boolean[] includeValidityDatesValues = {true, false};

        for (int curveIndex = 0; curveIndex < curves.length; ++curveIndex) {
            for (int challengeIndex = 0; challengeIndex < challenges.length; ++challengeIndex) {
                for (int purposeIndex = 0; purposeIndex < purposes.length; ++purposeIndex) {
                    for (boolean includeValidityDates : includeValidityDatesValues) {
                        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
                            try {
                                testEcAttestation(challenges[challengeIndex], includeValidityDates,
                                        curves[curveIndex], keySizes[curveIndex],
                                        purposes[purposeIndex], devicePropertiesAttestation);
                            } catch (Throwable e) {
                                if (devicePropertiesAttestation
                                        && (e.getCause() instanceof KeyStoreException)
                                        && KM_ERROR_CANNOT_ATTEST_IDS ==
                                                ((KeyStoreException) e.getCause()).getErrorCode()) {
                                    Log.i(TAG, "key attestation with device IDs not supported; "
                                            + "test skipped");
                                    continue;
                                }
                                throw new Exception("Failed on curve " + curveIndex +
                                        " challenge " + challengeIndex + " purpose " +
                                        purposeIndex + " includeValidityDates " +
                                        includeValidityDates + " and devicePropertiesAttestation " +
                                        devicePropertiesAttestation, e);
                            }
                        }
                    }
                }
            }
        }
    }

    public void testEcAttestation_TooLargeChallenge() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            try {
                testEcAttestation(new byte[129], true /* includeValidityDates */, "secp256r1", 256,
                        KM_PURPOSE_SIGN, devicePropertiesAttestation);
                fail("Attestation challenges larger than 128 bytes should be rejected");
            } catch (ProviderException e) {
                KeyStoreException cause = (KeyStoreException) e.getCause();
                assertTrue(KM_ERROR_INVALID_INPUT_LENGTH == cause.getErrorCode() ||
                        (devicePropertiesAttestation
                                && KM_ERROR_CANNOT_ATTEST_IDS == cause.getErrorCode())
                );
            }
        }
    }

    public void testEcAttestation_NoChallenge() throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            Date now = new Date();
            Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
            Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge(null)
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .build();

            generateKeyPair(KEY_ALGORITHM_EC, spec);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            try {
                Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
                assertEquals(1, certificates.length);

                X509Certificate attestationCert = (X509Certificate) certificates[0];
                assertNull(attestationCert.getExtensionValue(Attestation.ASN1_OID));
                assertNull(attestationCert.getExtensionValue(Attestation.EAT_OID));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    @RestrictedBuildTest
    @RequiresDevice
    public void testEcAttestation_DeviceLocked() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setAttestationChallenge(new byte[128])
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);

        if (TestUtils.hasStrongBox(getContext())) {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256);
            builder.setIsStrongBoxBacked(true);
        } else {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512);
        }

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, TestUtils.hasStrongBox(getContext()));

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            checkDeviceLocked(Attestation.loadFromCertificate(attestationCert));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testAttestationKmVersionMatchesFeatureVersion() throws Exception {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setAttestationChallenge(new byte[128])
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, false /* expectStrongBox */);
            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);
            int kmVersionFromAttestation = attestation.keymasterVersion;
            int keyStoreFeatureVersion = TestUtils.getFeatureVersionKeystore(getContext());

            // Feature Version is required on devices launching with Android 12 (API Level
            // 31) but may be reported on devices launching with an earlier version. If it's
            // present, it must match what is reported in attestation.
            int firstApiLevel = SystemProperties.getInt("ro.product.first_api_level", 0);
            if (firstApiLevel >= 31) {
                assertNotEquals(0, keyStoreFeatureVersion);
            }
            if (keyStoreFeatureVersion != 0) {
                assertEquals(kmVersionFromAttestation, keyStoreFeatureVersion);
            }
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testAttestationKmVersionMatchesFeatureVersionStrongBox() throws Exception {
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        int keyStoreFeatureVersionStrongBox =
                TestUtils.getFeatureVersionKeystoreStrongBox(getContext());

        if (!TestUtils.hasStrongBox(getContext())) {
            // If there's no StrongBox, ensure there's no feature version for it.
            assertEquals(0, keyStoreFeatureVersionStrongBox);
            return;
        }

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setAttestationChallenge(new byte[128])
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setIsStrongBoxBacked(true);

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, true /* expectStrongBox */);
            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);
            int kmVersionFromAttestation = attestation.keymasterVersion;

            // Feature Version is required on devices launching with Android 12 (API Level
            // 31) but may be reported on devices launching with an earlier version. If it's
            // present, it must match what is reported in attestation.
            int firstApiLevel = SystemProperties.getInt("ro.product.first_api_level", 0);
            if (firstApiLevel >= 31) {
                assertNotEquals(0, keyStoreFeatureVersionStrongBox);
            }
            if (keyStoreFeatureVersionStrongBox != 0) {
                assertEquals(kmVersionFromAttestation, keyStoreFeatureVersionStrongBox);
            }
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testEcAttestation_KeyStoreExceptionWhenRequestingUniqueId() throws Exception {
        String keystoreAlias = "test_key";
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                .setAttestationChallenge(new byte[128])
                .setUniqueIdIncluded(true)
                .build();

        try {
            generateKeyPair(KEY_ALGORITHM_EC, spec);
            fail("Attestation should have failed.");
        } catch (ProviderException e) {
            // Attestation is expected to fail because of lack of permissions.
            KeyStoreException cause = (KeyStoreException) e.getCause();
            assertEquals(KM_ERROR_PERMISSION_DENIED, cause.getErrorCode());
        } finally {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    @RequiresDevice
    public void testRsaAttestation() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        int[] keySizes = { // Smallish sizes to keep test runtimes down.
                512, 768, 1024
        };
        byte[][] challenges = {
                new byte[0], // empty challenge
                "challenge".getBytes(), // short challenge
                new byte[128] // long challenge
        };
        int[] purposes = {
                PURPOSE_SIGN | PURPOSE_VERIFY,
                PURPOSE_ENCRYPT | PURPOSE_DECRYPT,
        };
        String[][] encryptionPaddingModes = {
                {
                        ENCRYPTION_PADDING_NONE
                },
                {
                        ENCRYPTION_PADDING_RSA_OAEP,
                },
                {
                        ENCRYPTION_PADDING_RSA_PKCS1,
                },
                {
                        ENCRYPTION_PADDING_RSA_OAEP,
                        ENCRYPTION_PADDING_RSA_PKCS1,
                },
        };
        String[][] signaturePaddingModes = {
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                },
                {
                        SIGNATURE_PADDING_RSA_PSS,
                },
                {
                        SIGNATURE_PADDING_RSA_PKCS1,
                        SIGNATURE_PADDING_RSA_PSS,
                },
        };
        boolean[] devicePropertiesAttestationValues = {true, false};

        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            for (int keySize : keySizes) {
                for (byte[] challenge : challenges) {
                    for (int purpose : purposes) {
                        if (isEncryptionPurpose(purpose)) {
                            testRsaAttestations(keySize, challenge, purpose, encryptionPaddingModes,
                                    devicePropertiesAttestation);
                        } else {
                            testRsaAttestations(keySize, challenge, purpose, signaturePaddingModes,
                                    devicePropertiesAttestation);
                        }
                    }
                }
            }
        }
    }

    public void testRsaAttestation_TooLargeChallenge() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            try {
                testRsaAttestation(new byte[129], true /* includeValidityDates */, 512,
                        PURPOSE_SIGN,
                        null /* paddingModes; may be empty because we'll never test them */,
                        devicePropertiesAttestation);
                fail("Attestation challenges larger than 128 bytes should be rejected");
            } catch(ProviderException e){
                KeyStoreException cause = (KeyStoreException) e.getCause();
                assertTrue(KM_ERROR_INVALID_INPUT_LENGTH == cause.getErrorCode() ||
                        (devicePropertiesAttestation
                                && KM_ERROR_CANNOT_ATTEST_IDS == cause.getErrorCode())
                );
            }
        }
    }

    public void testRsaAttestation_NoChallenge() throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            Date now = new Date();
            Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
            Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge(null)
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .build();

            generateKeyPair(KEY_ALGORITHM_RSA, spec);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            try {
                Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
                assertEquals(1, certificates.length);

                X509Certificate attestationCert = (X509Certificate) certificates[0];
                assertNull(attestationCert.getExtensionValue(Attestation.ASN1_OID));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    @RestrictedBuildTest
    @RequiresDevice  // Emulators have no place to store the needed key
    public void testRsaAttestation_DeviceLocked() throws Exception {
        if (!TestUtils.isAttestationSupported()) {
            return;
        }

        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_PC))
            return;

        String keystoreAlias = "test_key";
        Date now = new Date();
        Date originationEnd = new Date(now.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(now.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                    .setAttestationChallenge("challenge".getBytes())
                    .setKeyValidityStart(now)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);

        if (TestUtils.hasStrongBox(getContext())) {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256);
            builder.setIsStrongBoxBacked(true);
        } else {
            builder.setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512);
        }

        generateKeyPair(KEY_ALGORITHM_RSA, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, TestUtils.hasStrongBox(getContext()));

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            checkDeviceLocked(Attestation.loadFromCertificate(attestationCert));
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    public void testAesAttestation() throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias,
                    PURPOSE_ENCRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setAttestationChallenge(new byte[0])
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .build();
            generateKey(spec, KeyProperties.KEY_ALGORITHM_AES);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            try {
                assertNull(keyStore.getCertificateChain(keystoreAlias));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    public void testHmacAttestation() throws Exception {
        boolean[] devicePropertiesAttestationValues = {true, false};
        for (boolean devicePropertiesAttestation : devicePropertiesAttestationValues) {
            String keystoreAlias = "test_key";
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(keystoreAlias, PURPOSE_SIGN)
                    .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation)
                    .build();

            generateKey(spec, KeyProperties.KEY_ALGORITHM_HMAC_SHA256);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            try {
                assertNull(keyStore.getCertificateChain(keystoreAlias));
            } finally {
                keyStore.deleteEntry(keystoreAlias);
            }
        }
    }

    private void testRsaAttestations(int keySize, byte[] challenge, int purpose,
            String[][] paddingModes, boolean devicePropertiesAttestation) throws Exception {
        for (String[] paddings : paddingModes) {
            try {
                testRsaAttestation(challenge, true /* includeValidityDates */, keySize, purpose,
                        paddings, devicePropertiesAttestation);
                testRsaAttestation(challenge, false /* includeValidityDates */, keySize, purpose,
                        paddings, devicePropertiesAttestation);
            } catch (Throwable e) {
                if (devicePropertiesAttestation && (e.getCause() instanceof KeyStoreException)
                        && KM_ERROR_CANNOT_ATTEST_IDS ==
                                ((KeyStoreException) e.getCause()).getErrorCode()) {
                    Log.i(TAG, "key attestation with device IDs not supported; test skipped");
                    continue;
                }
                throw new Exception("Failed on key size " + keySize + " challenge [" +
                        new String(challenge) + "], purposes " +
                        buildPurposeSet(purpose) + " paddings " +
                        ImmutableSet.copyOf(paddings) + " and devicePropertiesAttestation "
                        + devicePropertiesAttestation,
                        e);
            }
        }
    }

    public void testDeviceIdAttestation() throws Exception {
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_SERIAL, null);
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_IMEI, "Unable to retrieve IMEI");
        testDeviceIdAttestationFailure(AttestationUtils.ID_TYPE_MEID, "Unable to retrieve MEID");
    }

    @SuppressWarnings("deprecation")
    private void testRsaAttestation(byte[] challenge, boolean includeValidityDates, int keySize,
            int purposes, String[] paddingModes, boolean devicePropertiesAttestation)
            throws Exception {
        Log.i(TAG, "RSA key attestation with: challenge " + Arrays.toString(challenge) +
                " / includeValidityDates " + includeValidityDates + " / keySize " + keySize +
                " / purposes " + purposes + " / paddingModes " + Arrays.toString(paddingModes) +
                " / devicePropertiesAttestation " + devicePropertiesAttestation);

        String keystoreAlias = "test_key";
        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder =
            new KeyGenParameterSpec.Builder(keystoreAlias, purposes)
                        .setKeySize(keySize)
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge)
                        .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }
        if (isEncryptionPurpose(purposes)) {
            builder.setEncryptionPaddings(paddingModes);
            // Because we sometimes set "no padding", allow non-randomized encryption.
            builder.setRandomizedEncryptionRequired(false);
        }
        if (isSignaturePurpose(purposes)) {
            builder.setSignaturePaddings(paddingModes);
        }

        generateKeyPair(KEY_ALGORITHM_RSA, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, false /* expectStrongBox */);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);

            checkRsaKeyDetails(attestation, keySize, purposes,
                ImmutableSet.copyOf(paddingModes));
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime,
                includeValidityDates, devicePropertiesAttestation, attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkKeyUsage(X509Certificate attestationCert, int purposes) {

        boolean[] expectedKeyUsage = new boolean[KEY_USAGE_BITSTRING_LENGTH];
        if (isSignaturePurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_DIGITAL_SIGNATURE_BIT_OFFSET] = true;
        }
        if (isEncryptionPurpose(purposes)) {
            expectedKeyUsage[KEY_USAGE_KEY_ENCIPHERMENT_BIT_OFFSET] = true;
            expectedKeyUsage[KEY_USAGE_DATA_ENCIPHERMENT_BIT_OFFSET] = true;
        }
        assertThat(attestationCert.getKeyUsage(), is(expectedKeyUsage));
    }

    @SuppressWarnings("deprecation")
    private void testEcAttestation(byte[] challenge, boolean includeValidityDates, String ecCurve,
            int keySize, int purposes, boolean devicePropertiesAttestation) throws Exception {
        Log.i(TAG, "EC key attestation with: challenge " + Arrays.toString(challenge) +
                " / includeValidityDates " + includeValidityDates + " / ecCurve " + ecCurve +
                " / keySize " + keySize + " / purposes " + purposes +
                " / devicePropertiesAttestation " + devicePropertiesAttestation);

        String keystoreAlias = "test_key";
        Date startTime = new Date();
        Date originationEnd = new Date(startTime.getTime() + ORIGINATION_TIME_OFFSET);
        Date consumptionEnd = new Date(startTime.getTime() + CONSUMPTION_TIME_OFFSET);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(keystoreAlias,
                purposes)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(ecCurve))
                        .setDigests(DIGEST_NONE, DIGEST_SHA256, DIGEST_SHA512)
                        .setAttestationChallenge(challenge)
                        .setDevicePropertiesAttestationIncluded(devicePropertiesAttestation);

        if (includeValidityDates) {
            builder.setKeyValidityStart(startTime)
                    .setKeyValidityForOriginationEnd(originationEnd)
                    .setKeyValidityForConsumptionEnd(consumptionEnd);
        }

        generateKeyPair(KEY_ALGORITHM_EC, builder.build());

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        try {
            Certificate certificates[] = keyStore.getCertificateChain(keystoreAlias);
            verifyCertificateChain(certificates, false /* expectStrongBox */);

            X509Certificate attestationCert = (X509Certificate) certificates[0];
            Attestation attestation = Attestation.loadFromCertificate(attestationCert);

            checkEcKeyDetails(attestation, ecCurve, keySize);
            checkKeyUsage(attestationCert, purposes);
            checkKeyIndependentAttestationInfo(challenge, purposes, startTime,
                includeValidityDates, devicePropertiesAttestation, attestation);
        } finally {
            keyStore.deleteEntry(keystoreAlias);
        }
    }

    private void checkAttestationApplicationId(Attestation attestation)
            throws NoSuchAlgorithmException, NameNotFoundException {
        AttestationApplicationId aaid = null;
        int kmVersion = attestation.getKeymasterVersion();
        assertNull(attestation.getTeeEnforced().getAttestationApplicationId());
        aaid = attestation.getSoftwareEnforced().getAttestationApplicationId();

        if (kmVersion >= 3) {
            // must be present and correct
            assertNotNull(aaid);
            assertEquals(new AttestationApplicationId(getContext()), aaid);
        } else {
            // may be present and
            // must be correct if present
            if (aaid != null) {
                assertEquals(new AttestationApplicationId(getContext()), aaid);
            }
        }
    }

    private void checkAttestationDeviceProperties(boolean devicePropertiesAttestation,
            Attestation attestation) {
        final AuthorizationList keyDetailsList;
        final AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }

        if (devicePropertiesAttestation) {
            assertEquals(Build.BRAND, keyDetailsList.getBrand());
            assertEquals(Build.DEVICE, keyDetailsList.getDevice());
            assertEquals(Build.PRODUCT, keyDetailsList.getProduct());
            assertEquals(Build.MANUFACTURER, keyDetailsList.getManufacturer());
            assertEquals(Build.MODEL, keyDetailsList.getModel());
        } else {
            assertNull(keyDetailsList.getBrand());
            assertNull(keyDetailsList.getDevice());
            assertNull(keyDetailsList.getProduct());
            assertNull(keyDetailsList.getManufacturer());
            assertNull(keyDetailsList.getModel());
        }
        assertNull(nonKeyDetailsList.getBrand());
        assertNull(nonKeyDetailsList.getDevice());
        assertNull(nonKeyDetailsList.getProduct());
        assertNull(nonKeyDetailsList.getManufacturer());
        assertNull(nonKeyDetailsList.getModel());
    }

    private void checkAttestationNoUniqueIds(Attestation attestation) {
        assertNull(attestation.getTeeEnforced().getImei());
        assertNull(attestation.getTeeEnforced().getMeid());
        assertNull(attestation.getTeeEnforced().getSerialNumber());
        assertNull(attestation.getSoftwareEnforced().getImei());
        assertNull(attestation.getSoftwareEnforced().getMeid());
        assertNull(attestation.getSoftwareEnforced().getSerialNumber());
    }

    private void checkKeyIndependentAttestationInfo(byte[] challenge, int purposes, Date startTime,
            boolean includesValidityDates, boolean devicePropertiesAttestation,
            Attestation attestation) throws NoSuchAlgorithmException, NameNotFoundException {
        checkUnexpectedOids(attestation);
        checkAttestationSecurityLevelDependentParams(attestation);
        assertNotNull(attestation.getAttestationChallenge());
        assertThat(attestation.getAttestationChallenge(), is(challenge));
        // In EAT, this is null if not filled in. In ASN.1, this is an array with length 0.
        if (attestation.getUniqueId() != null) {
            assertEquals(0, attestation.getUniqueId().length);
        }
        checkPurposes(attestation, purposes);
        checkDigests(attestation,
                ImmutableSet.of(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256, KM_DIGEST_SHA_2_512));
        checkValidityPeriod(attestation, startTime, includesValidityDates);
        checkFlags(attestation);
        checkOrigin(attestation);
        checkAttestationApplicationId(attestation);
        checkAttestationDeviceProperties(devicePropertiesAttestation, attestation);
        checkAttestationNoUniqueIds(attestation);
    }

    private void checkUnexpectedOids(Attestation attestation) {
        assertThat("Attestations must not contain any extra data",
                attestation.getUnexpectedExtensionOids(), is(empty()));
    }

    private int getSystemPatchLevel() {
        Matcher matcher = OS_PATCH_LEVEL_STRING_PATTERN.matcher(Build.VERSION.SECURITY_PATCH);
        assertTrue(matcher.matches());
        String year_string = matcher.group(OS_PATCH_LEVEL_YEAR_GROUP_NAME);
        String month_string = matcher.group(OS_PATCH_LEVEL_MONTH_GROUP_NAME);
        int patch_level = Integer.parseInt(year_string) * 100 + Integer.parseInt(month_string);
        return patch_level;
    }

    private int getSystemOsVersion() {
        return parseSystemOsVersion(Build.VERSION.RELEASE);
    }

    private int parseSystemOsVersion(String versionString) {
        Matcher matcher = OS_VERSION_STRING_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            return 0;
        }

        int version = 0;
        String major_string = matcher.group(OS_MAJOR_VERSION_MATCH_GROUP_NAME);
        String minor_string = matcher.group(OS_MINOR_VERSION_MATCH_GROUP_NAME);
        String subminor_string = matcher.group(OS_SUBMINOR_VERSION_MATCH_GROUP_NAME);
        if (major_string != null) {
            version += Integer.parseInt(major_string) * 10000;
        }
        if (minor_string != null) {
            version += Integer.parseInt(minor_string) * 100;
        }
        if (subminor_string != null) {
            version += Integer.parseInt(subminor_string);
        }
        return version;
    }

    private void checkOrigin(Attestation attestation) {
        assertTrue("Origin must be defined",
                attestation.getSoftwareEnforced().getOrigin() != null ||
                        attestation.getTeeEnforced().getOrigin() != null);
        if (attestation.getKeymasterVersion() != 0) {
            assertTrue("Origin may not be defined in both SW and TEE, except on keymaster0",
                    attestation.getSoftwareEnforced().getOrigin() == null ||
                            attestation.getTeeEnforced().getOrigin() == null);
        }

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE) {
            assertThat(attestation.getSoftwareEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        } else if (attestation.getKeymasterVersion() == 0) {
            assertThat(attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_UNKNOWN));
        } else {
            assertThat(attestation.getTeeEnforced().getOrigin(), is(KM_ORIGIN_GENERATED));
        }
    }

    private void checkFlags(Attestation attestation) {
        assertFalse("All applications was not requested",
                attestation.getSoftwareEnforced().isAllApplications());
        assertFalse("All applications was not requested",
                attestation.getTeeEnforced().isAllApplications());
        assertFalse("Allow while on body was not requested",
                attestation.getSoftwareEnforced().isAllowWhileOnBody());
        assertFalse("Allow while on body was not requested",
                attestation.getTeeEnforced().isAllowWhileOnBody());
        assertNull("Auth binding was not requiested",
                attestation.getSoftwareEnforced().getUserAuthType());
        assertNull("Auth binding was not requiested",
                attestation.getTeeEnforced().getUserAuthType());
        assertTrue("noAuthRequired must be true",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        || attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("auth is either software or TEE",
                attestation.getSoftwareEnforced().isNoAuthRequired()
                        && attestation.getTeeEnforced().isNoAuthRequired());
        assertFalse("Software cannot implement rollback resistance",
                attestation.getSoftwareEnforced().isRollbackResistant());
    }

    private void checkValidityPeriod(Attestation attestation, Date startTime,
            boolean includesValidityDates) {
        AuthorizationList validityPeriodList;
        AuthorizationList nonValidityPeriodList;
        if (attestation.getTeeEnforced().getCreationDateTime() != null) {
            validityPeriodList = attestation.getTeeEnforced();
            nonValidityPeriodList = attestation.getSoftwareEnforced();
        } else {
            validityPeriodList = attestation.getSoftwareEnforced();
            nonValidityPeriodList = attestation.getTeeEnforced();
        }

        if (attestation.getKeymasterVersion() == 2) {
            Date creationDateTime = validityPeriodList.getCreationDateTime();

            assertNotNull(creationDateTime);
            assertNull(nonValidityPeriodList.getCreationDateTime());

            // We allow a little slop on creation times because the TEE/HAL may not be quite synced
            // up with the system.
            assertTrue("Test start time (" + startTime.getTime() + ") and key creation time (" +
                    creationDateTime.getTime() + ") should be close",
                    Math.abs(creationDateTime.getTime() - startTime.getTime()) <= 2000);
        }

        if (includesValidityDates) {
            Date activeDateTime = validityPeriodList.getActiveDateTime();
            Date originationExpirationDateTime = validityPeriodList.getOriginationExpireDateTime();
            Date usageExpirationDateTime = validityPeriodList.getUsageExpireDateTime();

            assertNotNull(activeDateTime);
            assertNotNull(originationExpirationDateTime);
            assertNotNull(usageExpirationDateTime);

            assertNull(nonValidityPeriodList.getActiveDateTime());
            assertNull(nonValidityPeriodList.getOriginationExpireDateTime());
            assertNull(nonValidityPeriodList.getUsageExpireDateTime());

            assertThat(originationExpirationDateTime.getTime(),
                    is(startTime.getTime() + ORIGINATION_TIME_OFFSET));
            assertThat(usageExpirationDateTime.getTime(),
                    is(startTime.getTime() + CONSUMPTION_TIME_OFFSET));
        }
    }

    private void checkDigests(Attestation attestation, Set<Integer> expectedDigests) {
        Set<Integer> softwareEnforcedDigests = attestation.getSoftwareEnforced().getDigests();
        Set<Integer> teeEnforcedDigests = attestation.getTeeEnforced().getDigests();

        if (softwareEnforcedDigests == null) {
            softwareEnforcedDigests = ImmutableSet.of();
        }
        if (teeEnforcedDigests == null) {
            teeEnforcedDigests = ImmutableSet.of();
        }

        Set<Integer> allDigests = ImmutableSet.<Integer> builder()
                .addAll(softwareEnforcedDigests)
                .addAll(teeEnforcedDigests)
                .build();
        Set<Integer> intersection = new ArraySet<>();
        intersection.addAll(softwareEnforcedDigests);
        intersection.retainAll(teeEnforcedDigests);

        assertThat(allDigests, is(expectedDigests));
        assertTrue("Digest sets must be disjoint", intersection.isEmpty());

        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Digests in software-enforced",
                    softwareEnforcedDigests, is(expectedDigests));
        } else {
            if (attestation.getKeymasterVersion() == 1) {
                // KM1 implementations may not support SHA512 in the TEE
                assertTrue(softwareEnforcedDigests.contains(KM_DIGEST_SHA_2_512)
                        || teeEnforcedDigests.contains(KM_DIGEST_SHA_2_512));

                assertThat(teeEnforcedDigests, hasItems(KM_DIGEST_NONE, KM_DIGEST_SHA_2_256));
            } else {
                assertThat(teeEnforcedDigests, is(expectedDigests));
            }
        }
    }

    private Set<Integer> checkPurposes(Attestation attestation, int purposes) {
        Set<Integer> expectedPurposes = buildPurposeSet(purposes);
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE
                || attestation.getKeymasterVersion() == 0) {
            assertThat("Purposes in software-enforced should match expected set",
                    attestation.getSoftwareEnforced().getPurposes(), is(expectedPurposes));
            assertNull("Should be no purposes in TEE-enforced",
                    attestation.getTeeEnforced().getPurposes());
        } else {
            assertThat("Purposes in TEE-enforced should match expected set",
                    attestation.getTeeEnforced().getPurposes(), is(expectedPurposes));
            assertNull("No purposes in software-enforced",
                    attestation.getSoftwareEnforced().getPurposes());
        }
        return expectedPurposes;
    }

    private boolean isGsiImage() {
        final File initGsiRc= new File("/system/system_ext/etc/init/init.gsi.rc");
        return initGsiRc.exists();
    }

    private void checkSystemPatchLevel(int teeOsPatchLevel, int systemPatchLevel) {
        if (isGsiImage()) {
            // b/168663786: When using a GSI image, the system patch level might be
            // greater than or equal to the OS patch level reported from TEE.
            assertThat(teeOsPatchLevel, lessThanOrEqualTo(systemPatchLevel));
        } else {
            assertThat(teeOsPatchLevel, is(systemPatchLevel));
        }
    }

    @SuppressWarnings("unchecked")
    private void checkAttestationSecurityLevelDependentParams(Attestation attestation) {
        assertThat("Attestation version must be 1, 2, 3, 4 or 100",
                attestation.getAttestationVersion(),
                either(is(1)).or(is(2)).or(is(3)).or(is(4)).or(is(100)));

        AuthorizationList teeEnforced = attestation.getTeeEnforced();
        AuthorizationList softwareEnforced = attestation.getSoftwareEnforced();

        int systemOsVersion = getSystemOsVersion();
        int systemPatchLevel = getSystemPatchLevel();

        switch (attestation.getAttestationSecurityLevel()) {
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                assertThat("TEE attestation can only come from TEE keymaster",
                        attestation.getKeymasterSecurityLevel(),
                        is(KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT));
                assertThat(attestation.getKeymasterVersion(),
                        either(is(2)).or(is(3)).or(is(4)).or(is(41)).or(is(100)));

                checkRootOfTrust(attestation, false /* requireLocked */);
                assertThat(teeEnforced.getOsVersion(), is(systemOsVersion));
                checkSystemPatchLevel(teeEnforced.getOsPatchLevel(), systemPatchLevel);
                break;

            case KM_SECURITY_LEVEL_SOFTWARE:
                if (attestation
                        .getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
                    assertThat("TEE KM version must be 0 or 1 with software attestation",
                            attestation.getKeymasterVersion(), either(is(0)).or(is(1)));
                } else {
                    assertThat("Software KM is version 3", attestation.getKeymasterVersion(),
                            is(3));
                    assertThat(softwareEnforced.getOsVersion(), is(systemOsVersion));
                    checkSystemPatchLevel(softwareEnforced.getOsPatchLevel(), systemPatchLevel);
                }

                assertNull("Software attestation cannot provide root of trust",
                        teeEnforced.getRootOfTrust());

                break;

            default:
                fail("Invalid attestation security level: "
                        + attestation.getAttestationSecurityLevel());
                break;
        }

        assertNull("Software-enforced list must not contain root of trust",
                softwareEnforced.getRootOfTrust());
    }

    private void checkDeviceLocked(Attestation attestation) {
        assertThat("Attestation version must be >= 1",
                attestation.getAttestationVersion(), greaterThanOrEqualTo(1));

        int attestationSecurityLevel = attestation.getAttestationSecurityLevel();
        switch (attestationSecurityLevel) {
            case KM_SECURITY_LEVEL_STRONG_BOX:
            case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
                assertThat("Attestation security level doesn't match keymaster security level",
                        attestation.getKeymasterSecurityLevel(), is(attestationSecurityLevel));
                assertThat(attestation.getKeymasterVersion(), greaterThanOrEqualTo(2));

                // Devices launched in Android 10.0 (API level 29) and after should run CTS
                // in LOCKED state.
                boolean requireLocked = (
                        SystemProperties.getInt("ro.product.first_api_level", 0) >= 29);
                checkRootOfTrust(attestation, requireLocked);
                break;

            case KM_SECURITY_LEVEL_SOFTWARE:
            default:
                // TEE attestation has been required since Android 7.0.
                fail("Unexpected attestation security level: " +
                     attestation.securityLevelToString(attestationSecurityLevel));
                break;
        }
    }

    private void checkRootOfTrust(Attestation attestation, boolean requireLocked) {
        RootOfTrust rootOfTrust = attestation.getRootOfTrust();
        assertNotNull(rootOfTrust);
        assertNotNull(rootOfTrust.getVerifiedBootKey());
        assertTrue("Verified boot key is only " + rootOfTrust.getVerifiedBootKey().length +
                   " bytes long", rootOfTrust.getVerifiedBootKey().length >= 32);
        if (requireLocked) {
            assertTrue(rootOfTrust.isDeviceLocked());
            checkEntropy(rootOfTrust.getVerifiedBootKey());
            assertEquals(KM_VERIFIED_BOOT_VERIFIED, rootOfTrust.getVerifiedBootState());
        }
    }

    private void checkEntropy(byte[] verifiedBootKey) {
        assertTrue("Failed Shannon entropy check", checkShannonEntropy(verifiedBootKey));
        assertTrue("Failed BiEntropy check", checkTresBiEntropy(verifiedBootKey));
    }

    private boolean checkShannonEntropy(byte[] verifiedBootKey) {
        double probabilityOfSetBit = countSetBits(verifiedBootKey) / (double)(verifiedBootKey.length * 8);
        return calculateShannonEntropy(probabilityOfSetBit) > 0.8;
    }

    private double calculateShannonEntropy(double probabilityOfSetBit) {
        if (probabilityOfSetBit <= 0.001 || probabilityOfSetBit >= .999) return 0;
        double entropy = (-probabilityOfSetBit * logTwo(probabilityOfSetBit)) -
                            ((1 - probabilityOfSetBit) * logTwo(1 - probabilityOfSetBit));
        Log.i(TAG, "Shannon entropy of VB Key: " + entropy);
        return entropy;
    }

    private boolean checkTresBiEntropy(byte[] verifiedBootKey) {
        double weightingFactor = 0;
        double weightedEntropy = 0;
        double probabilityOfSetBit = 0;
        int length = verifiedBootKey.length * 8;
        for(int i = 0; i < (verifiedBootKey.length * 8) - 2; i++) {
            probabilityOfSetBit = countSetBits(verifiedBootKey) / (double)length;
            weightingFactor += logTwo(i+2);
            weightedEntropy += calculateShannonEntropy(probabilityOfSetBit) * logTwo(i+2);
            deriveBitString(verifiedBootKey, length);
            length -= 1;
        }
        double tresBiEntropy = (1 / weightingFactor) * weightedEntropy;
        Log.i(TAG, "BiEntropy of VB Key: " + tresBiEntropy);
        return tresBiEntropy > 0.9;
    }

    private void deriveBitString(byte[] bitString, int activeLength) {
        int length = activeLength / 8;
        if (activeLength % 8 != 0) {
            length += 1;
        }

        byte mask = (byte)((byte)0x80 >>> ((activeLength + 6) % 8));
        if (activeLength % 8 == 1) {
            mask = (byte)~mask;
        }

        for(int i = 0; i < length; i++) {
            if (i == length - 1) {
                bitString[i] ^= ((bitString[i] & 0xFF) << 1);
                bitString[i] &= mask;
            } else {
                bitString[i] ^= ((bitString[i] & 0xFF) << 1) | ((bitString[i+1] & 0xFF) >>> 7);
            }
        }
    }

    private double logTwo(double value) {
        return Math.log(value) / Math.log(2);
    }

    private int countSetBits(byte[] toCount) {
        int setBitCount = 0;
        for(int i = 0; i < toCount.length; i++) {
            setBitCount += countSetBits(toCount[i]);
        }
        return setBitCount;
    }

    private int countSetBits(byte toCount) {
        int setBitCounter = 0;
        while(toCount != 0) {
            toCount &= (toCount - 1);
            setBitCounter++;
        }
        return setBitCounter;
    }

    private void checkRsaKeyDetails(Attestation attestation, int keySize, int purposes,
            Set<String> expectedPaddingModes) throws CertificateParsingException {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());

        assertEquals(KM_ALGORITHM_RSA, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());

        assertNull(keyDetailsList.getEcCurve());
        assertNull(nonKeyDetailsList.getEcCurve());

        assertEquals(65537, keyDetailsList.getRsaPublicExponent().longValue());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());

        Set<String> paddingModes;
        if (attestation.getKeymasterVersion() == 0) {
            // KM0 implementations don't support padding info, so it's always in the
            // software-enforced list.
            paddingModes = attestation.getSoftwareEnforced().getPaddingModesAsStrings();
            assertNull(attestation.getTeeEnforced().getPaddingModes());
        } else {
            paddingModes = keyDetailsList.getPaddingModesAsStrings();
            assertNull(nonKeyDetailsList.getPaddingModes());
        }

        // KM1 implementations may add ENCRYPTION_PADDING_NONE to the list of paddings.
        Set<String> km1PossiblePaddingModes = expectedPaddingModes;
        if (attestation.getKeymasterVersion() == 1 &&
                attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            builder.addAll(expectedPaddingModes);
            builder.add(ENCRYPTION_PADDING_NONE);
            km1PossiblePaddingModes = builder.build();
        }

        assertThat(paddingModes, either(is(expectedPaddingModes)).or(is(km1PossiblePaddingModes)));
    }

    private void checkEcKeyDetails(Attestation attestation, String ecCurve, int keySize) {
        AuthorizationList keyDetailsList;
        AuthorizationList nonKeyDetailsList;
        if (attestation.getKeymasterSecurityLevel() == KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT) {
            keyDetailsList = attestation.getTeeEnforced();
            nonKeyDetailsList = attestation.getSoftwareEnforced();
        } else {
            keyDetailsList = attestation.getSoftwareEnforced();
            nonKeyDetailsList = attestation.getTeeEnforced();
        }
        assertEquals(keySize, keyDetailsList.getKeySize().intValue());
        assertNull(nonKeyDetailsList.getKeySize());
        assertEquals(KM_ALGORITHM_EC, keyDetailsList.getAlgorithm().intValue());
        assertNull(nonKeyDetailsList.getAlgorithm());
        assertEquals(ecCurve, keyDetailsList.ecCurveAsString());
        assertNull(nonKeyDetailsList.getEcCurve());
        assertNull(keyDetailsList.getRsaPublicExponent());
        assertNull(nonKeyDetailsList.getRsaPublicExponent());
        assertNull(keyDetailsList.getPaddingModes());
        assertNull(nonKeyDetailsList.getPaddingModes());
    }

    private boolean isEncryptionPurpose(int purposes) {
        return (purposes & PURPOSE_DECRYPT) != 0 || (purposes & PURPOSE_ENCRYPT) != 0;
    }

    private boolean isSignaturePurpose(int purposes) {
        return (purposes & PURPOSE_SIGN) != 0 || (purposes & PURPOSE_VERIFY) != 0;
    }

    private ImmutableSet<Integer> buildPurposeSet(int purposes) {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        if ((purposes & PURPOSE_SIGN) != 0)
            builder.add(KM_PURPOSE_SIGN);
        if ((purposes & PURPOSE_VERIFY) != 0)
            builder.add(KM_PURPOSE_VERIFY);
        if ((purposes & PURPOSE_ENCRYPT) != 0)
            builder.add(KM_PURPOSE_ENCRYPT);
        if ((purposes & PURPOSE_DECRYPT) != 0)
            builder.add(KM_PURPOSE_DECRYPT);
        return builder.build();
    }

    private void generateKey(KeyGenParameterSpec spec, String algorithm)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm, "AndroidKeyStore");
        keyGenerator.init(spec);
        keyGenerator.generateKey();
    }

    private void generateKeyPair(String algorithm, KeyGenParameterSpec spec)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm,
                "AndroidKeyStore");
        keyPairGenerator.initialize(spec);
        keyPairGenerator.generateKeyPair();
    }

    public static void verifyCertificateChain(Certificate[] certChain, boolean expectStrongBox)
            throws GeneralSecurityException {
        assertNotNull(certChain);
        for (int i = 1; i < certChain.length; ++i) {
            try {
                PublicKey pubKey = certChain[i].getPublicKey();
                certChain[i - 1].verify(pubKey);
                if (i == certChain.length - 1) {
                    // Last cert should be self-signed.
                    certChain[i].verify(pubKey);
                }

                // Check that issuer in the signed cert matches subject in the signing cert.
                X509Certificate x509CurrCert = (X509Certificate) certChain[i];
                X509Certificate x509PrevCert = (X509Certificate) certChain[i - 1];
                X500Name signingCertSubject =
                        new JcaX509CertificateHolder(x509CurrCert).getSubject();
                X500Name signedCertIssuer =
                        new JcaX509CertificateHolder(x509PrevCert).getIssuer();
                // Use .toASN1Object().equals() rather than .equals() because .equals() is case
                // insensitive, and we want to verify an exact match.
                assertTrue(
                        signedCertIssuer.toASN1Object().equals(signingCertSubject.toASN1Object()));

                X500Name signedCertSubject =
                        new JcaX509CertificateHolder(x509PrevCert).getSubject();
                if (i == 1) {
                    // First cert should have subject "CN=Android Keystore Key".
                    assertEquals(signedCertSubject, new X500Name("CN=Android Keystore Key"));
                } else {
                    // Only strongbox implementations should have strongbox in the subject line
                    assertEquals(expectStrongBox, signedCertSubject.toString()
                                                                   .toLowerCase()
                                                                   .contains("strongbox"));
                }
            } catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
                    | NoSuchProviderException | SignatureException e) {
                throw new GeneralSecurityException("Using StrongBox: " + expectStrongBox + "\n"
                        + "Failed to verify certificate "
                        + certChain[i - 1] + " with public key " + certChain[i].getPublicKey(), e);
            }
        }
    }

    private void testDeviceIdAttestationFailure(int idType,
            String acceptableDeviceIdAttestationFailureMessage) throws Exception {
        try {
            AttestationUtils.attestDeviceIds(getContext(), new int[] {idType}, "123".getBytes());
            fail("Attestation should have failed.");
        } catch (SecurityException e) {
            // Attestation is expected to fail. If the device has the device ID type we are trying
            // to attest, it should fail with a SecurityException as we do not hold
            // READ_PRIVILEGED_PHONE_STATE permission.
        } catch (DeviceIdAttestationException e) {
            // Attestation is expected to fail. If the device does not have the device ID type we
            // are trying to attest (e.g. no IMEI on devices without a radio), it should fail with
            // a corresponding DeviceIdAttestationException.
            if (acceptableDeviceIdAttestationFailureMessage == null ||
                    !acceptableDeviceIdAttestationFailureMessage.equals(e.getMessage())) {
                throw e;
            }
        }
    }
}
