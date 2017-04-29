package ru.drom.gitgrep.server;

public final class AuthFailedException extends RuntimeException {
    public AuthFailedException(Exception e) {
        super(e);
    }
}
