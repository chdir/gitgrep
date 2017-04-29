package ru.drom.gitgrep.server;

import org.codegist.crest.annotate.ConnectionTimeout;
import org.codegist.crest.annotate.QueryParam;
import org.codegist.crest.annotate.SocketTimeout;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@SocketTimeout(10000)
@ConnectionTimeout(6000)
@Consumes("application/json")
public interface GithubApi {
    String CLIENT_ID = "bbac735327e764f890f6";

    String SERVICE_API = "ru.dom.gitgrep.services.API";

    @GET
    @Produces("application/vnd.github.mercy-preview+json")
    @Path("/search/repositories")
    RepositoryResults getRepositories(@QueryParam("q") String query,
                                      @QueryParam("page") int page,
                                      @QueryParam("per_page") int perPage) throws AuthFailedException;


}
