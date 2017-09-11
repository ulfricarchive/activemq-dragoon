package com.ulfric.dragoon.activemq;

import java.lang.reflect.Type;
import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ulfric.dragoon.exception.Try;

public class MessageHelper {

	private static final Gson GSON = new Gson(); // TODO shared GSON somewhere

	public static <T> T read(Message message, Type asType) {
		JsonElement json = toJson(message);

		if (json == null) {
			return null;
		}

		return GSON.fromJson(json, asType);
	}

	public static JsonElement toJson(Message message) {
		if (message == null) {
			return null;
		}

		if (message instanceof TextMessage) {
			return toJson((TextMessage) message);
		}

		if (message instanceof BytesMessage) {
			return toJson((BytesMessage) message);
		}

		if (message instanceof MapMessage) {
			return toJson((MapMessage) message);
		}

		if (message instanceof ObjectMessage) {
			return toJson((ObjectMessage) message);
		}

		throw new UnsupportedOperationException("Unsupported message type: " + Try.toGet(message::getJMSType));
 	}

	public static JsonElement toJson(TextMessage message) {
		if (message == null) {
			return null;
		}

		return GSON.fromJson(Try.toGet(message::getText), JsonElement.class);
	}

	public static JsonElement toJson(BytesMessage message) {
		if (message == null) {
			return null;
		}

		return GSON.fromJson(Try.toGet(message::readUTF), JsonElement.class);
	}

	public static JsonElement toJson(MapMessage message) {
		if (message == null) {
			return null;
		}

		JsonObject json = new JsonObject();
		Enumeration<String> names = Try.toGet(message::getMapNames);
		while (names.hasMoreElements()) {
			String name = names.nextElement();
			Object content = Try.toGet(() -> message.getObject(name));
			json.add(name, GSON.toJsonTree(content));
		}
		return json;
	}

	public static JsonElement toJson(ObjectMessage message) {
		if (message == null) {
			return null;
		}

		return GSON.toJsonTree(Try.toGet(message::getObject));
	}

	private MessageHelper() {
	}

}
