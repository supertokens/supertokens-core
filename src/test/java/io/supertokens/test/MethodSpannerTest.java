/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.telemetry.MethodSpanner;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MethodSpannerTest {

    private static final String API_KEY = "spanTestApiKeyValue0123456789abcdef";
    private static final String SAAS_SECRET = "spanTestSaasSecretValue0123456789abcdefghijklmnop";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testIssue114_methodSpannerDoesNotExportConfigSecrets() throws Exception {
        // The attribute builder records types only, plus values for an allow-list of non-sensitive types
        JsonObject config = new JsonObject();
        config.addProperty("api_keys", API_KEY);
        config.addProperty("supertokens_saas_secret", SAAS_SECRET);
        config.addProperty("postgresql_password", "dbPasswordValue");
        config.addProperty("postgresql_connection_uri", "postgresql://user:dbPasswordValue@localhost:5432/db");

        String arguments = MethodSpanner.describeArguments(new Object[]{
                config, "aStringThatCouldBeAPassword", new TenantIdentifier("localhost", "app1", "t1"),
                new AppIdentifier(null, "app2"), true, 42L, null, new ArrayList<>(List.of("someSecretInAList")),
                new HashMap<>(Map.of("key", "someSecretInAMap")), new String[]{"someSecretInAnArray"}});

        for (String secret : new String[]{API_KEY, SAAS_SECRET, "dbPasswordValue", "aStringThatCouldBeAPassword",
                "someSecretInAList", "someSecretInAMap", "someSecretInAnArray", "api_keys", "postgresql_password"}) {
            assertFalse(arguments, arguments.contains(secret));
        }
        assertEquals("{0: com.google.gson.JsonObject, 1: java.lang.String, "
                + "2: io.supertokens.pluginInterface.multitenancy.TenantIdentifier -> localhost | app1 | t1, "
                + "3: io.supertokens.pluginInterface.multitenancy.AppIdentifier ->  | app2, "
                + "4: java.lang.Boolean -> true, 5: java.lang.Long -> 42, 6: null, "
                + "7: java.util.ArrayList (size: 1), "
                + "8: java.util.HashMap (size: 1), 9: java.lang.String[] (length: 1)}", arguments);
        assertEquals("com.google.gson.JsonObject", MethodSpanner.describeValue(config));

        // And nothing exported by a core started with a global SDK (as with the --javaagent) contains the secrets
        Utils.setValueInConfig("api_keys", API_KEY);
        Utils.setValueInConfig("supertokens_saas_secret", SAAS_SECRET);

        try (InMemoryOtelForTesting otel = InMemoryOtelForTesting.install()) {
            String[] args = {"../"};
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            // make sure the woven spans were actually exported, so the assertions below are meaningful
            assertTrue(otel.getSpans().stream()
                    .anyMatch(span -> span.getName().equals("io.supertokens.storageLayer.StorageLayer.getNewInstance")));

            String exported = otel.getAllExportedText();
            assertFalse(exported.contains(API_KEY));
            assertFalse(exported.contains(SAAS_SECRET));
            assertFalse(exported.contains("api_keys"));
            assertFalse(exported.contains("supertokens_saas_secret"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testIssue23_getNewInstanceSpanHasNoDbPassword() throws Exception {
        try (InMemoryOtelForTesting otel = InMemoryOtelForTesting.install()) {
            String[] args = {"../"};
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            List<SpanData> getNewInstanceSpans = otel.getSpans().stream()
                    .filter(span -> span.getName().equals("io.supertokens.storageLayer.StorageLayer.getNewInstance"))
                    .collect(Collectors.toList());
            assertFalse(getNewInstanceSpans.isEmpty());

            for (SpanData span : getNewInstanceSpans) {
                String arguments = span.getAttributes().get(AttributeKey.stringKey("method.arguments"));
                assertNotNull(arguments);
                // the config is recorded by type only
                assertTrue(arguments, arguments.contains("1: com.google.gson.JsonObject,"));
                assertTrue(arguments, arguments.contains("io.supertokens.pluginInterface.multitenancy.TenantIdentifier -> "));
                for (String sensitive : new String[]{"postgresql_password", "postgresql_user", "mysql_password",
                        "postgresql_connection_uri", "\"" + "root" + "\""}) {
                    assertFalse(arguments, arguments.contains(sensitive));
                }
                String returns = span.getAttributes().get(AttributeKey.stringKey("method.returns"));
                assertNotNull(returns);
                assertFalse(returns, returns.contains("root"));
            }

            String exported = otel.getAllExportedText();
            assertFalse(exported.contains("postgresql_password"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
