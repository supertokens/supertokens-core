/*
 *    Copyright (c) 2023, SuperTokens, Inc. All rights reserved.
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

package io.supertokens.downloader.cliParsers;

/**
 * install [--path <path location>] [--with-source]
 */
public class InstallOptionsParser {
    private static final String SOURCE_OPTION = "--with-source";
    private Boolean withSource = false;

    public InstallOptionsParser(String[] options) {
        for (String option : options) {
            if (option.equals(SOURCE_OPTION)) {
                this.withSource = true;
                return;
            }
        }
    }

    public Boolean installWithSource() {
        return this.withSource;
    }
}
