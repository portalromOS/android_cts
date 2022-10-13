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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.annotations.AnnotationRunPrecedence.EARLY;

import com.android.bedstead.harrier.DeviceState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that a test method should run only when a given package is not installed.
 *
 * <p>You can guarantee that these methods do not run on devices with the package by
 * using {@code DeviceState}.
 *
 * <p>By default the test will be skipped if the package is available. If you'd rather the test
 * fail then use {@code failureMode = FailureMode.FAIL}. If you'd like to uninstall the package if
 * it is installed, see {@link EnsurePackageNotInstalled}.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RequirePackagesNotInstalled.class)
public @interface RequirePackageNotInstalled {
    String value();
    DeviceState.UserType onUser() default DeviceState.UserType.CURRENT_USER;
    FailureMode failureMode() default FailureMode.SKIP;

    /**
     * Weight sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower weight will be resolved before annotations with a higher weight.
     *
     * <p>If there is an order requirement between annotations, ensure that the weight of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Weight can be set to a {@link AnnotationRunPrecedence} constant, or to any {@link int}.
     */
    int weight() default EARLY;
}
