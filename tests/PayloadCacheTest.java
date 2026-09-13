package com.bodo121.s22updater;

import java.io.File;
import java.io.FileOutputStream;

public final class PayloadCacheTest {
    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        System.out.println(name + ": PASS");
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "s22-cache-test-" + System.nanoTime());
        check(dir.mkdirs(), "testCreateCacheDir");
        File cached = PayloadStore.file(dir, "bad-cache");
        try (FileOutputStream out = new FileOutputStream(cached)) {
            out.write(new byte[]{1, 2, 3});
        }
        try {
            PayloadCache.obtain(dir, "bad-cache", "https://", 4,
                    "0000000000000000000000000000000000000000000000000000000000000000", null);
            throw new AssertionError("testCorruptCacheQuarantined");
        } catch (Exception expected) {
            File bad = new File(dir, cached.getName() + ".bad");
            check(!cached.exists() && bad.isFile(), "testCorruptCacheQuarantined");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) file.delete();
            dir.delete();
        }
    }
}
