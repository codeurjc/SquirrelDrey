package es.codeurjc.squirrel.drey.hello.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Structures {
    public static Map<Integer, Integer> resultsMap = new ConcurrentHashMap<>();
    public static AtomicLong countDown = new AtomicLong();
}