/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.core;

import io.supertokens.Main;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class NotFoundOrHelloAPI extends WebserverAPI {

    private static final long serialVersionUID = 1L;

    public NotFoundOrHelloAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/";
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getRequestURI().equals("/")) {
            Storage storage = StorageLayer.getStorage(main);
            try {
                storage.getKeyValue("Test");
                super.sendTextResponse(200, "Hello", resp);
            } catch (StorageQueryException e) {
                // we send 500 status code
                throw new IOException(e);
            }
        } else {
            super.sendTextResponse(404, "Not found", resp);

            Logging.error(main, "Unknown API called: " + req.getRequestURL(), false);
        }
    }

}
