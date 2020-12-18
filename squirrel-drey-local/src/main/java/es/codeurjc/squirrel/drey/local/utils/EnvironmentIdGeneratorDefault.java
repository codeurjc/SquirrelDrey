package es.codeurjc.squirrel.drey.local.utils;

import java.util.UUID;

public class EnvironmentIdGeneratorDefault extends EnvironmentIdGenerator{

    public String generateEnvironmentId() {
        return UUID.randomUUID().toString();
    }

}
