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

package io.supertokens.cli;

import io.supertokens.cli.exception.QuitProgramException;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Utils {

    public static void copyFolderOrFile(File sourceFolder, File destinationFolder) throws IOException {
        if (!sourceFolder.exists()) {
            return;
        }
        if (sourceFolder.isDirectory()) {
            createAllDirs(destinationFolder);
            String[] files = sourceFolder.list();

            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(sourceFolder, file);
                    File destFile = new File(destinationFolder, file);
                    copyFolderOrFile(srcFile, destFile);
                }
            }
        } else {
            File parent = destinationFolder.getParentFile();
            createAllDirs(parent);
            File srcParent = sourceFolder.getParentFile();
            createAllDirs(srcParent);
            try {
                Files.copy(sourceFolder.toPath(), destinationFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (AccessDeniedException e) {
                throw new QuitProgramException("Moving content to installation location failed. Try again with"
                        + ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." : " sudo."),
                        null);
            }
        }
    }

    private static void createAllDirs(File destinationFolder) {
        if (destinationFolder != null && !destinationFolder.exists()) {
            boolean success = destinationFolder.mkdirs();
            if (!success) {
                throw new QuitProgramException("Moving content to installation location failed. Try again with"
                        + ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." : " sudo."),
                        null);
            }
        }
    }

    public static boolean deleteDirOrFile(File file) {
        if (!file.exists()) {
            return true;
        }
        boolean result = true;
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    result = result && deleteDirOrFile(f);
                }
            }
        }
        result = result && file.delete();
        return result;
    }

    public static String normaliseDirectoryPath(String dir) {
        if (OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) {
            if (!dir.endsWith("\\")) {
                return dir + "\\";
            }
        } else {
            if (!dir.endsWith("/")) {
                return dir + "/";
            }
        }
        return dir;
    }

    public static String formatWithFixedSpaces(String first, String second, int secondStart, int maxWidth) {
        StringBuilder spaces = new StringBuilder();
        int numberOfSpaces = secondStart - first.length();
        spaces.append(" ".repeat(Math.max(0, numberOfSpaces)));
        String actual = first + spaces + second;

        StringBuilder maxSpaces = new StringBuilder();
        maxSpaces.append(" ".repeat(Math.max(0, secondStart)));
        String[] splittedString = actual.split(" ");
        StringBuilder result = new StringBuilder();
        int length = 0;
        for (String curr : splittedString) {
            if (length + curr.length() + 1 > maxWidth && length != secondStart) {
                length = 40;
                result.append("\n").append(maxSpaces);
            }
            length += curr.length() + 1;
            result.append(curr).append(" ");
        }
        return result.toString();
    }

}
