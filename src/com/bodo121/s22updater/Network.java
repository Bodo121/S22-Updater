package com.bodo121.s22updater;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class Network {
    interface Progress { void update(long read, long total); }

    static URL validate(String value) throws Exception {
        URL url = new URL(value);
        if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null)
            throw new IOException("Use an HTTPS URL without embedded credentials");
        return url;
    }

    static HttpURLConnection open(String value) throws Exception {
        URL url = validate(value);
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "S22-Updater/4.4");
            try {
                int code = c.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect has no destination");
                    url = validate(new URL(url, location).toString());
                    c.disconnect();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                return c;
            } catch (Exception e) {
                c.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many redirects");
    }

    static void transfer(InputStream in, OutputStream out, long maximum, long total,
                         Progress progress) throws Exception {
        byte[] buffer = new byte[32768];
        long count = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled");
            count += n;
            if (count > maximum) throw new IOException("Download exceeds size limit");
            out.write(buffer, 0, n);
            if (progress != null) progress.update(count, total);
        }
        if (total >= 0 && count != total) throw new IOException("Download was interrupted");
    }

    static String text(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            transfer(in, out, 1024 * 1024, c.getContentLengthLong(), null);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
    }

    static void download(String url, File target, Progress progress) throws Exception {
        HttpURLConnection c = open(url);
        boolean complete = false;
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            transfer(in, out, 64 * 1024 * 1024, c.getContentLengthLong(), progress);
            out.getFD().sync();
            complete = true;
        } finally {
            c.disconnect();
            if (!complete) target.delete();
        }
    }
}
