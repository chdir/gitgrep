package ru.drom.gitgrep.server;

import org.codegist.crest.annotate.ConnectionTimeout;
import org.codegist.crest.annotate.SocketTimeout;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@SocketTimeout(10000)
@ConnectionTimeout(6000)
public interface GithubBaseApi {
    String ERR_DENIED = "access_denied";
    String ERR_SUSPENDED = "application_suspended";

    String HAZARD = "273bf87d19039a6e2246b88856403f52fe6eede0";

    String AUTH_API = "ru.dom.gitgrep.services.AUTH";

    @POST
    @Produces("application/json")
    @Path("login/oauth/access_token")
    AuthResults login(@FormParam("client_id") String clientId,
                      @FormParam("client_secret") String secret,
                      @FormParam("code") String code);
}
