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
package android.signature.cts.api;

import android.os.Bundle;
import android.provider.Settings;
import android.signature.cts.ApiDocumentParser;
import android.signature.cts.ClassProvider;
import android.signature.cts.ExcludingClassProvider;
import android.signature.cts.ExpectedFailuresFilter;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import android.signature.cts.VirtualPath;
import android.signature.cts.VirtualPath.LocalFilePath;
import android.signature.cts.VirtualPath.ResourcePath;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import repackaged.android.test.InstrumentationTestCase;
import repackaged.android.test.InstrumentationTestRunner;

/**
 */
public class AbstractApiTest extends InstrumentationTestCase {

    private static final String TAG = "SignatureTest";

    private TestResultObserver mResultObserver;

    ClassProvider mClassProvider;

    /**
     * The list of expected failures.
     */
    private Collection<String> expectedFailures = Collections.emptyList();

    protected String getGlobalExemptions() {
        return Settings.Global.getString(
                getInstrumentation().getContext().getContentResolver(),
                Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS);
    }

    protected String getGlobalHiddenApiPolicy() {
        return Settings.Global.getString(
                getInstrumentation().getContext().getContentResolver(),
                Settings.Global.HIDDEN_API_POLICY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResultObserver = new TestResultObserver();

        // Get the arguments passed to the instrumentation.
        Bundle instrumentationArgs =
                ((InstrumentationTestRunner) getInstrumentation()).getArguments();

        // Check that the device is in the correct state for running this test.
        assertEquals(
                String.format("Device in bad state: %s is not as expected",
                        Settings.Global.HIDDEN_API_BLACKLIST_EXEMPTIONS),
                getExpectedBlocklistExemptions(),
                getGlobalExemptions());
        assertEquals(
                String.format("Device in bad state: %s is not as expected",
                        Settings.Global.HIDDEN_API_POLICY),
                null,
                getGlobalHiddenApiPolicy());


        // Prepare for a class provider that loads classes from bootclasspath but filters
        // out known inaccessible classes.
        // Note that com.android.internal.R.* inner classes are also excluded as they are
        // not part of API though exist in the runtime.
        mClassProvider = new ExcludingClassProvider(
                new BootClassPathClassesProvider(),
                name -> name != null && name.startsWith("com.android.internal.R."));

        initializeFromArgs(instrumentationArgs);
    }

    /**
     * Initialize the expected failures.
     *
     * <p>Call from with {@code #initializeFromArgs}</p>
     *
     * @param expectedFailures the expected failures.
     */
    protected void initExpectedFailures(Collection<String> expectedFailures) {
        this.expectedFailures = expectedFailures;
        String tag = getClass().getName();
        Log.d(tag, "Expected failure count: " + expectedFailures.size());
        for (String failure: expectedFailures) {
            Log.d(tag, "Expected failure: \"" + failure + "\"");
        }
    }

    protected String getExpectedBlocklistExemptions() {
        return null;
    }

    protected void initializeFromArgs(Bundle instrumentationArgs) throws Exception {

    }

    protected interface RunnableWithResultObserver {
        void run(ResultObserver observer) throws Exception;
    }

    void runWithTestResultObserver(RunnableWithResultObserver runnable) {
        runWithTestResultObserver(expectedFailures, runnable);
    }

    private void runWithTestResultObserver(
            Collection<String> expectedFailures, RunnableWithResultObserver runnable) {
        try {
            ResultObserver observer = mResultObserver;
            if (!expectedFailures.isEmpty()) {
                observer = new ExpectedFailuresFilter(observer, expectedFailures);
            }
            runnable.run(observer);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            writer.write(e.toString());
            writer.write("\n");
            e.printStackTrace(new PrintWriter(writer));
            mResultObserver.notifyFailure(FailureType.CAUGHT_EXCEPTION, e.getClass().getName(),
                    writer.toString());
        }
        mResultObserver.onTestComplete(); // Will throw is there are failures
    }

    static String[] getCommaSeparatedListOptional(Bundle instrumentationArgs, String key) {
        String argument = instrumentationArgs.getString(key);
        if (argument == null) {
            return new String[0];
        }
        return argument.split(",");
    }

    static String[] getCommaSeparatedListRequired(Bundle instrumentationArgs, String key) {
        String argument = instrumentationArgs.getString(key);
        if (argument == null) {
            throw new IllegalStateException("Could not find required argument '" + key + "'");
        }
        return argument.split(",");
    }

    private Stream<VirtualPath> readResource(String resourceName) {
        try {
            ResourcePath resourcePath =
                    VirtualPath.get(getClass().getClassLoader(), resourceName);
            if (resourceName.endsWith(".zip")) {
                // Extract to a temporary file and read from there.
                Path file = extractResourceToFile(resourceName, resourcePath.newInputStream());
                return flattenPaths(VirtualPath.get(file.toString()));
            } else {
                return Stream.of(resourcePath);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Path extractResourceToFile(String resourceName, InputStream is) throws IOException {
        Path tempDirectory = Files.createTempDirectory("signature");
        Path file = tempDirectory.resolve(resourceName);
        Log.i(TAG, "extractResourceToFile: extracting " + resourceName + " to " + file);
        Files.copy(is, file);
        is.close();
        return file;
    }

    /**
     * Given a path in the local file system (possibly of a zip file) flatten it into a stream of
     * virtual paths.
     */
    private Stream<VirtualPath> flattenPaths(LocalFilePath path) {
        try {
            if (path.toString().endsWith(".zip")) {
                return getZipEntryFiles(path);
            } else {
                return Stream.of(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Stream<JDiffClassDescription> parseApiResourcesAsStream(
            ApiDocumentParser apiDocumentParser, String[] apiResources) {
        return Stream.of(apiResources)
                .flatMap(this::readResource)
                .flatMap(apiDocumentParser::parseAsStream);
    }

    /**
     * Get the zip entries that are files.
     *
     * @param path the path to the zip file.
     * @return paths to zip entries
     */
    protected Stream<VirtualPath> getZipEntryFiles(LocalFilePath path) throws IOException {
        @SuppressWarnings("resource")
        ZipFile zip = new ZipFile(path.toFile());
        return zip.stream().map(entry -> VirtualPath.get(zip, entry));
    }
}
