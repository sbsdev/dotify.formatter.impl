package org.daisy.dotify.formatter.impl;

class RestartPaginationException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2017654555446022589L;

	RestartPaginationException() {
	}

	RestartPaginationException(String message) {
		super(message);
	}

	RestartPaginationException(Throwable cause) {
		super(cause);
	}

	RestartPaginationException(String message, Throwable cause) {
		super(message, cause);
	}

	RestartPaginationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
