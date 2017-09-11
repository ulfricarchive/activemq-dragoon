package com.ulfric.dragoon.activemq;

import javax.jms.Session;
import javax.jms.TextMessage;

import com.google.gson.Gson;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;

public class MessageFactory {

	@Inject
	private Session session;

	@Inject
	private Gson gson;

	public TextMessage createTextMessage(String text) {
		return Try.toGet(() -> session.createTextMessage(text));
	}

	public TextMessage createJsonMessage(Object content) {
		return createTextMessage(gson.toJson(content));
	}

}
