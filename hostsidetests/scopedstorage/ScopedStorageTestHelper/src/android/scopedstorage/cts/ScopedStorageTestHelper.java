/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.scopedstorage.cts;

import static android.scopedstorage.cts.lib.RedactionTestHelper.EXIF_METADATA_QUERY;
import static android.scopedstorage.cts.lib.RedactionTestHelper.getExifMetadata;
import static android.scopedstorage.cts.lib.TestUtils.CAN_OPEN_FILE_FOR_READ_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.CAN_OPEN_FILE_FOR_WRITE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.CAN_READ_WRITE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.CHECK_DATABASE_ROW_EXISTS_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.CREATE_FILE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.CREATE_IMAGE_ENTRY_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.DELETE_FILE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.INTENT_EXCEPTION;
import static android.scopedstorage.cts.lib.TestUtils.INTENT_EXTRA_CALLING_PKG;
import static android.scopedstorage.cts.lib.TestUtils.INTENT_EXTRA_PATH;
import static android.scopedstorage.cts.lib.TestUtils.INTENT_EXTRA_URI;
import static android.scopedstorage.cts.lib.TestUtils.IS_URI_REDACTED_VIA_FILEPATH;
import static android.scopedstorage.cts.lib.TestUtils.IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_READ;
import static android.scopedstorage.cts.lib.TestUtils.IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_WRITE;
import static android.scopedstorage.cts.lib.TestUtils.OPEN_FILE_FOR_READ_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.OPEN_FILE_FOR_WRITE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.QUERY_TYPE;
import static android.scopedstorage.cts.lib.TestUtils.QUERY_URI;
import static android.scopedstorage.cts.lib.TestUtils.READDIR_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.RENAME_FILE_PARAMS_SEPARATOR;
import static android.scopedstorage.cts.lib.TestUtils.RENAME_FILE_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.SETATTR_QUERY;
import static android.scopedstorage.cts.lib.TestUtils.canOpen;
import static android.scopedstorage.cts.lib.TestUtils.getFileRowIdFromDatabase;
import static android.scopedstorage.cts.lib.TestUtils.getImageContentUri;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper app for ScopedStorageTest.
 *
 * <p>Used to perform ScopedStorageTest functions as a different app. Based on the Query type
 * app can perform different functions and send the result back to host app.
 */
public class ScopedStorageTestHelper extends Activity {
    private static final String TAG = "ScopedStorageTestHelper";
    /**
     * Regex that matches paths in all well-known package-specific directories,
     * and which captures the directory type as the first group (data|media|obb) and the
     * package name as the 2nd group.
     */
    private static final Pattern PATTERN_OWNED_PATH = Pattern.compile(
            "(?i)^/storage/[^/]+/(?:[0-9]+/)?Android/(data|media|obb)/([^/]+)(/?.*)?");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String queryType = getIntent().getStringExtra(QUERY_TYPE);
        queryType = queryType == null ? "null" : queryType;
        Intent returnIntent;
        try {
            switch (queryType) {
                case READDIR_QUERY:
                    returnIntent = sendDirectoryEntries(queryType);
                    break;
                case CAN_READ_WRITE_QUERY:
                case CREATE_FILE_QUERY:
                case DELETE_FILE_QUERY:
                case CAN_OPEN_FILE_FOR_READ_QUERY:
                case CAN_OPEN_FILE_FOR_WRITE_QUERY:
                case OPEN_FILE_FOR_READ_QUERY:
                case OPEN_FILE_FOR_WRITE_QUERY:
                case SETATTR_QUERY:
                    returnIntent = accessFile(queryType);
                    break;
                case EXIF_METADATA_QUERY:
                    returnIntent = sendMetadata(queryType);
                    break;
                case CREATE_IMAGE_ENTRY_QUERY:
                    returnIntent = createImageEntry(queryType);
                    break;
                case RENAME_FILE_QUERY:
                    returnIntent = renameFile(queryType);
                    break;
                case CHECK_DATABASE_ROW_EXISTS_QUERY:
                    returnIntent = checkDatabaseRowExists(queryType);
                    break;
                case IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_WRITE:
                case IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_READ:
                    returnIntent = isFileDescriptorRedactedForUri(queryType);
                    break;
                case IS_URI_REDACTED_VIA_FILEPATH:
                    returnIntent = isFilePathForUriRedacted(queryType);
                    break;
                case QUERY_URI:
                    returnIntent = queryForUri(queryType);
                    break;
                case "null":
                default:
                    throw new IllegalStateException(
                            "Unknown query received from launcher app: " + queryType);
            }
        } catch (Exception e) {
            returnIntent = new Intent(queryType);
            returnIntent.putExtra(INTENT_EXCEPTION, e);
        }
        sendBroadcast(returnIntent);
    }

    private Intent queryForUri(String queryType) {
        final Intent intent = new Intent(queryType);
        final Uri uri = getIntent().getParcelableExtra(INTENT_EXTRA_URI);

        try {
            final Cursor c = getContentResolver().query(uri, null, null, null);
            intent.putExtra(queryType, c != null && c.moveToFirst());
        } catch (Exception e) {
            intent.putExtra(INTENT_EXCEPTION, e);
        }

        return intent;
    }

    private Intent isFileDescriptorRedactedForUri(String queryType) {
        final Intent intent = new Intent(queryType);
        final Uri uri = getIntent().getParcelableExtra(INTENT_EXTRA_URI);

        try {
            final String mode = queryType.equals(IS_URI_REDACTED_VIA_FILE_DESCRIPTOR_FOR_WRITE)
                    ? "w" : "r";
            FileDescriptor fd = getContentResolver().openFileDescriptor(uri,
                    mode).getFileDescriptor();
            ExifInterface exifInterface = new ExifInterface(fd);
            intent.putExtra(queryType, exifInterface.getGpsDateTime() == -1);
        } catch (Exception e) {
            intent.putExtra(INTENT_EXCEPTION, e);
        }

        return intent;
    }

    private Intent isFilePathForUriRedacted(String queryType) {
        final Intent intent = new Intent(queryType);
        final Uri uri = getIntent().getParcelableExtra(INTENT_EXTRA_URI);

        try {
            final Cursor c = getContentResolver().query(uri, null, null, null);
            if (!c.moveToFirst()) {
                intent.putExtra(INTENT_EXCEPTION, new IOException(""));
                return intent;
            }
            final String path = c.getString(c.getColumnIndex(MediaStore.MediaColumns.DATA));
            ExifInterface redactedExifInf = new ExifInterface(path);
            intent.putExtra(queryType, redactedExifInf.getGpsDateTime() == -1);
        } catch (Exception e) {
            intent.putExtra(INTENT_EXCEPTION, e);
        }

        return intent;
    }

    private Intent sendMetadata(String queryType) throws IOException {
        final Intent intent = new Intent(queryType);
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            final String filePath = getIntent().getStringExtra(INTENT_EXTRA_PATH);
            if (EXIF_METADATA_QUERY.equals(queryType)) {
                intent.putExtra(queryType, getExifMetadata(new File(filePath)));
            }
        } else {
            throw new IllegalStateException(
                    EXIF_METADATA_QUERY + ": File path not set from launcher app");
        }
        return intent;
    }

    private Intent sendDirectoryEntries(String queryType) throws IOException {
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            final String directoryPath = getIntent().getStringExtra(INTENT_EXTRA_PATH);
            ArrayList<String> directoryEntriesList = new ArrayList<>();
            if (queryType.equals(READDIR_QUERY)) {
                final String[] directoryEntries = new File(directoryPath).list();
                if (directoryEntries == null) {
                    throw new IOException(
                            "I/O exception while listing entries for " + directoryPath);
                }
                Collections.addAll(directoryEntriesList, directoryEntries);
            }
            final Intent intent = new Intent(queryType);
            intent.putStringArrayListExtra(queryType, directoryEntriesList);
            return intent;
        } else {
            throw new IllegalStateException(
                    READDIR_QUERY + ": Directory path not set from launcher app");
        }
    }

    private Intent createImageEntry(String queryType) throws Exception {
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            final String path = getIntent().getStringExtra(INTENT_EXTRA_PATH);
            final String relativePath = path.substring(0, path.lastIndexOf('/'));
            final String name = path.substring(path.lastIndexOf('/') + 1);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);

            getContentResolver().insert(getImageContentUri(), values);

            final Intent intent = new Intent(queryType);
            intent.putExtra(queryType, true);
            return intent;
        } else {
            throw new IllegalStateException(
                    CREATE_IMAGE_ENTRY_QUERY + ": File path not set from launcher app");
        }
    }

    private Intent accessFile(String queryType) throws IOException {
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            final String packageName = getIntent().getStringExtra(INTENT_EXTRA_CALLING_PKG);
            final String filePath = getIntent().getStringExtra(INTENT_EXTRA_PATH);
            final File file = new File(filePath);
            final Intent intent = new Intent(queryType);
            switch (queryType) {
                case CAN_READ_WRITE_QUERY:
                    intent.putExtra(queryType, file.exists() && file.canRead() && file.canWrite());
                    return intent;
                case CREATE_FILE_QUERY:
                    maybeCreateParentDirInAndroid(file);
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }
                    intent.putExtra(queryType, file.createNewFile());
                    return intent;
                case DELETE_FILE_QUERY:
                    intent.putExtra(queryType, file.delete());
                    return intent;
                case SETATTR_QUERY:
                    int newTimeMillis = 12345000;
                    intent.putExtra(queryType, file.setLastModified(newTimeMillis));
                    return intent;
                case CAN_OPEN_FILE_FOR_READ_QUERY:
                    intent.putExtra(queryType, canOpen(file, false));
                    return intent;
                case CAN_OPEN_FILE_FOR_WRITE_QUERY:
                    intent.putExtra(queryType, canOpen(file, true));
                    return intent;
                case OPEN_FILE_FOR_READ_QUERY:
                case OPEN_FILE_FOR_WRITE_QUERY:
                    Uri contentUri = FileProvider.getUriForFile(getApplicationContext(),
                            getApplicationContext().getPackageName(), file);
                    intent.putExtra(queryType, contentUri);
                    // Grant permission to the possible instrumenting test apps
                    if (packageName != null) {
                        getApplicationContext().grantUriPermission(packageName,
                                contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                    return intent;
                default:
                    throw new IllegalStateException(
                            "Unknown query received from launcher app: " + queryType);
            }
        } else {
            throw new IllegalStateException(queryType + ": File path not set from launcher app");
        }
    }

    private Intent renameFile(String queryType) {
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            String[] paths = getIntent().getStringExtra(INTENT_EXTRA_PATH)
                    .split(RENAME_FILE_PARAMS_SEPARATOR);
            File src = new File(paths[0]);
            File dst = new File(paths[1]);
            boolean result = src.renameTo(dst);
            final Intent intent = new Intent(queryType);
            intent.putExtra(queryType, result);
            return intent;
        } else {
            throw new IllegalStateException(
                    queryType + ": File paths not set from launcher app");
        }
    }

    private Intent checkDatabaseRowExists(String queryType) {
        if (getIntent().hasExtra(INTENT_EXTRA_PATH)) {
            final String filePath = getIntent().getStringExtra(INTENT_EXTRA_PATH);
            boolean result =
                    getFileRowIdFromDatabase(getContentResolver(), new File(filePath)) != -1;
            final Intent intent = new Intent(queryType);
            intent.putExtra(queryType, result);
            return intent;
        } else {
            throw new IllegalStateException(
                    queryType + ": File path not set from launcher app");
        }
    }

    private void maybeCreateParentDirInAndroid(File file) {
        final String ownedPathType = getOwnedDirectoryType(file);
        if (ownedPathType == null) {
            return;
        }
        // Create the external app dir first.
        if (createExternalAppDir(ownedPathType)) {
            // Then create everything along the path.
            file.getParentFile().mkdirs();
        }
    }

    private boolean createExternalAppDir(String name) {
        // Apps are not allowed to create data/cache/obb etc under Android directly and are
        // expected to call one of the following methods.
        switch (name) {
            case "data":
                getApplicationContext().getExternalFilesDirs(null);
                getApplicationContext().getExternalCacheDirs();
                return true;
            case "obb":
                getApplicationContext().getObbDirs();
                return true;
            case "media":
                getApplicationContext().getExternalMediaDirs();
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns null if given path is not an owned path.
     */
    @Nullable
    private static String getOwnedDirectoryType(File path) {
        final Matcher m = PATTERN_OWNED_PATH.matcher(path.getAbsolutePath());
        if (m.matches()) {
            return m.group(1);
        }
        return null;
    }
}
