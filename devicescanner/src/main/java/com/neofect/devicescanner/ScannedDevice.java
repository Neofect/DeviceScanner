package com.neofect.devicescanner;

/**
 * @author neo.kim@neofect.com
 * @date Nov 16, 2016
 */
public abstract class ScannedDevice {

	private String identifier;
	private String name;
	private String description;
	private Object device;

	protected ScannedDevice(String identifier, String name, String description, Object device) {
		this.identifier = identifier;
		this.name = name;
		this.description = description;
		this.device = device;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public Object getDevice() {
		return device;
	}

}
