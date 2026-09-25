package org.yeauty.exception;

/** A required query parameter was absent, or converted to {@code null}. */
public class MissingRequestParameterException extends IllegalArgumentException {

    public MissingRequestParameterException(String name) {
        super("Required request parameter '" + name + "' is missing");
    }
}
