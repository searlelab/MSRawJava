package org.searlelab.msrawjava;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the compatibility status and release since which a public API declaration is supported.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface API {

	Status status();

	String since();

	enum Status {
		STABLE,
		EXPERIMENTAL,
		DEPRECATED
	}
}
