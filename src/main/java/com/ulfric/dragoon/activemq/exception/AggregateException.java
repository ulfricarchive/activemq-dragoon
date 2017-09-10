package com.ulfric.dragoon.activemq.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AggregateException extends Exception {

	private final List<? extends Throwable> causes;

	public AggregateException(String reason, List<? extends Throwable> causes) {
		super(reason);

		Objects.requireNonNull(causes, "causes");
		this.causes = Collections.unmodifiableList(new ArrayList<>(causes));
	}

	public List<? extends Throwable> getCauses() {
		return causes;
	}

}
