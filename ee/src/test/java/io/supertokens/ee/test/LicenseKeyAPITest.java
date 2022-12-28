package io.supertokens.ee.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ee.test.httpRequest.HttpRequestForTesting;
import io.supertokens.ee.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.WebserverAPI;
import org.junit.*;
import org.junit.rules.TestRule;

public class LicenseKeyAPITest {
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
    public void testBadInputForLicenseKeyAPI() throws Exception {
        String[] args = {"../../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // test passing licenseKey as an invalid type
        JsonObject resquestBody = new JsonObject();
        resquestBody.addProperty("test", 10);

        Exception error = null;
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/ee/license",
                    null, 1000, 1000, null, WebserverAPI.getLatestCDIVersion(), "");
        } catch (HttpResponseException e) {
            error = e;
        }

        Assert.assertNotNull(error);
        Assert.assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", error.getMessage());

        process.kill();
        Assert.assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
