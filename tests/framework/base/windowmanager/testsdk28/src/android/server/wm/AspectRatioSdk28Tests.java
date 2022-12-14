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

package android.server.wm;

import static android.content.pm.PackageManager.FEATURE_WATCH;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;

import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

/**
 * Build/Install/Run:
 *  atest CtsWindowManagerSdk28TestCases:AspectRatioSdk28Tests
 */
@Presubmit
public class AspectRatioSdk28Tests extends AspectRatioTestsBase {

    // The minimum supported device aspect ratio for watches.
    private static final float MIN_WATCH_DEVICE_ASPECT_RATIO = 1.0f;


    // Test target activity that has targetSdk="28" and resizeableActivity="false".
    public static class Sdk28MinAspectRatioActivity extends Activity {
    }

    @Rule
    public ActivityTestRule<?> mSdk28MinAspectRatioActivity = new ActivityTestRule<>(
            Sdk28MinAspectRatioActivity.class, false /* initialTouchMode */,
            false /* launchActivity */);

    /**
     * Device implementations with the Configuration.uiMode set as UI_MODE_TYPE_WATCH MUST have an
     * aspect ratio value set as 1.0
     */
    @Test
    public void testMinAspectRatioPreQActivityOnWatch() {
        // Only test for watch.
        assumeTrue(getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(FEATURE_WATCH));

        runAspectRatioTest(mSdk28MinAspectRatioActivity,
                (actual, displayId, activitySize, displaySize) ->
                assertEquals(actual, MIN_WATCH_DEVICE_ASPECT_RATIO, 0.0f));
    }
}
