package com.ulfric.dragoon.activemq;

import javax.jms.DeliveryMode;

public enum Persistence {

	NOT_PERSISTENT(DeliveryMode.NON_PERSISTENT),
	PERSISTENT(DeliveryMode.PERSISTENT);

	private final int value;

	Persistence(int value) {
		this.value = value;
	}

	public int intValue() {
		return value;
	}

}
