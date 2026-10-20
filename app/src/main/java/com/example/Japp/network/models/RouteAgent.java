package com.example.Japp.network.models;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Agent endpoints return these objects directly, without Result<T>. */
public final class RouteAgent {
    private RouteAgent() {}

    public static final class Turn {
        public String role;
        public String content;
        public Turn(String role, String content) { this.role = role; this.content = content; }
    }

    public static final class Stop {
        public String poiId;
        public String name;
        public int recommendedDuration;
        public String notes;
    }

    public static final class Origin {
        public double longitude;
        public double latitude;
    }

    public static final class Request {
        public String requestId;
        public String memoryId;
        public int accountId;
        public String message;
        public List<Turn> history = new ArrayList<>();
        public List<Stop> currentRoute = new ArrayList<>();
        public JsonObject constraints;
        public Origin origin;
    }

    public static final class Event {
        public String tool;
        public String label;
        public String status;
        public Long elapsedMs;
    }

    public static final class Run {
        public String runId;
        public String status;
        public String stage;
        public List<Event> events;
        public Result result;
        public String error;
    }

    public static final class Result {
        public String kind;
        public String message;
        public JsonObject constraints;
        public List<String> questions;
        public List<String> assumptions;
        public List<String> warnings;
        public List<RouteNode> nodes;
        public List<Day> days;
        public List<Leg> legs;
        public double estimatedCost;
        public int unknownCosts;
        public List<String> addedPoiIds;
        public List<String> removedPoiIds;
        public boolean requiresAcceptance;
    }

    public static final class Day {
        public int day;
        public String date;
        public int totalMinutes;
        public int walkingMeters;
        public String finishTime;
    }

    public static final class Leg {
        public int day;
        public String originName;
        public String destinationName;
        public String mode;
        public int minutes;
        public int distanceMeters;
        public List<List<Double>> polyline;
        public List<String> instructions;
        public String source;
        public String note;
    }
}
