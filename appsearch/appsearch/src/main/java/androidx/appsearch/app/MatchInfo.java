/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.appsearch.app;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;
import androidx.core.util.Preconditions;

/**
 * Snippet: It refers to a substring of text from the content of document that is returned as a
 * part of search result.
 * This class represents a match objects for any Snippets that might be present in
 * {@link SearchResults} from query. Using this class user can get the full text, exact matches and
 * Snippets of document content for a given match.
 *
 * <p>Class Example 1:
 * A document contains following text in property subject:
 * <p>A commonly used fake word is foo. Another nonsense word that’s used a lot is bar.
 *
 * <p>If the queryExpression is "foo".
 *
 * <p>{@link MatchInfo#getPropertyPath()} returns "subject"
 * <p>{@link MatchInfo#getFullText()} returns "A commonly used fake word is foo. Another nonsense
 * word that’s used a lot is bar."
 * <p>{@link MatchInfo#getExactMatchPosition()} returns [29, 32]
 * <p>{@link MatchInfo#getExactMatch()} returns "foo"
 * <p>{@link MatchInfo#getSnippetPosition()} returns [26, 33]
 * <p>{@link MatchInfo#getSnippet()} returns "is foo."
 * <p>
 * <p>Class Example 2:
 * A document contains a property name sender which contains 2 property names name and email, so
 * we will have 2 property paths: {@code sender.name} and {@code sender.email}.
 * <p> Let {@code sender.name = "Test Name Jr."} and {@code sender.email = "TestNameJr@gmail.com"}
 *
 * <p>If the queryExpression is "Test". We will have 2 matches.
 *
 * <p> Match-1
 * <p>{@link MatchInfo#getPropertyPath()} returns "sender.name"
 * <p>{@link MatchInfo#getFullText()} returns "Test Name Jr."
 * <p>{@link MatchInfo#getExactMatchPosition()} returns [0, 4]
 * <p>{@link MatchInfo#getExactMatch()} returns "Test"
 * <p>{@link MatchInfo#getSnippetPosition()} returns [0, 9]
 * <p>{@link MatchInfo#getSnippet()} returns "Test Name"
 * <p> Match-2
 * <p>{@link MatchInfo#getPropertyPath()} returns "sender.email"
 * <p>{@link MatchInfo#getFullText()} returns "TestNameJr@gmail.com"
 * <p>{@link MatchInfo#getExactMatchPosition()} returns [0, 20]
 * <p>{@link MatchInfo#getExactMatch()} returns "TestNameJr@gmail.com"
 * <p>{@link MatchInfo#getSnippetPosition()} returns [0, 20]
 * <p>{@link MatchInfo#getSnippet()} returns "TestNameJr@gmail.com"
 */
// TODO(sidchhabra): Capture real snippet after integration with icingLib.
public final class MatchInfo {
    // The path of the matching snippet property.
    static final String PROPERTY_PATH_FIELD = "propertyPath";
    // The index of matching value in its property. A property may have multiple values. This
    // index indicates which value is the match.
    static final String VALUES_INDEX_FIELD = "valuesIndex";
    static final String EXACT_MATCH_POSITION_LOWER_FIELD = "exactMatchPositionLower";
    static final String EXACT_MATCH_POSITION_UPPER_FIELD = "exactMatchPositionUpper";
    static final String WINDOW_POSITION_LOWER_FIELD = "windowPositionLower";
    static final String WINDOW_POSITION_UPPER_FIELD = "windowPositionUpper";

    private final String mFullText;
    private final String mPropertyPath;
    private final Bundle mBundle;
    private MatchRange mExactMatchRange;
    private MatchRange mWindowRange;


    MatchInfo(@NonNull Bundle bundle, @NonNull GenericDocument document) {
        mBundle = Preconditions.checkNotNull(bundle);
        Preconditions.checkNotNull(document);
        mPropertyPath = Preconditions.checkNotNull(bundle.getString(PROPERTY_PATH_FIELD));
        mFullText = getPropertyValues(document, mPropertyPath, mBundle.getInt(VALUES_INDEX_FIELD));
    }

    /** Returns the {@link Bundle} populated by this builder. */
    Bundle getBundle() {
        return mBundle;
    }

    /**
     * Gets the property path corresponding to the given entry.
     * <p>Property Path: '.' - delimited sequence of property names indicating which property in
     * the Document these snippets correspond to.
     * <p>Example properties: 'body', 'sender.name', 'sender.emailaddress', etc.
     * For class example 1 this returns "subject"
     */
    @NonNull
    public String getPropertyPath() {
        return mPropertyPath;
    }

    /**
     * Gets the full text corresponding to the given entry.
     * <p>For class example this returns "A commonly used fake word is foo. Another nonsense word
     * that’s used a lot is bar."
     */
    @NonNull
    public String getFullText() {
        return mFullText;
    }

    /**
     * Gets the exact {@link MatchRange} corresponding to the given entry.
     * <p>For class example 1 this returns [29, 32]
     */
    @NonNull
    public MatchRange getExactMatchPosition() {
        if (mExactMatchRange == null) {
            mExactMatchRange = new MatchRange(mBundle.getInt(EXACT_MATCH_POSITION_LOWER_FIELD),
                    mBundle.getInt(EXACT_MATCH_POSITION_UPPER_FIELD));
        }
        return mExactMatchRange;
    }

    /**
     * Gets the  {@link MatchRange} corresponding to the given entry.
     * <p>For class example 1 this returns "foo"
     */
    @NonNull
    public CharSequence getExactMatch() {
        return getSubstring(getExactMatchPosition());
    }

    /**
     * Gets the snippet {@link MatchRange} corresponding to the given entry.
     * <p>Only populated when set maxSnippetSize > 0 in
     * {@link SearchSpec.Builder#setMaxSnippetSize}.
     * <p>For class example 1 this returns [29, 41].
     */
    @NonNull
    public MatchRange getSnippetPosition() {
        if (mWindowRange == null) {
            mWindowRange = new MatchRange(mBundle.getInt(WINDOW_POSITION_LOWER_FIELD),
                    mBundle.getInt(WINDOW_POSITION_UPPER_FIELD));
        }
        return mWindowRange;
    }

    /**
     * Gets the snippet corresponding to the given entry.
     * <p>Snippet - Provides a subset of the content to display. Only populated when requested
     * maxSnippetSize > 0. The size of this content can be changed by
     * {@link SearchSpec.Builder#setMaxSnippetSize}. Windowing is centered around the middle of
     * the matched token with content on either side clipped to token boundaries.
     * <p>For class example 1 this returns "foo. Another"
     */
    @NonNull
    public CharSequence getSnippet() {
        return getSubstring(getSnippetPosition());
    }

    private CharSequence getSubstring(MatchRange range) {
        return getFullText()
                .substring(range.getStart(), range.getEnd());
    }

    /** Extracts the matching string from the document. */
    private static String getPropertyValues(GenericDocument document, String propertyName,
            int valueIndex) {
        // In IcingLib snippeting is available for only 3 data types i.e String, double and long,
        // so we need to check which of these three are requested.
        // TODO (tytytyww): getPropertyStringArray takes property name, handle for property path.
        // TODO (tytytyww): support double[] and long[].
        String[] values = document.getPropertyStringArray(propertyName);
        if (values == null) {
            throw new IllegalStateException("No content found for requested property path!");
        }
        return values[valueIndex];
    }

    /**
     * Class providing the position range of matching information.
     *
     * <p> All ranges are finite, and the left side of the range is always {@code <=} the right
     * side of the range.
     *
     * <p> Example: MatchRange(0, 100) represent a hundred ints from 0 to 99."
     *
     */
    public static class MatchRange{
        private final int mEnd;
        private final int mStart;

        /**
         * Creates a new immutable range.
         * <p> The endpoints are {@code [start, end)}; that is the range is bounded. {@code start}
         * must be lesser or equal to {@code end}.
         *
         * @param start The start point (inclusive)
         * @param end The end point (exclusive)
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public MatchRange(int start, int end) {
            if (start > end) {
                throw new IllegalArgumentException("Start point must be less than or equal to "
                        + "end point");
            }
            mStart = start;
            mEnd = end;
        }

        /** Gets the start point (inclusive). */
        public int getStart() {
            return mStart;
        }

        /** Gets the end point (exclusive). */
        public int getEnd() {
            return mEnd;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MatchRange)) {
                return false;
            }
            MatchRange otherMatchRange = (MatchRange) other;
            return this.getStart() == otherMatchRange.getStart()
                    && this.getEnd() == otherMatchRange.getEnd();
        }

        @Override
        @NonNull
        public String toString() {
            return "MatchRange { start: " + mStart + " , end: " + mEnd + "}";
        }

        @Override
        public int hashCode() {
            return ObjectsCompat.hash(mStart, mEnd);
        }
    }

    /** Builder for {@link MatchInfo objects}. */
    static class Builder {
        private final Bundle mBundle = new Bundle();
        private final GenericDocument mDocument;
        private boolean mBuilt = false;

        Builder(@NonNull GenericDocument document) {
            mDocument = Preconditions.checkNotNull(document);
        }

        /**
         * Sets the path of the matching snippet property.
         * @see MatchRange#getPropertyPath()
         */
        Builder setPropertyPath(@NonNull String propertyPath) {
            Preconditions.checkNotNull(propertyPath);
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mBundle.putString(PROPERTY_PATH_FIELD, propertyPath);
            return this;
        }

        /** Sets the index of matching value in its property. */
        Builder setValuesIndex(int valuesIndex) {
            mBundle.putInt(VALUES_INDEX_FIELD, valuesIndex);
            return this;
        }
        /**
         * Sets the position range within the matched string at which the exact match begins and
         * ends.
         */
        Builder setExactMatchPositionRange(int exactMatchPositionLower,
                int exactMatchPositionUpper) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mBundle.putInt(EXACT_MATCH_POSITION_LOWER_FIELD, exactMatchPositionLower);
            mBundle.putInt(EXACT_MATCH_POSITION_UPPER_FIELD, exactMatchPositionUpper);
            return this;
        }

        /** Sets the position range of the suggested snippet window.         */
        Builder setWindowPositionRange(int windowPositionLower, int windowPositionUpper) {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            mBundle.putInt(WINDOW_POSITION_LOWER_FIELD, windowPositionLower);
            mBundle.putInt(WINDOW_POSITION_UPPER_FIELD, windowPositionUpper);
            return this;
        }

        MatchInfo build() {
            Preconditions.checkState(!mBuilt, "Builder has already been used");
            if (!mBundle.containsKey(PROPERTY_PATH_FIELD)) {
                throw new IllegalArgumentException("Missing field: PROPERTY_PATH");
            }
            mBuilt = true;
            return new MatchInfo(mBundle, mDocument);
        }
    }
}
