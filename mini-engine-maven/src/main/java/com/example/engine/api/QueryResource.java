package com.example.engine.api;

import com.example.engine.core.TableManager;
import com.example.engine.model.QueryRequest;
import com.example.engine.model.QueryResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/query")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class QueryResource {
    @Inject
    TableManager tableManager;

    @POST
    public Response query(QueryRequest request) {
        try {
            QueryResponse response = tableManager.executeQuery(request);
            return Response.status(Response.Status.NOT_IMPLEMENTED).entity(response).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new TableResource.ErrorResponse(ex.getMessage()))
                    .build();
        }
    }
}
