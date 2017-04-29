package ru.drom.gitgrep.server;

public final class GithubServerException extends RuntimeException {
    private final int status;

    public GithubServerException(Throwable cause, int status) {
        super(cause);

        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
