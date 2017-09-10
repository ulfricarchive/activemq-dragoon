package com.ulfric.dragoon.activemq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import javax.jms.JMSException;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.Transport;

import com.ulfric.dragoon.activemq.exception.AggregateException;

public class DragoonConnectionFactory extends ActiveMQConnectionFactory implements AutoCloseable {

	private final Set<ActiveMQConnection> connections = Collections.newSetFromMap(new WeakHashMap<>());

	public DragoonConnectionFactory(String userName, String password, String brokerURL) {
		super(userName, password, brokerURL);
	}

	@Override
	protected ActiveMQConnection createActiveMQConnection(Transport transport, JMSStatsImpl stats) throws Exception {
		ActiveMQConnection connection = super.createActiveMQConnection(transport, stats);

		connections.add(connection);

		return connection;
	}

	@Override
	public void close() throws AggregateException {
		List<JMSException> exceptions = new ArrayList<>();

		for (ActiveMQConnection connection : connections) {
			try {
				if (isCloseable(connection)) {
					connection.close();
				}
			} catch (JMSException exception) {
				exceptions.add(exception);
			}
		}

		connections.clear();

		if (!exceptions.isEmpty()) {
			throw new AggregateException("Failed to close all connections", exceptions);
		}
	}

	private boolean isCloseable(ActiveMQConnection connection) {
		return connection.isClosed() && !connection.isClosing();
	}

}
