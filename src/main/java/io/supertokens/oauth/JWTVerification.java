package io.supertokens.oauth;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import io.supertokens.Main;
import io.supertokens.httpRequest.HttpRequest;

public class JWTVerification {

    public static JsonObject verifyJWTAndGetPayload(Main main, String token, String jwksUrl) {
        JwtParser jwtParser = Jwts.parser().keyLocator(new KeyLocatorImpl(main, jwksUrl)).build();
        Jws<Claims> jwtResult = jwtParser.parseSignedClaims(token);
        if (jwtResult == null) {
            throw new RuntimeException("Failed to verify JWT token");
        }
        Claims payload = jwtResult.getPayload();

        return new Gson().fromJson(new Gson().toJson(payload), JsonObject.class);
    }

    private static class KeyLocatorImpl implements Locator<Key> {
        private static final int CONNECTION_TIMEOUT = 5000;
        private static final int READ_TIMEOUT = 5000;
        private static final int MAX_RETRIES = 3; // Maximum number of retries for fetching JWKS

        private static Map<String, JsonObject> jwksCache = new HashMap<>(); // Cache for JWKS keys

        private final Main main;
        private final String jwksUrl;

        public KeyLocatorImpl(Main main, String jwksUrl) {
            this.main = main;
            this.jwksUrl = jwksUrl;
        }

        @Override
        public Key locate(Header header) {
            for (int i = 0; i < MAX_RETRIES; i++) {
                JsonObject jwksResponse = jwksCache.get(jwksUrl);
                if (jwksResponse == null) {
                    try {
                        jwksResponse = HttpRequest
                                .sendGETRequest(main, "", jwksUrl, null, CONNECTION_TIMEOUT, READ_TIMEOUT, null);
                        jwksCache.put(jwksUrl, jwksResponse); // Cache the fetched JWKS response
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to fetch JWKS keys for token verification", e);
                    }
                }

                Jwk<?> jwk = null;

                JsonArray keys = jwksResponse.get("keys").getAsJsonArray();

                for (JsonElement keyElement : keys) {
                    JsonObject keyObject = keyElement.getAsJsonObject();
                    if (keyObject.get("kid").getAsString().equals(header.get("kid"))) {
                        jwk = Jwks.parser().build().parse(keyObject.toString());
                        break;
                    }
                }

                if (jwk == null) {
                    jwksCache.remove(jwksUrl);
                    continue; // Retry
                }
                
                return jwk.toKey();
            }

            throw new RuntimeException("Failed to fetch JWKS keys for token verification");
        }
    }
}
