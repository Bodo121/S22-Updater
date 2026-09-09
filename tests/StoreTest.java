package com.bodo121.s22updater;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class StoreTest {
    interface Check { void run() throws Exception; }
    static void rejects(Check check) throws Exception {
        try { check.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("s22-test").toFile();
        try {
            File installed = PayloadStore.file(directory, "firmware-a");
            File candidate = new File(directory, "candidate.part");
            Files.write(installed.toPath(), "old".getBytes(StandardCharsets.UTF_8));
            Files.write(candidate.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
            String correct = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
            if (!correct.equals(PayloadStore.verify(candidate, 3, correct))) throw new AssertionError("Digest");
            rejects(() -> PayloadStore.verify(candidate, 4, correct));
            rejects(() -> PayloadStore.verify(candidate, 3, correct.replace('a', 'b')));
            if (!"old".equals(new String(Files.readAllBytes(installed.toPath()), StandardCharsets.UTF_8)))
                throw new AssertionError("Failed verification replaced good copy");
            PayloadStore.replace(candidate, installed);
            if (!correct.equals(PayloadStore.hash(installed)) || candidate.exists()) throw new AssertionError("Atomic install");
            rejects(() -> PayloadStore.file(directory, "../escape"));
            rejects(() -> Network.validate("http://example.com/file"));
            rejects(() -> Network.validate("https://user:password@example.com/file"));
            Network.validate("https://example.com/file");
            rejects(() -> Network.transfer(new ByteArrayInputStream(new byte[4]), new ByteArrayOutputStream(), 3, 4, null));
            rejects(() -> Network.transfer(new ByteArrayInputStream(new byte[3]), new ByteArrayOutputStream(), 8, 4, null));
            Thread.currentThread().interrupt();
            rejects(() -> Network.transfer(new ByteArrayInputStream(new byte[3]), new ByteArrayOutputStream(), 8, 3, null));
            Thread.interrupted();
            System.out.println("PASS: digest, size, rollback, atomic replacement, paths, HTTPS, bounds, truncation, cancellation");
        } finally {
            for (File file : directory.listFiles()) file.delete();
            directory.delete();
        }
    }
}
