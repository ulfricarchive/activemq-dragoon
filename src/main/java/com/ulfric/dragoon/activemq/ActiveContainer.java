package com.ulfric.dragoon.activemq;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.ulfric.dragoon.ObjectFactory;
import com.ulfric.dragoon.Parameters;
import com.ulfric.dragoon.activemq.configuration.ActiveConfiguration;
import com.ulfric.dragoon.activemq.event.EventPublisher;
import com.ulfric.dragoon.activemq.event.EventSubscriber;
import com.ulfric.dragoon.activemq.exception.AggregateException;
import com.ulfric.dragoon.application.Container;
import com.ulfric.dragoon.cfg4j.Settings;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;
import com.ulfric.dragoon.qualifier.GenericQualifier;
import com.ulfric.dragoon.qualifier.Qualifier;
import com.ulfric.dragoon.stereotype.Stereotypes;
import com.ulfric.dragoon.vault.Secret;

public class ActiveContainer extends Container { // TODO better error handling - retries

	@Secret("activemq/username") // TODO configurable to not use vault
	private String username;

	@Secret("activemq/password") // TODO configurable to not use vault
	private String password;

	@Settings("activemq")
	private ActiveConfiguration config;

	@Inject
	private Logger logger;

	@Inject
	private ObjectFactory factory;

	private DragoonConnectionFactory connections;

	public ActiveContainer() {
		addBootHook(this::register);
		addShutdownHook(this::unregister);
	}

	private void register() { // TODO cleanup method
		factory.bind(DragoonConnectionFactory.class).toValue(connections);
		factory.bind(ActiveMQConnectionFactory.class).toValue(connections);

		factory.bind(Connection.class).toLazy(ignore -> {
			Connection connection = Try.toGet(connections::createConnection);
			Try.toRun(connection::start);
			return connection;
		});

		factory.bind(Session.class).toLazy(ignore ->

		Try.toGet(() -> factory.request(Connection.class).createSession(false, Session.AUTO_ACKNOWLEDGE)));

		factory.bind(Topic.class).toFunction(parameters -> {
			Object[] arguments = parameters.getArguments();

			String name;
			if (arguments.length == 0) {
				name = parameters.getQualifier().getName();
			} else {
				Object argument = arguments[0];

				if (argument instanceof String) {
					name = (String) argument;
 				} else {
 					throw new IllegalArgumentException("Expected String, was " + argument);
 				}
			}

			return Try.toGet(() -> factory.request(Session.class).createTopic(name));
		});

		factory.bind(MessageConsumer.class).toFunction(parameters -> {
			Destination destination = destination(parameters);
			String selector = selector(parameters);
			boolean noLocal = noLocal(parameters);
			return Try.toGet(() -> factory.request(Session.class).createConsumer(destination, selector, noLocal));
		});

		factory.bind(MessageProducer.class).toFunction(parameters -> {
			Destination destination = destination(parameters);
			MessageProducer producer = Try.toGet(() -> factory.request(Session.class).createProducer(destination));

			Delivery delivery = stereotype(parameters, Delivery.class);
			if (delivery != null) {
				Try.toRun(() -> {
					producer.setDeliveryMode(delivery.persistence().intValue());
					producer.setPriority(delivery.priority());
					producer.setDisableMessageTimestamp(!delivery.timestamps());
				});
			}

			return producer;
		});

		factory.bind(EventPublisher.class).toFunction(parameters -> {
			MessageProducer backing = factory.request(MessageProducer.class, parameters);
			return new EventPublisher<>(backing);
		});

		factory.bind(EventSubscriber.class).toFunction(parameters -> {
			MessageConsumer backing = factory.request(MessageConsumer.class, parameters);
			Type genericType = genericType(parameters);
			Type type = parameterType(genericType);
			return new EventSubscriber<>(backing, type);
		});

		connections = new DragoonConnectionFactory(username, password, config.url());
	}

	private Type genericType(Parameters parameters) {
		Qualifier qualifier = parameters.getQualifier();
		if (qualifier instanceof GenericQualifier) {
			return ((GenericQualifier) qualifier).getGenericType();
		}
		return qualifier.getType();
	}

	private Type parameterType(Type type) {
		if (type instanceof ParameterizedType) {
			ParameterizedType parameterizedType = (ParameterizedType) type;
			Type[] parameters = parameterizedType.getActualTypeArguments();
			if (parameters.length == 0) {
				throw new IllegalArgumentException(type + " has no type parameters");
			}
			return parameters[0];
		}

		throw new IllegalArgumentException(type + " is not parameterized");
	}

	private Destination destination(Parameters parameters) {
		return topic(parameters);
	}

	private Topic topic(Parameters parameters) {
		Object[] arguments = parameters.getArguments();

		if (arguments.length == 1) {
			Object argument = arguments[0];
			return createTopicFromArgument(argument);
		}

		com.ulfric.dragoon.activemq.Topic topic = stereotype(parameters, com.ulfric.dragoon.activemq.Topic.class);
		if (topic == null) {
			throw new IllegalArgumentException("Topic request requires an @Topic on qualifier");
		}

		return createTopic(topic.value());
	}

	private String selector(Parameters parameters) {
		Selector selector = stereotype(parameters, Selector.class);

		if (selector == null) {
			return null;
		}

		return selector.value();
	}

	private boolean noLocal(Parameters parameters) {
		return stereotype(parameters, NoLocal.class) != null;
	}

	private <T extends Annotation> T stereotype(Parameters parameters, Class<T> type) {
		return Stereotypes.getFirst(parameters.getQualifier(), type);
	}

	private Topic createTopicFromArgument(Object argument) {
		if (!(argument instanceof String)) {
			throw new IllegalArgumentException("Expected String, was " + argument);
		}

		return createTopic((String) argument);
	}

	private Topic createTopic(String topic) {
		return factory.request(Topic.class, topic);
	}

	private void unregister() { // TODO split up
		factory.bind(DragoonConnectionFactory.class).toNothing();
		factory.bind(ActiveMQConnectionFactory.class).toNothing();
		factory.bind(Connection.class).toNothing();
		factory.bind(Session.class).toNothing();
		factory.bind(Topic.class).toNothing();
		factory.bind(MessageConsumer.class).toNothing();
		factory.bind(MessageProducer.class).toNothing();
		factory.bind(EventPublisher.class).toNothing();
		factory.bind(EventSubscriber.class).toNothing();

		try {
			connections.close();
		} catch (AggregateException exception) {
			for (Throwable cause : exception.getCauses()) {
				logger.log(Level.SEVERE, "Failed to close connection", cause);
			}
		}
	}

}
