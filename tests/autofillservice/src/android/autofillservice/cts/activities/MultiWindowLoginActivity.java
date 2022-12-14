/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.autofillservice.cts.activities;

import android.autofillservice.cts.testcore.Timeouts;
import android.content.res.Configuration;

import com.android.compatibility.common.util.RetryableException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Activity that allows capture recreated instance for testing multi window scenarios.
 */
public class MultiWindowLoginActivity extends LoginActivity {

    private static MultiWindowLoginActivity sLastInstance;
    private static CountDownLatch sLastInstanceLatch;

    @Override
    protected void onStart() {
        super.onStart();
        sLastInstance = this;
        if (sLastInstanceLatch != null) {
            sLastInstanceLatch.countDown();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            if (sLastInstanceLatch != null) {
                sLastInstanceLatch.countDown();
            }
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (sLastInstanceLatch != null) {
            // mWindowingMode is split-screen-primary
            if (isInMultiWindowMode) {
                sLastInstanceLatch.countDown();
            }
        }
    }

    public static void expectNewInstance(boolean waitWindowFocus) {
        sLastInstanceLatch = new CountDownLatch(waitWindowFocus ? 2 : 1);
    }

    public static MultiWindowLoginActivity waitNewInstance() throws InterruptedException {
        if (!sLastInstanceLatch.await(Timeouts.ACTIVITY_RESURRECTION.getMaxValue(),
                TimeUnit.MILLISECONDS)) {
            throw new RetryableException("New MultiWindowLoginActivity didn't start",
                    Timeouts.ACTIVITY_RESURRECTION);
        }
        sLastInstanceLatch = null;
        return sLastInstance;
    }
}
