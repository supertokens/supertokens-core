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

public class DeleteTenantMappingAPITest {

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

        String supertokensTenantId = "stId";
        String thirdPartyId = "tpId";
        JsonObject config = new JsonObject();
        config.addProperty("someKey", "someValue");

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("supertokensTenantId", supertokensTenantId);
            requestBody.addProperty("thirdPartyId", thirdPartyId);
            requestBody.add("config", config);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("created").getAsBoolean());
            assertFalse(response.get("update").getAsBoolean());
        }

        ThirdPartyTenantConfig tpTenantConfig = StorageLayer.getThirdPartyStorage(process.main)
                .getThirdPartyTenantConfig(supertokensTenantId, thirdPartyId);
        assertEquals(config.toString(), tpTenantConfig.config);
        assertEquals(supertokensTenantId, tpTenantConfig.supertokensTenantId);
        assertEquals(thirdPartyId, tpTenantConfig.thirdPartyId);

        // call delete api when mapping exists
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("supertokensTenantId", supertokensTenantId);
            requestBody.addProperty("thirdPartyId", thirdPartyId);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config/remove", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didConfigExist").getAsBoolean());
        }

        {
            // call delete api when mapping does not exist
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("supertokensTenantId", supertokensTenantId);
            requestBody.addProperty("thirdPartyId", thirdPartyId);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config/remove", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_16ForTests(), "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didConfigExist").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
