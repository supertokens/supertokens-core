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

package io.supertokens.emailpassword;

import org.mindrot.jbcrypt.BCrypt;

// code from: https://dzone.com/articles/hashing-passwords-in-java-with-bcrypt#:~:text=BCrypt%20Features&text=One
// %20way%20hashing%20%2D%20BCrypt%20is,hashes%20across%20each%20user's%20password.

public class UpdatableBCrypt {

    public static String hash(String password) {
        // the number of iterations is 2^ the number below.
        // https://security.stackexchange.com/a/83382/221626

        return BCrypt.hashpw(password, BCrypt.gensalt(11));
    }

    public static boolean verifyHash(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

}
