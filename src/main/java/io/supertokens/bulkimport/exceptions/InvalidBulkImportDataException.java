/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.bulkimport.exceptions;

import java.util.List;

public class InvalidBulkImportDataException extends Exception {
    private static final long serialVersionUID = 1L;
    public List<String> errors;

    public InvalidBulkImportDataException(List<String> errors) {
        super("Data has missing or invalid fields. Please check the errors field for more details.");
        this.errors = errors;
    }

    public void addError(String error) {
        this.errors.add(error);
    }
}
