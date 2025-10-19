package org.searlelab.msrawjava.exceptions;

public class TdfFormatException extends RuntimeException {
	private static final long serialVersionUID=1L;

	public TdfFormatException(String message, Throwable cause) {
		super(message, cause);
	}

	public TdfFormatException(String message) {
		super(message);
	}

	public TdfFormatException(Throwable cause) {
		super(cause);
	}

}
