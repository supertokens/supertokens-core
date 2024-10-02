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

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.supertokens.cli.cliOptionsParsers.CLIOptionsParser;
import io.supertokens.cli.commandHandler.CommandHandler;
import io.supertokens.cli.logging.Logging;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            int hashingPoolSize = 1;
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
            try {
                calibrateArgon2Hashing(targetTimePerHashMs, hashingPoolSize, maxMemoryMb, parallelism);
            } catch (TooLowMemoryProvidedForArgon2 ignored) {
                Logging.error("");
                Logging.error("====FAILED====");
                Logging.error(
                        "Optimal Argon2 settings could not be calculated. Try increasing the amount of memory given "
                                + "(using --with_argon2_max_memory_mb), or reducing the amount of concurrent hashing "
                                + "(using --with_argon2_hashing_pool_size)");
            }
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
                "This value determines the desired time (in milliseconds) to calculate one hash. The default value is"
                        + " 300 MS."));
        options.add(new Option("--with_argon2_hashing_pool_size",
                "If calibrating argon2 hashing, this value will affect how many maximum hashes can be computed "
                        + "concurrently. The default value is 1"));
        options.add(new Option("--with_argon2_max_memory_mb",
                "If calibrating argon2, this value determines how much maximum memory (RAM), in MB, to use for "
                        + "password " + "hashing. The default value is 1024 MB"));
        options.add(new Option("--with_argon2_parallelism",
                "If calibrating argon2, this value determines how many virtual cores to use for hashing. Learn more "
                        + "about this parameter here: https://tools.ietf.org/id/draft-irtf-cfrg-argon2-05.html#rfc"
                        + ".section.3.1. The defult value is 2*(number of cores in the system)"));
        return options;
    }

    @Override
    public String getShortDescription() {
        return "Used to calibrate the settings for password hashing algorithms for a specific machine";
    }

    @Override
    public String getUsage() {
        return "supertokens hashingCalibrate --with_alg=<argon2 | bcrypt> [--with_argon2_hashing_pool_size=1] "
                +
                "[--with_argon2_max_memory_mb=1024] [--with_argon2_parallelism=<value>] [--with_time_per_hash_ms=300]";
    }

    @Override
    public String getCommandName() {
        return "hashingCalibrate [options]";
    }

    private void calibrateBCrypt(int targetTimePerHashMs) {
        Logging.info("");
        Logging.info(Logging.ANSI_CYAN + "====Input Settings====" + Logging.ANSI_RESET);
        Logging.info("-> Target time per hash (--with_time_per_hash_ms): " + targetTimePerHashMs + " MS");
        Logging.info("");
        Logging.info("");
        Logging.info(Logging.ANSI_CYAN + "====Running algorithm====" + Logging.ANSI_RESET);

        int currentLogRounds = 11;
        int previousIterationLogRound = currentLogRounds;
        long previousIterationTimeTaken = 0;

        long finalAvgTime;
        int finalLogRounds;
        while (true) {
            long currentTimeTaken = getApproxTimeForBcryptHashWith(currentLogRounds);
            Logging.info("Took " + currentTimeTaken + " MS per hash");
            Logging.info("");

            if (currentTimeTaken >= targetTimePerHashMs && previousIterationTimeTaken <= targetTimePerHashMs) {
                if (previousIterationTimeTaken == 0) {
                    // this means that the first iteration itself took more time than the target time
                    previousIterationTimeTaken = currentTimeTaken;
                    previousIterationLogRound = currentLogRounds;
                }
                if (currentTimeTaken - targetTimePerHashMs <= targetTimePerHashMs - previousIterationTimeTaken) {
                    finalAvgTime = currentTimeTaken;
                    finalLogRounds = currentLogRounds;
                } else {
                    finalAvgTime = previousIterationTimeTaken;
                    finalLogRounds = previousIterationLogRound;
                }
                break;
            }

            Logging.info("Incrementing log rounds and trying again...");

            previousIterationTimeTaken = currentTimeTaken;
            previousIterationLogRound = currentLogRounds;

            currentLogRounds++;
        }

        Logging.info(Logging.ANSI_GREEN + "====Final values====");
        Logging.info("Average time per hash is: " + finalAvgTime + " MS");
        Logging.info("");
        Logging.info("bcrypt_log_rounds: " + finalLogRounds);
        Logging.info("");
        Logging.info("====================" + Logging.ANSI_RESET);
        Logging.info(
                "You should use this as a docker env variable or put this in the config.yaml file in the SuperTokens "
                        + "installation directory.");
        Logging.info("");

    }

    private long getApproxTimeForBcryptHashWith(int logRounds) {
        Logging.info("");
        Logging.info("Current log rounds: " + logRounds);

        long avg = 0;
        int numberOfTries = 10;
        for (int i = 0; i < numberOfTries; i++) {
            long beforeTime = System.currentTimeMillis();
            BCrypt.hashpw("somePassword", BCrypt.gensalt(logRounds));
            avg += (System.currentTimeMillis() - beforeTime);
            Logging.infoNoNewLine(".");
        }

        return avg / numberOfTries;
    }

    private void calibrateArgon2Hashing(int targetTimePerHashMs, int hashingPoolSize, int maxMemoryMb, int parallelism)
            throws TooLowMemoryProvidedForArgon2 {
        Logging.info("");
        Logging.info(Logging.ANSI_CYAN + "====Input Settings====" + Logging.ANSI_RESET);
        Logging.info("");
        Logging.info("-> Target time per hash (--with_time_per_hash_ms): " + targetTimePerHashMs + " MS");
        Logging.info("-> Number of max concurrent hashes (--with_argon2_hashing_pool_size): " + hashingPoolSize);
        Logging.info("-> Max amount of memory to consume across " + hashingPoolSize
                + " concurrent hashes (--with_argon2_max_memory_mb): " + maxMemoryMb + " MB");
        Logging.info("-> Argon2 parallelism (--with_argon2_parallelism): " + parallelism);

        Logging.info("");
        Logging.info("");
        Logging.info(Logging.ANSI_CYAN + "====Running algorithm====" + Logging.ANSI_RESET);

        int maxMemoryBytes = maxMemoryMb * 1024 * 1024;
        int currMemoryBytes = maxMemoryBytes / hashingPoolSize; // equal to max memory that can be used per hash
        int currIterations = 1;

        long finalAvgTime;
        while (true) {
            long currentTimeTaken = getApproxTimeForArgon2HashWith(currMemoryBytes, currIterations, parallelism,
                    hashingPoolSize);
            Logging.info("Took " + currentTimeTaken + " MS per hash");
            Logging.info("");
            Logging.info("");

            if (Math.abs(currentTimeTaken - targetTimePerHashMs) < 10) {
                finalAvgTime = currentTimeTaken;
                break;
            }

            if (currentTimeTaken > targetTimePerHashMs) {
                Logging.info("Adjusting memory to reach target time.");

                // decrease memory by 5% or 1 mb (whichever is greater)
                currMemoryBytes = currMemoryBytes - Math.max((int) (0.05 * currMemoryBytes), 1024 * 1024);
            } else {
                Logging.info("Adjusting iterations to reach target time.");
                currIterations += 1;
            }
        }

        Logging.info(Logging.ANSI_GREEN + "====Final values====");
        Logging.info("Average time per hash is: " + finalAvgTime + " MS");
        Logging.info("");
        Logging.info("argon2_memory_kb: " + currMemoryBytes / 1024 + " (" + (currMemoryBytes / (1024 * 1024)) + " MB)");
        Logging.info("argon2_iterations: " + currIterations);
        Logging.info("argon2_parallelism: " + parallelism);
        Logging.info("argon2_hashing_pool_size: " + hashingPoolSize);
        Logging.info("");
        Logging.info("====================" + Logging.ANSI_RESET);
        Logging.info(
                "You should use these as docker env variables or put them in the config.yaml file in the SuperTokens "
                        + "installation directory.");
        Logging.info("");
    }

    private long getApproxTimeForArgon2HashWith(int memory, int iterations, int parallelism, int maxConcurrentHashes)
            throws TooLowMemoryProvidedForArgon2 {
        if (memory < (15 * 1024 * 1024) || iterations > 100) {
            throw new TooLowMemoryProvidedForArgon2();
        }
        Logging.info("");
        Logging.info("Current argon2 settings");
        Logging.info("-> memory: " + memory / (1024 * 1024) + " MB");
        Logging.info("-> iterations: " + iterations);
        Logging.info("Calculating average hashing time....");

        ExecutorService service = Executors.newFixedThreadPool(maxConcurrentHashes);
        AtomicInteger averageTime = new AtomicInteger();
        for (int i = 0; i < maxConcurrentHashes; i++) {
            service.execute(() -> {
                int avgTimeForThisThread = 0;
                int numberOfTries = 50;
                for (int y = 0; y < numberOfTries; y++) {
                    long beforeTime = System.currentTimeMillis();
                    Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, 16, 32);
                    argon2.hash(iterations, memory / 1024, parallelism, "somePassword".toCharArray());
                    int diff = (int) (System.currentTimeMillis() - beforeTime);
                    avgTimeForThisThread = avgTimeForThisThread + diff;
                    Logging.infoNoNewLine(".");
                }
                avgTimeForThisThread = avgTimeForThisThread / numberOfTries;
                averageTime.addAndGet(avgTimeForThisThread);
            });
        }

        service.shutdown();
        try {
            service.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }

        return averageTime.get() / maxConcurrentHashes;
    }

    static class TooLowMemoryProvidedForArgon2 extends Exception {

    }
}
