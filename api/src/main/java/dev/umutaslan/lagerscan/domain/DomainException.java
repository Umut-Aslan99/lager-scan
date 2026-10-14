package dev.umutaslan.lagerscan.domain;

/** A business rule was violated. The message is shown to the warehouse worker as-is (German). */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
