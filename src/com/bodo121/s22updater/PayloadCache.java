package com.bodo121.s22updater;

import java.io.*;

/** Verified payload cache using .part downloads and atomic replace. */
final class PayloadCache {
    static File obtain(File dir, String id, String url, long size, String sha, Network.Progress progress)
            throws Exception {
        File target = PayloadStore.file(dir, id);
        if (target.isFile()) {
            PayloadStore.verify(target, size, sha);
            return target;
        }
        File part = new File(dir, target.getName() + ".part");
        try {
            Network.download(url, part, progress);
            PayloadStore.verify(part, size, sha);
            PayloadStore.replace(part, target);
            return target;
        } finally { part.delete(); }
    }
    static boolean verified(File dir, String id, long size, String sha) {
        try { PayloadStore.verify(PayloadStore.file(dir, id), size, sha); return true; }
        catch (Exception e) { return false; }
    }
    static int clear(File dir) {
        File[] files = dir.listFiles();
        int count = 0;
        if (files == null) return 0;
        for (File f : files) if (f.getName().startsWith("payload-") && (f.getName().endsWith(".so")
                || f.getName().endsWith(".part"))) if (f.delete()) count++;
        return count;
    }
}
