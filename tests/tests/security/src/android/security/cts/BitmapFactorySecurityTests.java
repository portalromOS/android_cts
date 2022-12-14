/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.lang.Exception;
import org.junit.runner.RunWith;
import org.junit.Test;

import android.security.cts.R;

@RunWith(AndroidJUnit4.class)
public class BitmapFactorySecurityTests extends StsExtraBusinessLogicTestCase {
    private FileDescriptor getResource(int resId) {
        try {
            InputStream is = getInstrumentation().getContext().getResources().openRawResource(resId);
            assertNotNull(is);
            File file = File.createTempFile("BitmapFactorySecurityFile" + resId, "img");
            file.deleteOnExit();
            FileOutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = is.read(buffer)) != -1) {
                output.write(buffer, 0, readLength);
            }
            is.close();
            output.close();
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            return pfd.getFileDescriptor();
        } catch (Exception e) {
            fail("Could not get resource " + resId + "! " + e);
            return null;
        }
    }

    /**
     * Verifies that decoding a corrupt ICO does crash.
     */
    @AsbSecurityTest(cveBugId = 38116746)
    @Test
    public void test_android_bug_38116746() {
        FileDescriptor exploitImage = getResource(R.raw.bug_38116746);
        try {
            BitmapFactory.decodeFileDescriptor(exploitImage);
        } catch (OutOfMemoryError e) {
            fail("OOM attempting to decode ICO");
        }

        // This previously crashed in fread. No need to check the output.
        BitmapFactory.decodeFileDescriptor(getResource(R.raw.b38116746_new));
    }

    /**
     * Verifies that decoding a corrupt BMP does crash.
     */
    @AsbSecurityTest(cveBugId = 37627194)
    @Test
    public void test_android_bug_37627194() {
        FileDescriptor exploitImage = getResource(R.raw.bug_37627194);
        try {
            BitmapFactory.decodeFileDescriptor(exploitImage);
        } catch (OutOfMemoryError e) {
            fail("OOM attempting to decode BMP");
        }
    }

    @AsbSecurityTest(cveBugId = 156261521)
    @Test
    public void test_android_bug_156261521() {
        // Previously decoding this would crash.
        FileDescriptor exploitImage = getResource(R.raw.bug_156261521);
        BitmapFactory.decodeFileDescriptor(exploitImage);
    }
}
