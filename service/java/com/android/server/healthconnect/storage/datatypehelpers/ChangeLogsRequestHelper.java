/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorIntegerList;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorStringList;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.healthconnect.aidl.ChangeLogTokenRequestParcel;
import android.util.Pair;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class to interact with the DB table that stores the information about the change log requests
 * i.e. {@code TABLE_NAME}
 *
 * <p>This class returns the row_id of the change_log_request_table as a token, that can later be
 * used to recreate the request.
 *
 * @hide
 */
public final class ChangeLogsRequestHelper {
    /** A class to represent the request corresponding to a token */
    public static final class TokenRequest {
        private final List<String> mPackageNamesToFilter;
        private final List<Integer> mRecordTypes;
        private final String mRequestingPackageName;
        private final long mRowIdChangeLogs;

        /**
         * @param requestingPackageName contributing package name
         * @param packageNamesToFilter package names to filter
         * @param recordTypes records to filter
         * @param rowIdChangeLogs row id of change log table after which the logs are to be fetched
         */
        public TokenRequest(
                @NonNull List<String> packageNamesToFilter,
                @NonNull List<Integer> recordTypes,
                @NonNull String requestingPackageName,
                long rowIdChangeLogs) {
            mPackageNamesToFilter = packageNamesToFilter;
            mRecordTypes = recordTypes;
            mRequestingPackageName = requestingPackageName;
            mRowIdChangeLogs = rowIdChangeLogs;
        }

        public long getRowIdChangeLogs() {
            return mRowIdChangeLogs;
        }

        @NonNull
        public String getRequestingPackageName() {
            return mRequestingPackageName;
        }

        @NonNull
        public List<String> getPackageNamesToFilter() {
            return mPackageNamesToFilter;
        }

        @NonNull
        public List<Integer> getRecordTypes() {
            return mRecordTypes;
        }
    }

    private static final String TABLE_NAME = "change_log_request_table";
    private static final String PACKAGES_TO_FILTERS_COLUMN_NAME = "packages_to_filter";
    private static final String RECORD_TYPES_COLUMN_NAME = "record_types";
    private static final String PACKAGE_NAME_COLUMN_NAME = "package_name";
    private static final String ROW_ID_CHANGE_LOGS_TABLE_COLUMN_NAME = "row_id_change_logs_table";
    private static ChangeLogsRequestHelper sChangeLogsRequestHelper;

    private ChangeLogsRequestHelper() {}

    @NonNull
    public static ChangeLogsRequestHelper getInstance() {
        if (sChangeLogsRequestHelper == null) {
            sChangeLogsRequestHelper = new ChangeLogsRequestHelper();
        }

        return sChangeLogsRequestHelper;
    }

    @NonNull
    public static TokenRequest getRequest(long token) {
        ReadTableRequest readTableRequest =
                new ReadTableRequest(TABLE_NAME)
                        .setWhereClause(
                                new WhereClauses()
                                        .addWhereEqualsClause(
                                                PRIMARY_COLUMN_NAME, String.valueOf(token)));
        TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (SQLiteDatabase db = transactionManager.getReadableDb();
                Cursor cursor = transactionManager.read(db, readTableRequest)) {
            cursor.moveToFirst();
            return new TokenRequest(
                    getCursorStringList(cursor, PACKAGES_TO_FILTERS_COLUMN_NAME, DELIMITER),
                    getCursorIntegerList(cursor, RECORD_TYPES_COLUMN_NAME, DELIMITER),
                    getCursorString(cursor, PACKAGE_NAME_COLUMN_NAME),
                    getCursorInt(cursor, ROW_ID_CHANGE_LOGS_TABLE_COLUMN_NAME));
        }
    }

    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnsInfo());
    }

    public long getToken(
            @NonNull String packageName, @NonNull ChangeLogTokenRequestParcel request) {
        ContentValues contentValues = new ContentValues();

        /**
         * Store package names here as a package name and not as {@link AppInfoHelper.AppInfo#mId}
         * as ID might not be available right now but might become available when the actual request
         * for this token comes
         */
        contentValues.put(
                PACKAGES_TO_FILTERS_COLUMN_NAME,
                String.join(DELIMITER, request.getPackageNamesToFilter()));
        contentValues.put(
                RECORD_TYPES_COLUMN_NAME,
                Arrays.stream(request.getRecordTypes())
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(DELIMITER)));
        contentValues.put(PACKAGE_NAME_COLUMN_NAME, packageName);
        contentValues.put(
                ROW_ID_CHANGE_LOGS_TABLE_COLUMN_NAME,
                ChangeLogsHelper.getInstance().getLatestRowId());

        return TransactionManager.getInitialisedInstance()
                .insert(new UpsertTableRequest(TABLE_NAME, contentValues));
    }

    @NonNull
    private List<Pair<String, String>> getColumnsInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY));
        columnInfo.add(new Pair<>(PACKAGES_TO_FILTERS_COLUMN_NAME, TEXT_NOT_NULL));
        columnInfo.add(new Pair<>(PACKAGE_NAME_COLUMN_NAME, TEXT_NOT_NULL));
        columnInfo.add(new Pair<>(RECORD_TYPES_COLUMN_NAME, TEXT_NULL));
        columnInfo.add(new Pair<>(ROW_ID_CHANGE_LOGS_TABLE_COLUMN_NAME, INTEGER));

        return columnInfo;
    }
}
