package com.ulfric.dragoon.activemq.event;

import com.ulfric.dragoon.activemq.MessageFactory;
import com.ulfric.dragoon.activemq.MessageHelper;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Consumer;

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

	public void setListener(Consumer<T> consumer) {
		Try.toRun(() -> this.consumer.setMessageListener(asListener(consumer)));
	}

	private MessageListener asListener(Consumer<T> consumer) {
		return message -> consumer.accept(MessageHelper.read(message, type));
	}

}
