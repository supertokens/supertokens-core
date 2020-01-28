/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.cli;

import io.supertokens.cli.exception.QuitProgramException;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {

    public final static String SERVER_URL = "https://api.supertokens.io/0";

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
                throw new QuitProgramException(
                        "Moving content to installation location failed. Try again with" +
                                ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." :
                                        " sudo."), null);
            }
        }
    }

    private static void createAllDirs(File destinationFolder) {
        if (destinationFolder != null && !destinationFolder.exists()) {
            boolean success = destinationFolder.mkdirs();
            if (!success) {
                throw new QuitProgramException(
                        "Moving content to installation location failed. Try again with" +
                                ((OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS) ? " root permissions." :
                                        " sudo."), null);
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

    private static boolean isZip(File f) {
        int fileSignature = 0;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            fileSignature = raf.readInt();
        } catch (IOException e) {
            // handle if you like
        }
        return fileSignature == 0x504B0304;
    }

    private static String bytesToString(byte[] bArr) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bArr) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] stringToBytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }

    public static String hashSHA256(String base) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(stringToBytes(base));
        return bytesToString(hash);
    }

    public static boolean isZipFile(File f) throws IOException {
        int fileSignature = 0;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            fileSignature = raf.readInt();
        }
        return fileSignature == 0x504B0304 || fileSignature == 0x504B0506 || fileSignature == 0x504B0708;
    }

    public static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            boolean success = destDir.mkdirs();
            if (!success) {
                throw new AccessDeniedException("Cannot create necessary folder");
            }
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.exists()) {
            File parent = f.getParentFile();
            if (!parent.exists()) {
                boolean ignored = parent.mkdirs();
            }
            boolean ignored = f.createNewFile();
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    public static boolean internetIsAvailable() {
        try {
            final URL url = new URL("http://www.google.com");
            final URLConnection conn = url.openConnection();
            conn.connect();
            conn.getInputStream().close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void makeAllExeFilesInFolderExecutable(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) {
                return;
            }
            for (File f : files) {
                makeAllExeFilesInFolderExecutable(f);
            }
        } else {
            if (file.getName().endsWith(".exe")) {
                boolean ignored = file.setExecutable(true, false);
            }
        }
    }
}
