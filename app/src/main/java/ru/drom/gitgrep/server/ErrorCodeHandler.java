package ru.drom.gitgrep.server;

import org.codegist.crest.CRestException;
import org.codegist.crest.handler.ErrorHandler;
import org.codegist.crest.io.Request;
import org.codegist.crest.io.RequestException;
import org.codegist.crest.io.Response;

public final class ErrorCodeHandler implements ErrorHandler {
    @Override
    public <T> T handle(Request request, Exception e) throws Exception {
        if (e instanceof RequestException) {
            final Response response = ((RequestException) e).getResponse();

            if (response != null) {
                final int status = response.getStatusCode();

                switch (status) {
                    case 401:
                    case 404:  // Github's other way of signalling authentication error
                        throw new AuthFailedException(e);
                    default:
                        throw new GithubServerException(e, status);
                    case -1:
                        break;
                }
            }
        }

        throw handleOtherThrowable(e);
    }

    private Exception handleOtherThrowable(Throwable something) {
        return new CRestException("Unexpected type of error: " + something.getMessage(), something);
    }
}
