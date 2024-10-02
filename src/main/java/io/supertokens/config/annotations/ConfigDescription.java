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

package io.supertokens.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide a description for a configuration fields. To be used on the fields of `CoreConfig` and config
 * class in the plugin like `PostgreSQLConfig`, `MysqlConfig`, etc.
 */
@Retention(RetentionPolicy.RUNTIME)
// Make annotation accessible at runtime so that config descriptions can be read from API
@Target(ElementType.FIELD) // Annotation can only be applied to fields
public @interface ConfigDescription {
    String value(); // String value that provides a description for the configuration field
}
