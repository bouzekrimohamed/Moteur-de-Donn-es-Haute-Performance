package com.example.engine.api;

import com.example.engine.core.TableManager;
import com.example.engine.model.CreateTableRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;
import java.util.Map;

@Path("/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TableResource {

    @Inject
    TableManager tableManager;

    @POST
    public Map<String, Object> create(CreateTableRequest req) throws IOException {
        tableManager.createTable(req);
        return Map.of(
                "status", "OK",
                "table", req.name
        );
    }
}
