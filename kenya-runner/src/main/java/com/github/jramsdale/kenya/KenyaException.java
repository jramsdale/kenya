package com.github.jramsdale.kenya;

public class KenyaException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public KenyaException() {
		super();
	}
	
	public KenyaException(Throwable e) {
		super(e);
	}
	
	public KenyaException(String message) {
		super(message);
	}
	
	public KenyaException(String message, Throwable e) {
		super(message, e);
	}

}
