/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cli.commandHandler.hashingCalibrate;

import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.logging.Logging;

import java.util.ArrayList;
import java.util.List;

public class HashingCalibrateHandler extends CommandHandler {
    @Override
    protected void doCommand(String installationDir, boolean viaInstaller, String[] args) {
        String alg = CLIOptionsParser.parseOption("--with_alg", args);
        if (alg != null && alg.equalsIgnoreCase("bcrypt")) {
            String targetTimePerHashMsStr = CLIOptionsParser.parseOption("--with_time_per_hash_ms", args);
            int targetTimePerHashMs = 300;
            if (targetTimePerHashMsStr != null) {
                targetTimePerHashMs = Integer.parseInt(targetTimePerHashMsStr);
            }
            calibrateBCrypt(targetTimePerHashMs);
        } else if (alg != null && alg.equalsIgnoreCase("argon2")) {
            String targetTimePerHashMsStr = CLIOptionsParser.parseOption("--with_time_per_hash_ms", args);
            int targetTimePerHashMs = 300;
            if (targetTimePerHashMsStr != null) {
                targetTimePerHashMs = Integer.parseInt(targetTimePerHashMsStr);
            }

            String hashingPoolSizeStr = CLIOptionsParser.parseOption("--with_argon2_hashing_pool_size", args);
            int hashingPoolSize = 10;
            if (hashingPoolSizeStr != null) {
                hashingPoolSize = Integer.parseInt(hashingPoolSizeStr);
            }

            String maxMemoryMbStr = CLIOptionsParser.parseOption("--with_argon2_max_memory_mb", args);
            int maxMemoryMb = 1024;
            if (maxMemoryMbStr != null) {
                maxMemoryMb = Integer.parseInt(maxMemoryMbStr);
            }

            String parallelismStr = CLIOptionsParser.parseOption("--with_argon2_parallelism", args);
            int parallelism = Runtime.getRuntime().availableProcessors() * 2;
            if (parallelismStr != null) {
                parallelism = Integer.parseInt(parallelismStr);
            }
            calibrateArgon2Hashing(targetTimePerHashMs, hashingPoolSize, maxMemoryMb, parallelism);
        } else {
            Logging.error("Please provide one of --with_alg=argon2 or --with_alg=bcrypt");
        }
    }

    @Override
    protected List<Option> getOptionsAndDescription() {
        List<Option> options = new ArrayList<>();
        options.add(new Option("--with_alg",
                "The password hashing algorithm to calibrate with. Possible values are \"argon2\" and \"bcrypt\""));
        options.add(new Option("--with_time_per_hash_ms",
                "This value determines the desired time (in milliseconds) to calculate one hash. The default value is" +
                        " 300 MS."));
        options.add(new Option("--with_argon2_hashing_pool_size",
                "If calibrating argon2 hashing, this value will affect how many maximum hashes can be computed " +
                        "concurrently. The default value is 10"));
        options.add(new Option("--with_argon2_max_memory_mb",
                "If calibrating argon2, this value determines how much maximum memory (RAM), in MB, to use for " +
                        "password " +
                        "hashing. The default value is 1024 MB"));
        options.add(new Option("--with_argon2_parallelism",
                "If calibrating argon2, this value determines how many virtual cores to use for hashing. Learn more " +
                        "about this parameter here: https://tools.ietf.org/id/draft-irtf-cfrg-argon2-05.html#rfc" +
                        ".section.3.1. The defult value is 2*(number of cores in the system)"));
        return options;
    }

    @Override
    public String getShortDescription() {
        return "Used to calibrate the settings for password hashing algorithms for a specific machine";
    }

    @Override
    public String getUsage() {
        return "supertokens hashingCalibrate --with_alg=<argon2 | bcrypt> [--with_argon2_hashing_pool_size=10] " +
                "[--with_argon2_max_memory_mb=1024] [--with_argon2_parallelism=<value>] [--with_time_per_hash_ms=300]";
    }

    @Override
    public String getCommandName() {
        return "hashingCalibrate [options]";
    }

    private void calibrateBCrypt(int targetTimePerHashMs) {
        Logging.info("");
        Logging.info("====Input Settings====");
        Logging.info("-> Target time per hash (--with_time_per_hash_ms): " + targetTimePerHashMs + " MS");
        // TODO:
    }

    private void calibrateArgon2Hashing(int targetTimePerHashMs, int hashingPoolSize, int maxMemoryMb,
                                        int parallelism) {
        Logging.info("");
        Logging.info("====Input Settings====");
        Logging.info("-> Target time per hash (--with_time_per_hash_ms): " + targetTimePerHashMs + " MS");
        Logging.info("-> Number of max concurrent hashes (--with_argon2_hashing_pool_size): " + hashingPoolSize);
        Logging.info(
                "-> Max amount of memory to consume across " + hashingPoolSize +
                        " concurrent hashes (--with_argon2_max_memory_mb): " + maxMemoryMb +
                        " MB");
        Logging.info("-> Argon2 parallelism (--with_argon2_parallelism): " + parallelism);
        // TODO:
    }
}
