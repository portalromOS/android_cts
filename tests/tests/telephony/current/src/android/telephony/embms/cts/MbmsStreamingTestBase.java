/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony.embms.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.MbmsStreamingSession;
import android.telephony.cts.embmstestapp.CtsStreamingService;
import android.telephony.cts.embmstestapp.ICtsStreamingMiddlewareControl;
import android.telephony.mbms.MbmsStreamingSessionCallback;
import android.telephony.mbms.StreamingServiceInfo;

import com.android.internal.os.SomeArgs;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;

public class MbmsStreamingTestBase {
    protected static final int ASYNC_TIMEOUT = 10000;

    protected static class TestCallback extends MbmsStreamingSessionCallback {
        private final BlockingQueue<SomeArgs> mErrorCalls = new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mStreamingServicesUpdatedCalls =
                new LinkedBlockingQueue<>();
        private final BlockingQueue<SomeArgs> mMiddlewareReadyCalls = new LinkedBlockingQueue<>();
        private int mNumErrorCalls = 0;

        @Override
        public void onError(int errorCode, @Nullable String message) {
            mNumErrorCalls += 1;
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = errorCode;
            args.arg2 = message;
            mErrorCalls.add(args);
        }

        @Override
        public void onStreamingServicesUpdated(List<StreamingServiceInfo> services) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = services;
            mStreamingServicesUpdatedCalls.add(args);
        }

        @Override
        public void onMiddlewareReady() {
            mMiddlewareReadyCalls.add(SomeArgs.obtain());
        }

        public SomeArgs waitOnError() {
            try {
                return mErrorCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public SomeArgs waitOnStreamingServicesUpdated() {
            try {
                return mStreamingServicesUpdatedCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        public boolean waitOnMiddlewareReady() {
            try {
                return mMiddlewareReadyCalls.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS) != null;
            } catch (InterruptedException e) {
                return false;
            }
        }

        public int getNumErrorCalls() {
            return mNumErrorCalls;
        }
    }

    Context mContext;
    HandlerThread mHandlerThread;
    Executor mCallbackExecutor;
    ICtsStreamingMiddlewareControl mMiddlewareControl;
    MbmsStreamingSession mStreamingSession;
    TestCallback mCallback = new TestCallback();

    @Before
    public void setUp() throws Exception {
        mContext = getContext();
        mHandlerThread = new HandlerThread("EmbmsCtsTestWorker");
        mHandlerThread.start();
        mCallbackExecutor = (new Handler(mHandlerThread.getLooper()))::post;
        mCallback = new TestCallback();
        getControlBinder();
        setupStreamingSession();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        mStreamingSession.close();
        mMiddlewareControl.reset();
    }

    private void setupStreamingSession() throws Exception {
        mStreamingSession = MbmsStreamingSession.create(
                mContext, mCallbackExecutor, mCallback);
        assertNotNull(mStreamingSession);
        assertTrue(mCallback.waitOnMiddlewareReady());
        assertEquals(0, mCallback.getNumErrorCalls());
        List initializeCall = (List) mMiddlewareControl.getStreamingSessionCalls().get(0);
        assertEquals(CtsStreamingService.METHOD_INITIALIZE, initializeCall.get(0));
    }

    private void getControlBinder() throws InterruptedException {
        Intent bindIntent = new Intent(CtsStreamingService.CONTROL_INTERFACE_ACTION);
        bindIntent.setComponent(CtsStreamingService.CONTROL_INTERFACE_COMPONENT);
        final CountDownLatch bindLatch = new CountDownLatch(1);

        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mMiddlewareControl = ICtsStreamingMiddlewareControl.Stub.asInterface(service);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mMiddlewareControl = null;
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        bindLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    protected List<List<Object>> getMiddlewareCalls(String methodName) throws RemoteException {
        return ((List<List<Object>>) mMiddlewareControl.getStreamingSessionCalls()).stream()
                .filter((elem) -> elem.get(0).equals(methodName))
                .collect(Collectors.toList());
    }
}