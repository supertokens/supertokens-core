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

package io.supertokens.inmemorydb;

import java.util.HashSet;
import java.util.Set;

class Lock {

    private Set<String> lockedKeys = new HashSet<>();
    private final Object waitMutex = new Object();

    void lock(String name) {
        synchronized (this.waitMutex) {
            while (true) {
                if (!this.lockedKeys.contains(name)) {
                    this.lockedKeys.add(name);
                    return;
                }

                try {
                    this.waitMutex.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    void unlock(String name) {
        synchronized (this.waitMutex) {
            this.lockedKeys.remove(name);
            this.waitMutex.notifyAll();
        }
    }

}
