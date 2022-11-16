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
package android.healthconnect.datatypes;

import android.healthconnect.datatypes.units.Mass;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Captures the user's lean body mass. Each record represents a single instantaneous measurement.
 */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS)
public final class LeanBodyMassRecord extends InstantRecord {

    private final Mass mMass;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}.
     * @param time Start time of this activity
     * @param zoneOffset Zone offset of the user when the activity started
     * @param mass Mass of this activity
     */
    private LeanBodyMassRecord(
            @NonNull Metadata metadata,
            @NonNull Instant time,
            @NonNull ZoneOffset zoneOffset,
            @NonNull Mass mass) {
        super(metadata, time, zoneOffset);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(time);
        Objects.requireNonNull(zoneOffset);
        Objects.requireNonNull(mass);
        mMass = mass;
    }
    /**
     * @return mass in {@link Mass} unit.
     */
    @NonNull
    public Mass getMass() {
        return mMass;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        LeanBodyMassRecord that = (LeanBodyMassRecord) o;
        return getMass().equals(that.getMass());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getMass());
    }

    /** Builder class for {@link LeanBodyMassRecord} */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private ZoneOffset mZoneOffset;
        private final Mass mMass;

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param time Start time of this activity
         * @param mass Mass in {@link Mass} unit. Required field. Valid range: 0-1000 kilograms.
         */
        public Builder(@NonNull Metadata metadata, @NonNull Instant time, @NonNull Mass mass) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(time);
            Objects.requireNonNull(mass);
            mMetadata = metadata;
            mTime = time;
            mZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            mMass = mass;
        }

        /** Sets the zone offset of the user when the activity happened */
        @NonNull
        public Builder setZoneOffset(@NonNull ZoneOffset zoneOffset) {
            Objects.requireNonNull(zoneOffset);
            mZoneOffset = zoneOffset;
            return this;
        }

        /**
         * @return Object of {@link LeanBodyMassRecord}
         */
        @NonNull
        public LeanBodyMassRecord build() {
            return new LeanBodyMassRecord(mMetadata, mTime, mZoneOffset, mMass);
        }
    }
}
