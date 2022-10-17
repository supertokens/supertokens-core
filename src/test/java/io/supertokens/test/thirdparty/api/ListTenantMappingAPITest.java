package io.supertokens.test.thirdparty.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.thirdparty.ThirdPartyTenantConfig;
import io.supertokens.storageLayer.StorageLayer;

public class ListTenantMappingAPITest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testGoodInput() throws Exception {

        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        String supertokensTenantId = "stId";
        String thirdPartyId = "tpId";
        JsonObject config = new JsonObject();
        config.addProperty("someKey", "someValue");

        {
            requestBody.addProperty("supertokensTenantId", supertokensTenantId);
            requestBody.addProperty("thirdPartyId", thirdPartyId);
            requestBody.add("config", config);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("created").getAsBoolean());
            assertFalse(response.get("update").getAsBoolean());

            ThirdPartyTenantConfig tpTenantConfig = StorageLayer.getThirdPartyStorage(process.main)
                    .getThirdPartyTenantConfig(supertokensTenantId, thirdPartyId);
            assertEquals(config.toString(), tpTenantConfig.config);
            assertEquals(supertokensTenantId, tpTenantConfig.supertokensTenantId);
            assertEquals(thirdPartyId, tpTenantConfig.thirdPartyId);
        }

        // retrieve data with supertokensTenantId
        {
            HashMap<String, String> QUERY_PARAMS = new HashMap<>();
            QUERY_PARAMS.put("supertokensTenantId", supertokensTenantId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config/list", QUERY_PARAMS, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());
            JsonArray configs = response.get("configs").getAsJsonArray();
            assertEquals(1, configs.size());
            JsonObject configFromResponse = configs.get(0).getAsJsonObject();
            assertEquals(supertokensTenantId, configFromResponse.get("supertokensTenantId").getAsString());
            assertEquals(thirdPartyId, configFromResponse.get("thirdPartyId").getAsString());
            assertEquals(config, configFromResponse.get("config").getAsJsonObject());
        }

        // retrieve data with thirdPartyId
        {
            HashMap<String, String> QUERY_PARAMS = new HashMap<>();
            QUERY_PARAMS.put("thirdPartyId", thirdPartyId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config/list", QUERY_PARAMS, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());
            JsonArray configs = response.get("configs").getAsJsonArray();
            assertEquals(1, configs.size());
            JsonObject configFromResponse = configs.get(0).getAsJsonObject();
            assertEquals(supertokensTenantId, configFromResponse.get("supertokensTenantId").getAsString());
            assertEquals(thirdPartyId, configFromResponse.get("thirdPartyId").getAsString());
            assertEquals(config, configFromResponse.get("config").getAsJsonObject());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
