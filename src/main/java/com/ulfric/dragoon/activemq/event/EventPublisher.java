package com.ulfric.dragoon.activemq.event;

import java.util.Objects;

import javax.jms.MessageProducer;

import com.ulfric.dragoon.activemq.MessageFactory;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;

public class EventPublisher<T extends Event> {

	private final MessageProducer producer;

	@Inject
	private MessageFactory factory;

	public EventPublisher(MessageProducer producer) {
		Objects.requireNonNull(producer, "producer");

		this.producer = producer;
	}

	public void send(T event) {
		Try.toRun(() -> producer.send(factory.createJsonMessage(event)));
	}

}
