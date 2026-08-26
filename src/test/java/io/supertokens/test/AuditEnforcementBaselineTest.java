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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Backstop for the compile-time audit-transaction guard ({@code AuditEnforcementAspect}).
 *
 * <p>The aspect is the real enforcement — it fails compilation on any raw
 * {@code SQLStorage.startTransaction(...)} in domain code that isn't allowlisted
 * with {@code @UnauditedTransaction}. This test covers what a {@code declare error}
 * pointcut cannot express:
 *
 * <ol>
 *   <li><b>Shrink-only allowlist.</b> The number of {@code @UnauditedTransaction}
 *       annotations may not grow past the baseline. Every new domain transaction
 *       is forced through {@code startAuditedTransaction(...)}; the legacy backlog
 *       burns down (baseline shrinks) but never expands. Lowering the baseline as
 *       sites are converted is expected and encouraged.</li>
 *   <li><b>{@code call()}-evasion.</b> A {@code declare error} on
 *       {@code call(... startTransaction(..))} does not match method references
 *       ({@code X::startTransaction}) or reflective invocation. Those are rare and
 *       greppable, so we forbid them outright in the guarded packages.</li>
 * </ol>
 */
public class AuditEnforcementBaselineTest {

    // Number of @UnauditedTransaction annotations across src/main. SHRINK-ONLY:
    // reduce this when a site is converted to startAuditedTransaction; never raise it.
    // A new unaudited transaction should be converted, not added to the allowlist.
    private static final int UNAUDITED_TRANSACTION_BASELINE = 60;

    // Mechanism files that legitimately contain the annotation's name (its declaration
    // and the aspect's error-message text) — not call-site usages.
    private static final List<String> MECHANISM_FILES = List.of(
            "UnauditedTransaction.java", "AuditEnforcementAspect.java");

    private static final Pattern USAGE =
            Pattern.compile("@UnauditedTransaction\\s*\\(\\s*justification");

    @Test
    public void unauditedTransactionAllowlistIsShrinkOnly() throws Exception {
        File root = sourceRoot();
        int count = 0;
        List<String> annotatedFiles = new ArrayList<>();
        for (File f : javaFilesUnder(root)) {
            if (MECHANISM_FILES.contains(f.getName())) {
                continue;
            }
            String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Matcher m = USAGE.matcher(src);
            int inFile = 0;
            while (m.find()) {
                inFile++;
            }
            if (inFile > 0) {
                count += inFile;
                annotatedFiles.add(f.getName() + " (" + inFile + ")");
            }
        }

        assertTrue("Found no @UnauditedTransaction annotations — the baseline test is not "
                        + "scanning source (source root: " + root + ")",
                count > 0);

        if (count > UNAUDITED_TRANSACTION_BASELINE) {
            fail("@UnauditedTransaction count grew to " + count + " (baseline "
                    + UNAUDITED_TRANSACTION_BASELINE + "). New domain transactions must use "
                    + "ActivityLogSQLStorage.startAuditedTransaction(...), not the allowlist. "
                    + "Annotated files: " + annotatedFiles);
        }
        // Keep the baseline honest: if the real count dropped, lower the constant.
        assertTrue("@UnauditedTransaction count is " + count + " but the baseline constant is "
                        + UNAUDITED_TRANSACTION_BASELINE + " — lower UNAUDITED_TRANSACTION_BASELINE "
                        + "to " + count + " to lock in the reduction.",
                count == UNAUDITED_TRANSACTION_BASELINE);
    }

    @Test
    public void noCallEvadingRawTransactionForms() throws Exception {
        File root = sourceRoot();
        // Method references and reflective lookups slip past the aspect's call() pointcut.
        Pattern methodRef = Pattern.compile("::\\s*startTransaction\\b");
        Pattern reflective = Pattern.compile(
                "get(Declared)?Method\\s*\\(\\s*\"startTransaction\"");
        List<String> offenders = new ArrayList<>();
        for (File f : javaFilesUnder(root)) {
            // The in-memory storage implements transactions; it is not domain code
            // and is excluded from the aspect (mirror that here).
            if (f.getPath().replace(File.separatorChar, '/').contains("/inmemorydb/")) {
                continue;
            }
            String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            if (methodRef.matcher(src).find() || reflective.matcher(src).find()) {
                offenders.add(f.getName());
            }
        }
        assertTrue("Found call()-evading startTransaction forms (method reference or reflection) "
                        + "in guarded code: " + offenders + ". Use startAuditedTransaction(...) or a "
                        + "direct call the aspect can see.",
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

    // Locate src/main/java/io/supertokens independently of the test's working directory
    // (the supertokens-root harness runs tests from a different cwd) by walking up from
    // the compiled test class location, then falling back to a cwd-relative path.
    private static File sourceRoot() throws Exception {
        File start = new File(AuditEnforcementBaselineTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        for (File d = start; d != null; d = d.getParentFile()) {
            File cand = new File(d, "src/main/java/io/supertokens");
            if (cand.isDirectory()) {
                return cand;
            }
        }
        File cwdCand = new File("src/main/java/io/supertokens");
        if (cwdCand.isDirectory()) {
            return cwdCand;
        }
        throw new IllegalStateException("could not locate src/main/java/io/supertokens from "
                + start + " or " + cwdCand.getAbsolutePath());
    }
}
