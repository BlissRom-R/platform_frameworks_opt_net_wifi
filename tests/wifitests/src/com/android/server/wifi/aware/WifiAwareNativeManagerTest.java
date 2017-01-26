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

package com.android.server.wifi.aware;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.wifi.V1_0.IWifiNanIface;
import android.os.Looper;

import com.android.server.wifi.HalDeviceManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test harness for WifiAwareNativeManager.
 */
public class WifiAwareNativeManagerTest {
    private WifiAwareNativeManager mDut;
    @Mock private WifiAwareStateManager mWifiAwareStateManagerMock;
    @Mock private HalDeviceManager mHalDeviceManager;
    @Mock private IWifiNanIface mWifiNanIfaceMock;
    private ArgumentCaptor<HalDeviceManager.ManagerStatusListener> mManagerStatusListenerCaptor =
            ArgumentCaptor.forClass(HalDeviceManager.ManagerStatusListener.class);
    private ArgumentCaptor<HalDeviceManager.InterfaceDestroyedListener>
            mDestroyedListenerCaptor = ArgumentCaptor.forClass(
            HalDeviceManager.InterfaceDestroyedListener.class);
    private ArgumentCaptor<HalDeviceManager.InterfaceAvailableForRequestListener>
            mAvailListenerCaptor = ArgumentCaptor.forClass(
            HalDeviceManager.InterfaceAvailableForRequestListener.class);
    private InOrder mInOrder;
    @Rule public ErrorCollector collector = new ErrorCollector();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDut = new WifiAwareNativeManager(mWifiAwareStateManagerMock,
                mHalDeviceManager);

        mInOrder = inOrder(mWifiAwareStateManagerMock, mHalDeviceManager);
    }

    /**
     * Test the control flow of the manager:
     * 1. onStart
     * 2. null NAN iface
     * 3. onAvailableForRequest
     * 4. non-null NAN iface -> enableUsage
     * 5. onStop -> disableUsage
     * 6. onStart
     * 7. non-null NAN iface -> enableUsage
     * 8. onDestroyed -> disableUsage
     * 9. onStop
     */
    @Test
    public void testControlFlow() {
        // configure HalDeviceManager as ready/wifi started
        when(mHalDeviceManager.isReady()).thenReturn(true);
        when(mHalDeviceManager.isStarted()).thenReturn(true);

        // validate (and capture) that register manage status callback
        mInOrder.verify(mHalDeviceManager).registerStatusListener(
                mManagerStatusListenerCaptor.capture(), any(Looper.class));

        // 1 & 2 onReady: validate that trying to get a NAN interface (make sure gets a NULL)
        when(mHalDeviceManager.createNanIface(
                any(HalDeviceManager.InterfaceDestroyedListener.class),
                any(HalDeviceManager.InterfaceAvailableForRequestListener.class),
                any(Looper.class)))
                .thenReturn(null);

        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture(),
                any(Looper.class));
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 3 & 4 onAvailableForRequest + non-null return value: validate that enables usage
        when(mHalDeviceManager.createNanIface(
                any(HalDeviceManager.InterfaceDestroyedListener.class),
                any(HalDeviceManager.InterfaceAvailableForRequestListener.class),
                any(Looper.class)))
                .thenReturn(mWifiNanIfaceMock);

        mAvailListenerCaptor.getValue().onAvailableForRequest();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture(),
                any(Looper.class));
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 5 onStop: disable usage
        when(mHalDeviceManager.isStarted()).thenReturn(false);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 6 & 7 onReady + non-null NAN interface: enable usage
        when(mHalDeviceManager.isStarted()).thenReturn(true);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        mInOrder.verify(mHalDeviceManager).createNanIface(
                mDestroyedListenerCaptor.capture(), mAvailListenerCaptor.capture(),
                any(Looper.class));
        mInOrder.verify(mWifiAwareStateManagerMock).enableUsage();
        collector.checkThat("non-null interface", mDut.getWifiNanIface(),
                equalTo(mWifiNanIfaceMock));

        // 8 onDestroyed: disable usage
        mDestroyedListenerCaptor.getValue().onDestroyed();

        mInOrder.verify(mWifiAwareStateManagerMock).disableUsage();
        collector.checkThat("null interface", mDut.getWifiNanIface(), nullValue());

        // 9 onStop: nothing more happens
        when(mHalDeviceManager.isStarted()).thenReturn(false);
        mManagerStatusListenerCaptor.getValue().onStatusChanged();

        verifyNoMoreInteractions(mWifiAwareStateManagerMock);
    }
}