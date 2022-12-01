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

package com.android.server.healthconnect.permission;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.healthconnect.Constants;
import android.os.Environment;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

class FirstGrantTimeDatastoreXmlPersistence implements FirstGrantTimeDatastore {
    private static final String TAG = "FirstGrantTimeDatastorePersistence";
    private static final String GRANT_TIME_FILE_NAME = "health-permissions-first-grant-times.xml";

    private static final String TAG_FIRST_GRANT_TIMES = "first-grant-times";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_SHARED_USER = "shared-user";

    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_FIRST_GRANT_TIME = "first-grant-time";
    private static final String ATTRIBUTE_VERSION = "version";

    @Nullable
    @Override
    public UserGrantTimeState readForUser(@NonNull UserHandle user) {
        File file = getFile(user);
        if (Constants.DEBUG) {
            Log.d(TAG, "Reading xml from " + file);
        }
        try (FileInputStream inputStream = new AtomicFile(file).openRead()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            return parseXml(parser);
        } catch (FileNotFoundException e) {
            Log.i(TAG, GRANT_TIME_FILE_NAME + " not found");
            return null;
        } catch (XmlPullParserException | IOException e) {
            throw new IllegalStateException("Failed to read " + file, e);
        }
    }

    @Override
    public void writeForUser(
            @NonNull UserGrantTimeState grantTimesState, @NonNull UserHandle user) {
        File file = getFile(user);
        if (Constants.DEBUG) {
            Log.d(TAG, "Writing xml to " + file);
        }
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream outputStream = null;
        try {
            outputStream = atomicFile.startWrite();

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name());
            serializer.startDocument(/* encoding= */ null, /* standalone= */ true);
            serializeGrantTimes(serializer, grantTimesState);

            serializer.endDocument();
            atomicFile.finishWrite(outputStream);
        } catch (Exception e) {
            Log.wtf(
                    TAG,
                    "Failed to write " + GRANT_TIME_FILE_NAME + ", restoring backup: " + file,
                    e);
            atomicFile.failWrite(outputStream);
        } finally {
            IoUtils.closeQuietly(outputStream);
        }
    }

    private static void serializeGrantTimes(
            @NonNull XmlSerializer serializer, @NonNull UserGrantTimeState userGrantTimeState)
            throws IOException {
        serializer.startTag(null, TAG_FIRST_GRANT_TIMES);
        serializer.attribute(
                null, ATTRIBUTE_VERSION, Integer.toString(userGrantTimeState.getVersion()));

        for (Map.Entry<String, Instant> entry :
                userGrantTimeState.getPackageGrantTimes().entrySet()) {
            String packageName = entry.getKey();
            Instant grantTime = entry.getValue();

            serializer.startTag(null, TAG_PACKAGE);
            serializer.attribute(null, ATTRIBUTE_NAME, packageName);
            serializer.attribute(null, ATTRIBUTE_FIRST_GRANT_TIME, grantTime.toString());
            serializer.endTag(null, TAG_PACKAGE);
        }

        for (Map.Entry<String, Instant> entry :
                userGrantTimeState.getSharedUserGrantTimes().entrySet()) {
            String sharedUserName = entry.getKey();
            Instant grantTime = entry.getValue();

            serializer.startTag(null, TAG_SHARED_USER);
            serializer.attribute(null, ATTRIBUTE_NAME, sharedUserName);
            serializer.attribute(null, ATTRIBUTE_FIRST_GRANT_TIME, grantTime.toString());
            serializer.endTag(null, TAG_SHARED_USER);
        }

        serializer.endTag(null, TAG_FIRST_GRANT_TIMES);
    }

    @NonNull
    private static UserGrantTimeState parseXml(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int targetDepth = parser.getDepth() + 1;
        int type = parser.next();

        // Scan the xml until find the grant time tag at the target depth.
        while (type != XmlPullParser.END_DOCUMENT
                && (parser.getDepth() >= targetDepth || type != XmlPullParser.END_TAG)) {
            if (parser.getDepth() > targetDepth || type != XmlPullParser.START_TAG) {
                type = parser.next();
                continue;
            }

            if (parser.getName().equals(TAG_FIRST_GRANT_TIMES)) {
                return parseFirstGrantTimes(parser);
            }

            type = parser.next();
        }
        throw new IllegalStateException(
                "Missing <" + TAG_FIRST_GRANT_TIMES + "> in " + GRANT_TIME_FILE_NAME);
    }

    @NonNull
    private static UserGrantTimeState parseFirstGrantTimes(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String versionValue = parser.getAttributeValue(/* namespace= */ null, ATTRIBUTE_VERSION);
        int version =
                versionValue != null
                        ? Integer.parseInt(versionValue)
                        : UserGrantTimeState.NO_VERSION;
        Map<String, Instant> packagePermissions = new ArrayMap<>();
        Map<String, Instant> sharedUserPermissions = new ArrayMap<>();

        int targetDepth = parser.getDepth() + 1;
        int type = parser.next();
        // Scan the xml until find the needed tags at the target depth.
        while (type != XmlPullParser.END_DOCUMENT
                && (parser.getDepth() >= targetDepth || type != XmlPullParser.END_TAG)) {
            if (parser.getDepth() > targetDepth || type != XmlPullParser.START_TAG) {
                type = parser.next();
                continue;
            }
            switch (parser.getName()) {
                case TAG_PACKAGE:
                {
                    String packageName =
                            parser.getAttributeValue(/* namespace= */ null, ATTRIBUTE_NAME);
                    Instant firstGrantTime =
                            Instant.parse(
                                    parser.getAttributeValue(
                                            /* namespace= */ null, ATTRIBUTE_FIRST_GRANT_TIME));
                    packagePermissions.put(packageName, firstGrantTime);
                    break;
                }
                case TAG_SHARED_USER:
                {
                    String sharedUserName =
                            parser.getAttributeValue(/* namespace= */ null, ATTRIBUTE_NAME);
                    Instant firstGrantTime =
                            Instant.parse(
                                    parser.getAttributeValue(
                                            /* namespace= */ null, ATTRIBUTE_FIRST_GRANT_TIME));
                    sharedUserPermissions.put(sharedUserName, firstGrantTime);
                    break;
                }
            }
            type = parser.next();
        }

        return new UserGrantTimeState(packagePermissions, sharedUserPermissions, version);
    }

    private static File getModuleDataDirectoryPerUser(@NonNull UserHandle user) {
        // TODO(b/243144969): Switch to the final data destination once it's finalised.
        File systemDataDir = new File(Environment.getDataDirectory(), /* child= */ "system");
        File moduleDataDir = new File(systemDataDir, /* child= */ "healthconnect");
        return new File(moduleDataDir, String.valueOf(user.getIdentifier()));
    }

    @NonNull
    private static File getFile(@NonNull UserHandle user) {
        File moduleDataDirPerUser = getModuleDataDirectoryPerUser(user);
        return new File(moduleDataDirPerUser, GRANT_TIME_FILE_NAME);
    }
}