package com.bodo121.s22updater;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;

final class PayloadStore {
    static String hash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) md.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    static File file(File directory, String id) throws Exception {
        if (!id.matches("[A-Za-z0-9._-]{1,120}")) throw new IOException("Invalid payload ID");
        return new File(directory, "payload-" + id + ".so");
    }

    static String verify(File file, long size, String expected) throws Exception {
        if (!file.isFile() || file.length() == 0) throw new IOException("Empty download");
        if (size > 0 && file.length() != size) throw new IOException("Download size does not match feed");
        String actual = hash(file);
        if (!expected.isEmpty() && !actual.equalsIgnoreCase(expected))
            throw new IOException("SHA-256 does not match feed");
        return actual;
    }

    static void replace(File source, File target) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
