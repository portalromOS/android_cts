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
package android.signature.cts.tests;

import android.signature.cts.ApiPresenceChecker;
import android.signature.cts.ClassProvider;
import android.signature.cts.ExcludingClassProvider;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.Assert;

/**
 * Base class for tests of implementations of {@link ApiPresenceChecker}.
 */
public abstract class ApiPresenceCheckerTest<T extends ApiPresenceChecker> {

    static final String VALUE = "VALUE";

    private static ClassProvider createClassProvider(String[] excludedRuntimeClasses) {
        ClassProvider provider = new TestClassesProvider();
        if (excludedRuntimeClasses.length != 0) {
            provider = new ExcludingClassProvider(provider,
                    name -> Arrays.stream(excludedRuntimeClasses)
                            .anyMatch(myname -> myname.equals(name)));
        }
        return provider;
    }

    protected static JDiffClassDescription createClass(String name) {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", name);
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC);
        return clz;
    }

    protected static JDiffClassDescription createAbstractClass(String name) {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", name);
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        return clz;
    }

    protected static void addConstructor(JDiffClassDescription clz, String... paramTypes) {
        JDiffClassDescription.JDiffConstructor constructor =
            new JDiffClassDescription.JDiffConstructor(clz.getShortClassName(), Modifier.PUBLIC);
        if (paramTypes != null) {
            for (String type : paramTypes) {
                constructor.addParam(type);
            }
        }
        clz.addConstructor(constructor);
    }

    protected static void addPublicVoidMethod(JDiffClassDescription clz, String name) {
        clz.addMethod(method(name, Modifier.PUBLIC, "void"));
    }

    protected static void addPublicBooleanField(JDiffClassDescription clz, String name) {
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                name, "boolean", Modifier.PUBLIC, VALUE);
        clz.addField(field);
    }

    void checkSignatureCompliance(JDiffClassDescription classDescription,
            String... excludedRuntimeClassNames) {
        ResultObserver resultObserver = new NoFailures();
        checkSignatureCompliance(classDescription, resultObserver,
                excludedRuntimeClassNames);
    }

    void checkSignatureCompliance(JDiffClassDescription classDescription,
            ResultObserver resultObserver, String... excludedRuntimeClasses) {
        runWithApiChecker(resultObserver,
                checker -> checker.checkSignatureCompliance(classDescription),
                excludedRuntimeClasses);
    }

    void runWithApiChecker(
            ResultObserver resultObserver, Consumer<T> consumer, String... excludedRuntimeClasses) {
        ClassProvider provider = createClassProvider(excludedRuntimeClasses);
        T checker = createChecker(resultObserver, provider);
        consumer.accept(checker);
    }

    protected abstract T createChecker(ResultObserver resultObserver, ClassProvider provider);

    protected JDiffClassDescription createInterface(String name) {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", name);
        clz.setType(JDiffClassDescription.JDiffType.INTERFACE);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        return clz;
    }

    protected static JDiffClassDescription.JDiffMethod method(
            String name, int modifiers, String returnType) {
        return new JDiffClassDescription.JDiffMethod(name, modifiers, returnType);
    }

    protected static class NoFailures implements ResultObserver {

        @Override
        public void notifyFailure(FailureType type, String name, String errmsg) {
            Assert.fail("Saw unexpected test failure: " + name + " failure type: " + type
                    + " error message: " + errmsg);
        }
    }

    protected static class ExpectFailure implements ResultObserver {

        private FailureType expectedType;

        private boolean failureSeen;

        ExpectFailure(FailureType expectedType) {
            this.expectedType = expectedType;
        }

        @Override
        public void notifyFailure(FailureType type, String name, String errMsg) {
            if (type == expectedType) {
                if (failureSeen) {
                    Assert.fail("Saw second test failure: " + name + " failure type: " + type);
                } else {
                    // We've seen the error, mark it and keep going
                    failureSeen = true;
                }
            } else {
                Assert.fail("Saw unexpected test failure: " + name + " failure type: " + type);
            }
        }

        void validate() {
            Assert.assertTrue(failureSeen);
        }
    }

}
