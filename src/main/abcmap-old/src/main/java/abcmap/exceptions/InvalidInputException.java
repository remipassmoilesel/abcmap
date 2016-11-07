package abcmap.exceptions;

public class InvalidInputException extends Exception {

	public InvalidInputException() {
		super();
	}

	public InvalidInputException(String str) {
		super(str);
	}

	public InvalidInputException(Exception e) {
		super(e);
	}

}
