package io.supertokens.oauth;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class OAuthToken {
    public enum TokenType {
        ACCESS_TOKEN(1),
        ID_TOKEN(2),
        SAML_ID_TOKEN(3);

        private final int value;

        TokenType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private static Set<String> NON_OVERRIDABLE_TOKEN_PROPS = Set.of(
        "kid", "typ", "alg", "aud",
        "iss", "iat", "exp", "nbf", "jti", "ext",
        "sid", "rat", "at_hash", "gid",
        "client_id", "scp", "sub", "stt"
    );

    public static JsonObject getPayloadFromJWTToken(AppIdentifier appIdentifier,
            @Nonnull Main main, @Nonnull String token)
            throws TenantOrAppNotFoundException, TryRefreshTokenException, StorageQueryException,
            UnsupportedJWTSigningAlgorithmException, StorageTransactionLogicException {
        List<JWTSigningKeyInfo> keyInfoList = SigningKeys.getInstance(appIdentifier, main).getAllKeys();
        Exception error = null;
        JWT.JWTInfo jwtInfo = null;
        JWT.JWTPreParseInfo preParseJWTInfo = null;
        try {
            preParseJWTInfo = JWT.preParseJWTInfo(token);
        } catch (JWTException e) {
            // This basically should never happen, but it means, that the token structure is
            // wrong, can't verify
            throw new TryRefreshTokenException(e);
        }

        for (JWTSigningKeyInfo keyInfo : keyInfoList) {
            try {
                jwtInfo = JWT.verifyJWTAndGetPayload(preParseJWTInfo,
                        ((JWTAsymmetricSigningKeyInfo) keyInfo).publicKey);
                error = null;
                break;
            } catch (NoSuchAlgorithmException e) {
                // This basically should never happen, but it means, that can't verify any
                // tokens, no need to retry
                throw new TryRefreshTokenException(e);
            } catch (KeyException | JWTException e) {
                error = e;
            }
        }

        if (jwtInfo == null) {
            throw new TryRefreshTokenException(error);
        }

        if (jwtInfo.payload.get("exp").getAsLong() * 1000 < System.currentTimeMillis()) {
            throw new TryRefreshTokenException("Access token expired");
        }

        return jwtInfo.payload;
    }

    public static String reSignToken(AppIdentifier appIdentifier, Main main, String token, String iss, JsonObject payloadUpdate, String atHash, TokenType tokenType, boolean useDynamicSigningKey, int retryCount) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException,
            JWTCreationException {
        JsonObject payload = JWT.getPayloadWithoutVerifying(token).payload;

        payload.addProperty("iss", iss);
        payload.addProperty("stt", tokenType.getValue());
        if (atHash != null) {
            payload.addProperty("at_hash", atHash);
        }

        if (tokenType == TokenType.ACCESS_TOKEN) {
            // we need to move rsub, tId and sessionHandle from ext to root
            Transformations.transformExt(payload);
        } else {
            if (payload.has("ext")) {
                JsonObject ext = payload.get("ext").getAsJsonObject();
                payload.addProperty("sid", ext.get("sessionHandle").getAsString());
            }
        }

        // This should only happen in the authorization code flow during the token exchange. (enforced on the api level)
        // Other flows (including later calls using the refresh token) will have the payloadUpdate defined.
        if (payloadUpdate == null) {
            if (tokenType == TokenType.ACCESS_TOKEN) {
                if (payload.has("ext") && payload.get("ext").isJsonObject()) {
                    payloadUpdate = payload.getAsJsonObject("ext").getAsJsonObject("initialPayload");
                    payload.remove("ext");
                }
            } else {
                payloadUpdate = payload.getAsJsonObject("initialPayload");
            }
        }
        payload.remove("ext");
        payload.remove("initialPayload");

        // We ensure that the gid is there
        // If it isn't that means that we are in a client_credentials (M2M) flow
        if (!payload.has("gid")) {
            payload.addProperty("gid", UUID.randomUUID().toString());
        }

        if (payloadUpdate != null) {
            for (Map.Entry<String, JsonElement> entry : payloadUpdate.entrySet()) {
                if (!NON_OVERRIDABLE_TOKEN_PROPS.contains(entry.getKey())) {
                    payload.add(entry.getKey(), entry.getValue());
                }
            }
        }

        JWTSigningKeyInfo keyToUse;
        if (useDynamicSigningKey) {
            keyToUse = Utils.getJWTSigningKeyInfoFromKeyInfo(
                    SigningKeys.getInstance(appIdentifier, main).getLatestIssuedDynamicKey());
        } else {
            keyToUse = SigningKeys.getInstance(appIdentifier, main)
                    .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
        }

        token = JWTSigningFunctions.createJWTToken(JWTSigningKey.SupportedAlgorithms.RS256, new HashMap<>(),
                    payload, null, payload.get("exp").getAsLong(), payload.get("iat").getAsLong(), keyToUse);
        return token;
    }
}
