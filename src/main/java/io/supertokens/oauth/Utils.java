package io.supertokens.oauth;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class Utils {
    public static Map<String, String> getQueryParamsMapFromJsonObject(JsonObject jsonObject) {
        Map<String, String> queryParams = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            queryParams.put(entry.getKey(), entry.getValue().getAsString());
        }
        return queryParams;
    }

    public static Map<String, String> getBodyParamsMapFromJsonObject(JsonObject bodyFromSdk) {
        Map<String, String> bodyParams = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : bodyFromSdk.entrySet()) {
            bodyParams.put(entry.getKey(), entry.getValue().getAsString());
        }
        return bodyParams;
    }
}
