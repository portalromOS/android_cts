# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := android.test.base.stubs

LOCAL_JNI_SHARED_LIBRARIES := libctsdeviceinfo

LOCAL_MULTILIB := both

DEVICE_INFO_MIN_SDK := 23
DEVICE_INFO_TARGET_SDK := 23

LOCAL_MIN_SDK_VERSION := 23

DEVICE_INFO_PERMISSIONS :=

DEVICE_INFO_ACTIVITIES := \
    com.android.compatibility.common.deviceinfo.GlesStubActivity \
    com.android.cts.deviceinfo.CameraDeviceInfo \
    com.android.cts.deviceinfo.SensorDeviceInfo \
    com.android.cts.deviceinfo.VulkanDeviceInfo

LOCAL_PACKAGE_NAME := CtsDeviceInfo
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice

# Disable dexpreopt and <uses-library> check for test.
LOCAL_ENFORCE_USES_LIBRARIES := false
LOCAL_DEX_PREOPT := false

# Tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests sts mts vts catbox gcatbox ats

include $(BUILD_CTS_DEVICE_INFO_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
