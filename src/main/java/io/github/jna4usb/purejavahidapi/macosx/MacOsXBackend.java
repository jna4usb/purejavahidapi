/*
 * Copyright (c) 2014, Kustaa Nyholm / SpareTimeLabs
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 *
 * Neither the name of the Kustaa Nyholm or SpareTimeLabs nor the names of its
 * contributors may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package io.github.jna4usb.purejavahidapi.macosx;

import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFRelease;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFRetain;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFRunLoopGetCurrent;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFSetGetCount;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFSetGetValues;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.kCFAllocatorDefault;
import static io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.kCFRunLoopDefaultMode;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDDeviceOpen;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerClose;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerCopyDevices;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerCreate;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerScheduleWithRunLoop;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerSetDeviceMatching;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.kIOHIDOptionsTypeNone;
import static io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.kIOReturnSuccess;
import static io.github.jna4usb.purejavahidapi.macosx.MacOsXHidDevice.createPathForDevide;
import static io.github.jna4usb.purejavahidapi.macosx.MacOsXHidDevice.processPendingEvents;

import com.sun.jna.Pointer;
import io.github.jna4usb.purejavahidapi.HidDevice;
import io.github.jna4usb.purejavahidapi.HidDeviceInfo;
import io.github.jna4usb.purejavahidapi.macosx.CoreFoundationLibrary.CFSetRef;
import io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDDeviceRef;
import io.github.jna4usb.purejavahidapi.macosx.IOHIDManagerLibrary.IOHIDManagerRef;
import io.github.jna4usb.purejavahidapi.shared.Backend;
import java.util.LinkedList;
import java.util.List;

public class MacOsXBackend extends Backend {

	/* package */static IOHIDManagerRef m_HidManager;

	@Override
	public List<HidDeviceInfo> enumerateDevices() {
		List<HidDeviceInfo> list = new LinkedList<HidDeviceInfo>();
		processPendingEvents();

		IOHIDManagerSetDeviceMatching(MacOsXBackend.m_HidManager, null);
		CFSetRef device_set = IOHIDManagerCopyDevices(MacOsXBackend.m_HidManager);

		int num_devices = (int) CFSetGetCount(device_set);
		Pointer[] device_array = new Pointer[(int) num_devices];

		CFSetGetValues(device_set, device_array);
		for (int i = 0; i < num_devices; i++) {
			IOHIDDeviceRef dev = new IOHIDDeviceRef(device_array[i]);
			MacOsXHidDeviceInfo info = new MacOsXHidDeviceInfo(dev);
			list.add(info);
		}

		CFRelease(device_set);

		return list;
	}

	@Override
	public HidDevice openDevice(HidDeviceInfo deviceInfo) {
		return new MacOsXHidDevice((MacOsXHidDeviceInfo) deviceInfo, this);
	}

	/* package */IOHIDDeviceRef getIOHIDDeviceRef(String path) {
		MacOsXHidDevice.processPendingEvents(); // FIXME why do we call this here???

		CFSetRef device_set = IOHIDManagerCopyDevices(m_HidManager);

		int num_devices = (int) CFSetGetCount(device_set);
		Pointer[] device_array = new Pointer[(int) num_devices];

		CFSetGetValues(device_set, device_array);
		for (int i = 0; i < num_devices; i++) {
			IOHIDDeviceRef os_dev = new IOHIDDeviceRef(device_array[i]);
			String x = createPathForDevide(os_dev);
			if (path.equals(x)) {
				int ret = IOHIDDeviceOpen(os_dev, kIOHIDOptionsTypeNone);
				if (ret == kIOReturnSuccess) {
					CFRetain(os_dev);
					CFRelease(device_set);
					return os_dev;
				} else {
					System.out.printf("IOHIDDeviceOpen: %d,%d,%d\n", (ret >> (32 - 6)) & 0x3f, (ret >> (32 - 6 - 12)) & 0xFFF, ret & 0x3FFF);
				}
			}
		}
		CFRelease(device_set);
		return null;
	}

	public void cleanup() {
		if (m_HidManager != null) {
			IOHIDManagerClose(m_HidManager, kIOHIDOptionsTypeNone);
			CFRelease(m_HidManager);
			m_HidManager = null;
		}
	}

	//--------------------------------------------------------------
	public void init() {
		if (m_HidManager == null) {
			m_HidManager = IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone);
			if (m_HidManager == null)
				throw new RuntimeException("IOHIDManagerCreate call failed");
			IOHIDManagerSetDeviceMatching(m_HidManager, null);
			IOHIDManagerScheduleWithRunLoop(m_HidManager, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
			//tryToReadTheDescriptor();
		}
	}

}
