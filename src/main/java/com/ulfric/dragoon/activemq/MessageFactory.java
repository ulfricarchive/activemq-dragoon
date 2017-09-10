package com.ulfric.dragoon.activemq;

import javax.jms.Session;
import javax.jms.TextMessage;

import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;

public class MessageFactory {

	@Inject
	private Session session;

	public TextMessage createTextMessage(String text) {
		return Try.toGet(() -> session.createTextMessage(text));
	}

}
