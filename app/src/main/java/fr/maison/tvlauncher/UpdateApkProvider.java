package fr.maison.tvlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;

/** Non-exported provider: one exact URI, one private APK, temporary read grants only. */
public final class UpdateApkProvider extends ContentProvider {
    static Uri uri(Context context) { return Uri.parse("content://" + context.getPackageName() + ".updates/update.apk"); }
    static boolean allowed(Context context, Uri requested) { return uri(context).equals(requested); }
    @Override public boolean onCreate() { return true; }
    private void check(Uri requested) {
        if (!allowed(getContext(), requested) || ApkUpdates.ready(getContext()) == null) throw new IllegalArgumentException("APK unavailable");
    }
    @Override public String getType(Uri requested) { check(requested); return ApkUpdates.MIME; }
    @Override public ParcelFileDescriptor openFile(Uri requested, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        check(requested);
        return ParcelFileDescriptor.open(ApkUpdates.file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri requested, String[] projection, String selection, String[] args, String sort) {
        check(requested);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "TVAssistant-update.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = ApkUpdates.file(getContext()).length();
        }
        cursor.addRow(row); return cursor;
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
}
