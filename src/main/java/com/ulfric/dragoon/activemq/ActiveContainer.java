package com.ulfric.dragoon.activemq;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.ulfric.dragoon.ObjectFactory;
import com.ulfric.dragoon.activemq.configuration.ActiveConfiguration;
import com.ulfric.dragoon.activemq.event.EventConsumer;
import com.ulfric.dragoon.activemq.event.EventProducer;
import com.ulfric.dragoon.activemq.exception.AggregateException;
import com.ulfric.dragoon.application.Container;
import com.ulfric.dragoon.cfg4j.Settings;
import com.ulfric.dragoon.exception.Try;
import com.ulfric.dragoon.extension.inject.Inject;
import com.ulfric.dragoon.naming.NameHelper;
import com.ulfric.dragoon.stereotype.Stereotypes;
import com.ulfric.dragoon.vault.Secret;

public class ActiveContainer extends Container { // TODO better error handling

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

		factory.bind(Session.class).toLazy(ignore -> // TODO should we create a new session?
			Try.toGet(() -> factory.request(Connection.class).createSession(false, Session.AUTO_ACKNOWLEDGE)));

		factory.bind(Topic.class).toFunction(parameters -> {
			String name = nameFromFieldOrPassed(parameters);

			return Try.toGet(() -> factory.request(Session.class).createTopic(name));
		});

		factory.bind(Queue.class).toFunction(parameters -> {
			String name = nameFromFieldOrPassed(parameters);

			return Try.toGet(() -> factory.request(Session.class).createQueue(name));
		});

		factory.bind(MessageConsumer.class).toFunction(parameters -> { // TODO support both Topic AND Queue on the same consumer
			Destination destination = destination(parameters);
			return Try.toGet(() -> factory.request(Session.class).createConsumer(destination));
		});

		factory.bind(MessageProducer.class).toFunction(parameters -> { // TODO support both Topic AND Queue on the same consumer
			Destination destination = destination(parameters);
			MessageProducer producer = Try.toGet(() -> factory.request(Session.class).createProducer(destination));

			Delivery delivery = annotationFromParameters(parameters, Delivery.class);
			if (delivery != null) {
				Try.toRun(() -> {
					producer.setDeliveryMode(delivery.persistence().intValue());
					producer.setPriority(delivery.priority());
					producer.setDisableMessageTimestamp(!delivery.timestamps());
				});
			}

			return producer;
		});

		factory.bind(EventProducer.class).toFunction(parameters -> {
			MessageProducer backing = factory.request(MessageProducer.class, parameters);
			return new EventProducer<>(backing);
		});

		factory.bind(EventConsumer.class).toFunction(parameters -> {
			MessageConsumer backing = factory.request(MessageConsumer.class, parameters);
			Field field = (Field) parameters[1];
			Type type = getParameterType(field.getGenericType());
			return new EventConsumer<>(backing, type);
		});

		connections = new DragoonConnectionFactory(username, password, config.url());
	}

	private Type getParameterType(Type type) {
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

	private Destination destination(Object[] parameters) {
		if (parameters.length == 0) {
			throw new IllegalArgumentException("Destination requires a topic or a queue");
		}

		Destination destination = topic(parameters);
		if (destination == null) {
			destination = queue(parameters);

			if (destination == null) {
				throw new IllegalArgumentException("Destination requires a topic or a queue");
			}
		}

		return destination;
	}

	private Topic topic(Object[] parameters) { // TODO
		com.ulfric.dragoon.activemq.Topic topic = annotationFromParameters(parameters, com.ulfric.dragoon.activemq.Topic.class);
		if (topic == null) {
			return null;
		}
		return factory.request(Topic.class, topic.value());
	}

	private Queue queue(Object[] parameters) {
		com.ulfric.dragoon.activemq.Queue queue = annotationFromParameters(parameters, com.ulfric.dragoon.activemq.Queue.class);
		if (queue == null) {
			return null;
		}
		return factory.request(Queue.class, queue.value());
	}

	private <A extends Annotation> A annotationFromParameters(Object[] parameters, Class<A> annotation) {
		if (parameters.length == 0) {
			return null;
		}

		Object object = parameters.length == 1 ? parameters[0] : parameters[1];
		return Stereotypes.getFirst(annotationHolder(object), annotation);
	}

	private AnnotatedElement annotationHolder(Object object) {
		if (object == null) {
			return Object.class;
		}

		if (object instanceof AnnotatedElement) {
			return (AnnotatedElement) object;
		}

		return object.getClass();
	}

	private String nameFromFieldOrPassed(Object[] parameters) { // TODO cleanup method, currently unreadable
		if (parameters.length == 0) {
			return null;
		}

		if (parameters.length == 1) {
			return nameOrNull(parameters[0]);
		}

		return nameOrNull(parameters[1]);
	}

	private String nameOrNull(Object object) {
		return object == null ? null : NameHelper.getName(object);
	}

	private void unregister() { // TODO split up
		factory.bind(DragoonConnectionFactory.class).toNothing();
		factory.bind(ActiveMQConnectionFactory.class).toNothing();
		factory.bind(Connection.class).toNothing();
		factory.bind(Session.class).toNothing();
		factory.bind(Topic.class).toNothing();
		factory.bind(Queue.class).toNothing();
		factory.bind(MessageConsumer.class).toNothing();
		factory.bind(MessageProducer.class).toNothing();

		try {
			connections.close();
		} catch (AggregateException exception) {
			for (Throwable cause : exception.getCauses()) {
				logger.log(Level.SEVERE, "Failed to close connection", cause);
			}
		}
	}

}
