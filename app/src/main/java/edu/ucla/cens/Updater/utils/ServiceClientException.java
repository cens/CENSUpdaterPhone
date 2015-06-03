package edu.ucla.cens.Updater.utils;

public class ServiceClientException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ServiceClientException() {
	}

	public ServiceClientException(String detailMessage) {
		super(detailMessage);
	}

	public ServiceClientException(Throwable throwable) {
		super(throwable);
	}

	public ServiceClientException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
