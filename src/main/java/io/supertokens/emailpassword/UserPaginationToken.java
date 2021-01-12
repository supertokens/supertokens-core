/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.emailpassword;

import java.util.Base64;

public class UserPaginationToken {
    public final String userId;
    public final long timeJoined;

    public UserPaginationToken(String userId, long timeJoined) {
        this.userId = userId;
        this.timeJoined = timeJoined;
    }

    public static UserPaginationToken extractTokenInfo(String token) throws IllegalArgumentException {
        String decodedPaginationToken = new String(Base64.getDecoder().decode(token));
        String[] splitDecodedToken = decodedPaginationToken.split(";");
        if (splitDecodedToken.length != 2) {
            throw new IllegalArgumentException();
        }
        String userId = splitDecodedToken[0];
        long timeJoined = Long.parseLong(splitDecodedToken[1]);
        return new UserPaginationToken(userId, timeJoined);
    }

    public String generateToken() {
        return new String(Base64.getEncoder().encode((this.userId + ";" + this.timeJoined).getBytes()));
    }
}
