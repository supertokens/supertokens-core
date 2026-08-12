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

package io.supertokens.test.session;

import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Directly exercises {@link WebserverAPI#enforcePublicTenantFromVersion} - the CDI 5.6 public-tenant restriction
 * that app-specific session APIs (e.g. {@code /recipe/session/verify}) apply.
 *
 * <p>Core does not advertise CDI 5.6 over HTTP yet ({@code getLatestCDIVersion() == v5_5}), so the rejection leg
 * - a non-public tenant calling at CDI >= 5.6 -> {@link BadPermissionException} (403) - is unreachable through a
 * real request: {@code getVersionFromRequest} caps the negotiated version at the latest advertised one.
 * {@code MultitenantAPITest} therefore only guards the non-regression leg (non-public tenant still works at the
 * latest advertised version). This test pins the gate itself by overriding the version the request negotiates,
 * so the enforcement cannot silently break (e.g. an {@code &&} flipped to {@code ||}, a dropped tenant check, or
 * a mis-set version boundary) before 5.6 is advertised.
 */
public class PublicTenantGateTest extends Mockito {

    // A minimal WebserverAPI stub that lets the test choose the CDI version the request "negotiates" (bypassing
    // the latest-advertised cap that getVersionFromRequest would otherwise impose) and pins the API path so the
    // real getTenantId(req) path-parsing decides public vs non-public from the mocked servlet path.
    private static class GatedAPI extends WebserverAPI {
        private static final long serialVersionUID = 1L;
        private final SemVer negotiatedVersion;

        GatedAPI(SemVer negotiatedVersion) {
            super(null, "");
            this.negotiatedVersion = negotiatedVersion;
        }

        @Override
        public String getPath() {
            return "/recipe/session/verify";
        }

        @Override
        protected SemVer getVersionFromRequest(HttpServletRequest req) {
            return negotiatedVersion;
        }

        void callGate(HttpServletRequest req) throws Exception {
            enforcePublicTenantFromVersion(req, SemVer.v5_6);
        }
    }

    private static HttpServletRequest requestForPath(String servletPath) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getServletPath()).thenReturn(servletPath);
        return req;
    }

    @Test
    public void testNonPublicTenantAtGateVersionIsRejected() throws Exception {
        GatedAPI api = new GatedAPI(SemVer.v5_6);
        HttpServletRequest req = requestForPath("/appid-public/t1/recipe/session/verify");
        try {
            api.callGate(req);
            fail("expected BadPermissionException for a non-public tenant at CDI >= 5.6");
        } catch (BadPermissionException e) {
            assertEquals("Only public tenantId can call this app specific API", e.getMessage());
        }
    }

    @Test
    public void testPublicTenantAtGateVersionIsAllowed() throws Exception {
        GatedAPI api = new GatedAPI(SemVer.v5_6);
        // The default tenant on the public path -> getTenantId(req) == null -> no restriction.
        api.callGate(requestForPath("/recipe/session/verify"));
        api.callGate(requestForPath("/appid-public/public/recipe/session/verify"));
    }

    @Test
    public void testNonPublicTenantBelowGateVersionIsAllowed() throws Exception {
        // Non-regression leg: below the gate a non-public tenant is untouched (matches MultitenantAPITest, but
        // asserted here on the gate method directly rather than over HTTP).
        GatedAPI api = new GatedAPI(SemVer.v5_5);
        api.callGate(requestForPath("/appid-public/t1/recipe/session/verify"));
    }
}
