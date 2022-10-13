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

package android.hardware.input.cts.tests;

import static org.junit.Assume.assumeTrue;

import android.hardware.cts.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SonyDualshock4UsbTest extends InputHidTestCase {

    // Simulates the behavior of PlayStation DualShock4 gamepad (model CUH-ZCT1U)
    public SonyDualshock4UsbTest() {
        super(R.raw.sony_dualshock4_usb_register);
    }

    @Test
    public void testAllKeys() {
        testInputEvents(R.raw.sony_dualshock4_usb_keyeventtests);
    }

    @Test
    public void testAllMotions() {
        testInputEvents(R.raw.sony_dualshock4_usb_motioneventtests);
    }

    @Test
    public void testBattery() {
        testInputBatteryEvents(R.raw.sony_dualshock4_usb_batteryeventtests);
    }

    @Test
    public void testVibrator() throws Exception {
        assumeTrue("Requires kernel > 4.19", isKernelVersionGreaterThan("4.19"));
        testInputVibratorEvents(R.raw.sony_dualshock4_usb_vibratortests);
    }
}
