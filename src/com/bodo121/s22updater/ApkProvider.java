package com.bodo121.s22updater;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal read-only provider exposing the staged app-update APK. */
public class ApkProvider extends ContentProvider {
    static final String AUTHORITY_SUFFIX = ".updates";
    private static final int CODE_APK = 1;
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

    static Uri uriFor(Context context, File apk) {
        if (!apk.getParentFile().getName().equals("updates"))
            throw new SecurityException("Only staged updates are shared");
        return new Uri.Builder().scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath("update.apk").build();
    }

    static File fileFor(Context context, Uri uri) throws FileNotFoundException {
        if (!"update.apk".equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        File file = new File(new File(context.getCacheDir(), "updates"), "update.apk");
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }

    @Override public boolean onCreate() {
        matcher.addURI(getContext().getPackageName() + AUTHORITY_SUFFIX, "update.apk", CODE_APK);
        return true;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (matcher.match(uri) != CODE_APK || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(fileFor(getContext(), uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (matcher.match(uri) != CODE_APK) return null;
        try {
            File file = fileFor(getContext(), uri);
            MatrixCursor cursor = new MatrixCursor(
                    new String[]{"_display_name", "_size"}, 1);
            cursor.addRow(new Object[]{"S22-Updater.apk", file.length()});
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public String getType(Uri uri) {
        return matcher.match(uri) == CODE_APK ? "application/vnd.android.package-archive" : null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
