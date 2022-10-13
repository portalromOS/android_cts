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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.LimitExceededException;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class LimitExceededExceptionTest {
    @Test
    public void testLimitExceededExceptionCtor() {
        try {
            throw new LimitExceededException();
        } catch (LimitExceededException e) {
            assertThat(e.getMessage()).isNull();
        }
    }

    @Test
    public void testLimitExceededExceptionCtor_withMessage() {
        final String testMessage = "Test operation failed.";
        try {
            throw new LimitExceededException(testMessage);
        } catch (LimitExceededException e) {
            assertThat(e.getMessage()).isEqualTo(testMessage);
        }
    }
}
