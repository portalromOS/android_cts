/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;

public class NearbyAppStreamingPolicyTest extends BaseDeviceAdminTest {

    public void testGetNearbyAppStreamingPolicy_getsNearbyStreamingDisabledAsDefault() {
        assertThat(mDevicePolicyManager.getNearbyAppStreamingPolicy())
                .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_DISABLED);
    }

    public void testSetNearbyAppStreamingPolicy_changesPolicy() {
        mDevicePolicyManager.setNearbyAppStreamingPolicy(
                DevicePolicyManager.NEARBY_STREAMING_ENABLED);

        assertThat(mDevicePolicyManager.getNearbyAppStreamingPolicy())
                .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_ENABLED);
    }
}
