/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.media.test.client.tests;

import static androidx.media.test.lib.CommonConstants.MOCK_MEDIA_LIBRARY_SERVICE;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowser2;
import androidx.media.MediaController2;
import androidx.media.MediaController2.ControllerCallback;
import androidx.media.SessionCommandGroup2;
import androidx.media.SessionToken2;
import androidx.media.test.client.RemoteMediaLibrarySession;
import androidx.media.test.lib.TestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests {@link MediaBrowser2}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaBrowser2Test extends MediaController2Test {

    @Test
    public void testSubscribe() throws InterruptedException {
        prepareLooper();
        final String testParentId = "testSubscribeId";
        final Bundle testExtras = TestUtils.createTestBundle();
        final SessionToken2 token = new SessionToken2(mContext, MOCK_MEDIA_LIBRARY_SERVICE);

        MediaBrowser2 browser = (MediaBrowser2) createController(token);
        browser.subscribe(testParentId, testExtras);

        // Check that onSubscribe() is called.
        RemoteMediaLibrarySession session = new RemoteMediaLibrarySession(mContext);
        assertTrue(session.isOnSubscribeCalled(testParentId, testExtras));
    }

    @Test
    public void testUnsubscribe() throws InterruptedException {
        prepareLooper();
        final String testParentId = "testUnsubscribeId";
        final SessionToken2 token = new SessionToken2(mContext, MOCK_MEDIA_LIBRARY_SERVICE);

        MediaBrowser2 browser = (MediaBrowser2) createController(token);
        browser.unsubscribe(testParentId);

        // Check that onUnsubscribe() is called.
        RemoteMediaLibrarySession session = new RemoteMediaLibrarySession(mContext);
        assertTrue(session.isOnUnsubscribeCalled(testParentId));
    }

    @Override
    TestControllerInterface onCreateController(final @NonNull SessionToken2 token,
            final @Nullable ControllerCallback callback) throws InterruptedException {
        final AtomicReference<TestControllerInterface> controller = new AtomicReference<>();
        sHandler.postAndSync(new Runnable() {
            @Override
            public void run() {
                // Create controller on the test handler, for changing MediaBrowserCompat's Handler
                // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
                // and commands wouldn't be run if tests codes waits on the test handler.
                controller.set(new TestMediaBrowser(mContext, token, new TestBrowserCallback()));
            }
        });
        return controller.get();
    }

    public static class TestBrowserCallback extends MediaBrowser2.BrowserCallback
            implements MediaSession2TestBase.TestControllerCallbackInterface {
        public final CountDownLatch connectLatch = new CountDownLatch(1);
        public final CountDownLatch disconnectLatch = new CountDownLatch(1);

        TestBrowserCallback() {}

        @CallSuper
        @Override
        public void onConnected(MediaController2 controller, SessionCommandGroup2 commands) {
            connectLatch.countDown();
        }

        @CallSuper
        @Override
        public void onDisconnected(MediaController2 controller) {
            disconnectLatch.countDown();
        }

        @Override
        public void waitForConnect(boolean expect) throws InterruptedException {
            if (expect) {
                assertTrue(connectLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            } else {
                assertFalse(connectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            }
        }

        @Override
        public void waitForDisconnect(boolean expect) throws InterruptedException {
            if (expect) {
                assertTrue(disconnectLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
            } else {
                assertFalse(disconnectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            }
        }

        @Override
        public void setRunnableForOnCustomCommand(Runnable runnable) {}
    }

    public class TestMediaBrowser extends MediaBrowser2 implements
            MediaSession2TestBase.TestControllerInterface {
        private final BrowserCallback mCallback;

        public TestMediaBrowser(@NonNull Context context, @NonNull SessionToken2 token,
                @NonNull BrowserCallback callback) {
            super(context, token, sHandlerExecutor, callback);
            mCallback = callback;
        }

        @Override
        public BrowserCallback getCallback() {
            return mCallback;
        }
    }
}
