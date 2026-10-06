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

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Every general-pool transaction runs at the pool's default level (READ COMMITTED).
 *
 * <p>The sites that used to ask for REPEATABLE READ either lock the row they update with
 * {@code SELECT ... FOR UPDATE} (session verify on CDI &lt; 5.6, OAuth non-rotating refresh) or
 * only insert (in-memory {@code createDeviceWithCode}). Under READ COMMITTED a racing writer waits
 * on the row lock and reads the latest row; under REPEATABLE READ it gets a serialization failure
 * and goes through the retry loop. A non-default level also makes the storage set and reset the
 * connection's isolation per transaction.
 */
public class TransactionIsolationLevelGuardTest {

    private static final Pattern NON_DEFAULT_LEVEL = Pattern.compile(
            "TransactionIsolationLevel\\s*\\.\\s*(REPEATABLE_READ|SERIALIZABLE|READ_UNCOMMITTED|NONE)\\b");

    // A wildcard static import would let a bare REPEATABLE_READ through the pattern above.
    private static final Pattern WILDCARD_STATIC_IMPORT = Pattern.compile(
            "import\\s+static\\s+[\\w.]*TransactionIsolationLevel\\s*\\.\\s*\\*");

    @Test
    public void noCallSiteRequestsANonDefaultIsolationLevel() throws Exception {
        List<String> offenders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File root : sourceRoots()) {
            files.addAll(javaFilesUnder(root));
        }
        for (File f : files) {
            String[] lines = Files.readString(f.toPath(), StandardCharsets.UTF_8).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (NON_DEFAULT_LEVEL.matcher(lines[i]).find()
                        || WILDCARD_STATIC_IMPORT.matcher(lines[i]).find()) {
                    offenders.add(f.getName() + ":" + (i + 1));
                }
            }
        }
        assertTrue("Transactions must run at the default isolation level (READ COMMITTED); use "
                        + "SELECT ... FOR UPDATE to serialise writers instead. Found: " + offenders,
                offenders.isEmpty());
    }

    private static List<File> javaFilesUnder(File dir) {
        List<File> out = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            return out;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                out.addAll(javaFilesUnder(c));
            } else if (c.getName().endsWith(".java")) {
                out.add(c);
            }
        }
        return out;
    }

    // Same lookup as AuditEnforcementBaselineTest: walk up from the compiled test class so the
    // test works from any working directory, then fall back to a cwd-relative path. The ee module
    // is scanned too when present.
    private static List<File> sourceRoots() throws Exception {
        File repoRoot = repoRoot();
        List<File> roots = new ArrayList<>();
        roots.add(new File(repoRoot, "src/main/java"));
        File ee = new File(repoRoot, "ee/src/main/java");
        if (ee.isDirectory()) {
            roots.add(ee);
        }
        return roots;
    }

    private static File repoRoot() throws Exception {
        File start = new File(TransactionIsolationLevelGuardTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        for (File d = start; d != null; d = d.getParentFile()) {
            if (new File(d, "src/main/java/io/supertokens").isDirectory()) {
                return d;
            }
        }
        File cwd = new File("").getAbsoluteFile();
        if (new File(cwd, "src/main/java/io/supertokens").isDirectory()) {
            return cwd;
        }
        throw new IllegalStateException("could not locate src/main/java/io/supertokens from "
                + start + " or " + cwd);
    }
}
