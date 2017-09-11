package com.ulfric.dragoon.activemq.event;

import java.lang.reflect.Type;
import java.util.Objects;

import javax.jms.Message;
import javax.jms.MessageConsumer;

import com.ulfric.dragoon.activemq.MessageFactory;
import com.ulfric.dragoon.activemq.MessageHelper;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;

public class EventSubscriber<T extends Event> {

	private final MessageConsumer consumer;
	private final Type type;

	@Inject
	private MessageFactory factory;

	public EventSubscriber(MessageConsumer consumer, Type type) {
		Objects.requireNonNull(consumer, "consumer");
		Objects.requireNonNull(type, "type");

		this.consumer = consumer;
		this.type = type;
	}

	public T receive() {
		Message message = Try.toGet(consumer::receive);
		return MessageHelper.read(message, type);
	}

}
