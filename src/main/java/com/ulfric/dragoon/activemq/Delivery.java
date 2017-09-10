package com.ulfric.dragoon.activemq;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(FIELD)
public @interface Delivery {

	Persistence persistence() default Persistence.NOT_PERSISTENT;

	int priority() default 4;

	boolean timestamps() default true;

}
