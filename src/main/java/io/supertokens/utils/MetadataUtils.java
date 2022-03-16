package io.supertokens.utils;

import com.google.gson.JsonObject;

public class MetadataUtils {
    public static void shallowMergeMetadataUpdate(JsonObject target, JsonObject update) {
        update.entrySet().forEach((entry) -> {
            target.remove(entry.getKey());
            if (!entry.getValue().isJsonNull()) {
                target.add(entry.getKey(), entry.getValue());
            }
        });
    }

}
