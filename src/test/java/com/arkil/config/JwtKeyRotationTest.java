package com.arkil.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyRotationTest {

    @Test
    void signsWithActivePrivateKeyWhenPreviousPublicKeyRemainsPublished() throws Exception {
        RSAKey active = key("active");
        RSAKey previousPublic = key("previous").toPublicJWK();
        var config = new AuthorizationServerConfig(null, null, new MockEnvironment());
        ReflectionTestUtils.setField(config, "configuredJwkSetJson",
                new JWKSet(java.util.List.of(active, previousPublic)).toString(false));
        var encoder = config.jwtEncoder(config.jwkSource());

        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().subject("user").issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60)).build())).getTokenValue();

        assertThat(token).isNotBlank();
    }

    @Test
    void productionRequiresPersistentSigningKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var config = new AuthorizationServerConfig(null, null, environment);

        assertThatThrownBy(config::jwkSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARKIL_JWT_JWK_SET_JSON");
    }

    private static RSAKey key(String id) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(id).build();
    }
}
