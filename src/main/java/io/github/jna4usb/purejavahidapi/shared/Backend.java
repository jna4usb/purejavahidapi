package io.github.jna4usb.purejavahidapi.shared;

import io.github.jna4usb.purejavahidapi.HidDeviceInfo;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import io.github.jna4usb.purejavahidapi.DeviceRemovalListener;
import io.github.jna4usb.purejavahidapi.HidDevice;

abstract public class Backend {
	// FIXME all this backend stuff is not part of the public API so it should have some access restrictions
	private HashMap<String, HidDevice> m_OpenDevices = new HashMap<String, HidDevice>();

	public abstract void init();

	public abstract void cleanup();

	public abstract List<HidDeviceInfo> enumerateDevices();

	public abstract HidDevice openDevice(HidDeviceInfo path) throws IOException;

	public void removeDevice(String deviceId) {
		m_OpenDevices.remove(deviceId);
	}

	public void addDevice(String deviceId, HidDevice device) {
		m_OpenDevices.put(deviceId, device);
	}

	public HidDevice getDevice(String deviceId) {
		return m_OpenDevices.get(deviceId);
	}

	public void deviceRemoved(String deviceId) {
		HidDevice device = getDevice(deviceId);
		if (device != null) {
			DeviceRemovalListener listener = device.getDeviceRemovalListener();
			device.close();
			if (listener != null)
				listener.onDeviceRemoval(device);
		}
	}

}
