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
package android.signature.cts;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for those that process a set of API definition files and perform some checking on
 * them.
 */
public class ApiPresenceChecker {

    final ResultObserver resultObserver;

    final ClassProvider classProvider;

    public ApiPresenceChecker(ClassProvider classProvider, ResultObserver resultObserver) {
        this.classProvider = classProvider;
        this.resultObserver = resultObserver;
    }

    /**
     * Checks test class's name, modifier, fields, constructors, and
     * methods.
     */
    public void checkSignatureCompliance(JDiffClassDescription classDescription) {
        Class<?> runtimeClass = checkClassCompliance(classDescription);
        if (runtimeClass != null) {
            checkFieldsCompliance(classDescription, runtimeClass);
            checkConstructorCompliance(classDescription, runtimeClass);
            checkMethodCompliance(classDescription, runtimeClass);
        }
    }

    /**
     * Checks that the class found through reflection matches the
     * specification from the API xml file.
     *
     * @param classDescription a description of a class in an API.
     */
    private Class<?> checkClassCompliance(JDiffClassDescription classDescription) {
        try {
            Class<?> runtimeClass = ReflectionHelper
                    .findRequiredClass(classDescription, classProvider);

            if (runtimeClass == null) {
                resultObserver.notifyFailure(FailureType.missing(classDescription),
                        classDescription.getAbsoluteClassName(),
                        "Classloader is unable to find " + classDescription
                                .getAbsoluteClassName());
                return null;
            }

            if (!checkClass(classDescription, runtimeClass)) {
                return null;
            }

            return runtimeClass;
        } catch (Exception e) {
            LogHelper.loge("Got exception when checking class compliance", e);
            resultObserver.notifyFailure(
                    FailureType.CAUGHT_EXCEPTION,
                    classDescription.getAbsoluteClassName(),
                    "Exception while checking class compliance!");
            return null;
        }
    }

    /**
     * Implement to provide custom check of the supplied class description.
     *
     * <p>This should not peform checks on the members, those will be done separately depending
     * on the result of this method.
     *
     * @param classDescription the class description to check
     * @param runtimeClass the runtime class corresponding to the class description.
     * @return true if the checks passed and the members should now be checked.
     */
    protected boolean checkClass(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        return true;
    }


    /**
     * Checks all fields in test class for compliance with the API xml.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    private void checkFieldsCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        // A map of field name to field of the fields contained in runtimeClass.
        Map<String, Field> classFieldMap = buildFieldMap(runtimeClass);
        for (JDiffClassDescription.JDiffField field : classDescription.getFields()) {
            try {
                Field f = classFieldMap.get(field.mName);
                if (f == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_FIELD,
                            field.toReadableString(classDescription.getAbsoluteClassName()),
                            "No field with correct signature found:" +
                                    field.toSignatureString());
                } else {
                    checkField(classDescription, runtimeClass, field, f);
                }
            } catch (Exception e) {
                LogHelper.loge("Got exception when checking field compliance", e);
                resultObserver.notifyFailure(
                        FailureType.CAUGHT_EXCEPTION,
                        field.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception while checking field compliance");
            }
        }
    }

    /**
     * Scan a class (an its entire inheritance chain) for fields.
     *
     * @return a {@link Map} of fieldName to {@link Field}
     */
    private static Map<String, Field> buildFieldMap(Class<?> testClass) {
        try {
            return buildFieldMapImpl(testClass);
        } catch (NoClassDefFoundError e) {
            LogHelper.loge("AbstractApiChecker: Could not retrieve fields of " + testClass, e);
            return Collections.emptyMap();
        }
    }

    private static Map<String, Field> buildFieldMapImpl(Class<?> testClass) {
        Map<String, Field> fieldMap = new HashMap<>();
        // Scan the superclass
        if (testClass.getSuperclass() != null) {
            fieldMap.putAll(buildFieldMapImpl(testClass.getSuperclass()));
        }

        // Scan the interfaces
        for (Class<?> interfaceClass : testClass.getInterfaces()) {
            fieldMap.putAll(buildFieldMapImpl(interfaceClass));
        }

        // Check the fields in the test class
        for (Field field : testClass.getDeclaredFields()) {
            fieldMap.put(field.getName(), field);
        }

        return fieldMap;
    }

    protected void checkField(JDiffClassDescription classDescription,
            Class<?> runtimeClass,
            JDiffClassDescription.JDiffField fieldDescription, Field field) {
    }


    /**
     * Checks whether the constructor parsed from API xml file and
     * Java reflection are compliant.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    private void checkConstructorCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        Map<Constructor, String> mismatchReasons = new LinkedHashMap<>();
        for (JDiffClassDescription.JDiffConstructor con : classDescription.getConstructors()) {
            try {
                Constructor<?> c = ReflectionHelper.findMatchingConstructor(runtimeClass, con,
                        mismatchReasons);
                if (c == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_CONSTRUCTOR,
                            con.toReadableString(classDescription.getAbsoluteClassName()),
                            String.format(
                                    "No constructor with correct signature found. The following"
                                            + " constructors were rejected:\n%s",
                                    mismatchReasons.entrySet()
                                            .stream()
                                            .map(e -> String.format("\t\t%s - %s\n",
                                                    e.getKey(), e.getValue()))
                                            .collect(Collectors.joining())));
                } else {
                    checkConstructor(classDescription, runtimeClass, con, c);
                }
            } catch (Exception e) {
                LogHelper.loge("Got exception when checking constructor compliance", e);
                resultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION,
                        con.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception while checking constructor compliance!");
            }
        }
    }

    protected void checkConstructor(JDiffClassDescription classDescription,
            Class<?> runtimeClass,
            JDiffClassDescription.JDiffConstructor ctorDescription, Constructor<?> ctor) {
    }

    /**
     * Checks that the method found through reflection matches the
     * specification from the API xml file.
     *
     * @param classDescription a description of a class in an API.
     * @param runtimeClass the runtime class corresponding to {@code classDescription}.
     */
    private void checkMethodCompliance(JDiffClassDescription classDescription,
            Class<?> runtimeClass) {
        Map<Method, String> mismatchReasons = new LinkedHashMap<>();
        for (JDiffClassDescription.JDiffMethod method : classDescription.getMethods()) {
            try {
                Method m = ReflectionHelper.findMatchingMethod(
                        runtimeClass, method, mismatchReasons);
                if (m == null) {
                    resultObserver.notifyFailure(FailureType.MISSING_METHOD,
                            method.toReadableString(classDescription.getAbsoluteClassName()),
                            String.format(
                                    "No method with correct signature found. The following methods"
                                            + " with the same name were rejected:\n%s",
                                    mismatchReasons.entrySet()
                                            .stream()
                                            .map(e -> String.format("\t\t%s - %s\n",
                                                    e.getKey(), e.getValue()))
                                            .collect(Collectors.joining())));
                } else {
                    checkMethod(classDescription, runtimeClass, method, m);
                }
                // Clear the list.
                mismatchReasons.clear();
            } catch (Exception e) {
                LogHelper.loge("Got exception when checking method compliance", e);
                resultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION,
                        method.toReadableString(classDescription.getAbsoluteClassName()),
                        "Exception while checking method compliance!");
            }
        }
    }

    protected void checkMethod(JDiffClassDescription classDescription,
            Class<?> runtimeClass,
            JDiffClassDescription.JDiffMethod methodDescription, Method method) {
    }
}
