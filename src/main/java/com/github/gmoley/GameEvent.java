package com.github.gmoley;

import com.google.gson.JsonObject;

public class GameEvent {
    private String game ;
    private TrackedStats gameEvent;
    private int value;
    private String displayName;
    private String headline;
    private String subline;

    public GameEvent(TrackedStats gameEvent, int value) {
        this.game = GamesensePlugin.game;
        this.gameEvent = gameEvent;
        this.value = value;
    }

    public GameEvent(TrackedStats gameEvent, int value, String displayName) {
        this(gameEvent, value);
        this.displayName = displayName;
    }

    public GameEvent(TrackedStats gameEvent, String headline, String subline) {
        this.game = GamesensePlugin.game;
        this.gameEvent = gameEvent;
        this.headline = headline;
        this.subline = subline;
    }

    public JsonObject buildJson(){
        JsonObject object = new JsonObject();
        object.addProperty("game",game);
        object.addProperty("event",gameEvent.name());
        JsonObject data = new JsonObject();
        data.addProperty("value",value);
        object.add("data",data);
        return object;
    }

    public JsonObject buildJsonOLED() {
        JsonObject object = new JsonObject();
        object.addProperty("game", game);
        object.addProperty("event", gameEvent.name());
        JsonObject data = new JsonObject();
        JsonObject frame = new JsonObject();
        frame.addProperty("headline", displayName);
        frame.addProperty("subline", String.valueOf(value));
        data.add("frame", frame);
        object.add("data", data);
        return object;
    }

    public JsonObject buildJsonOLEDCombined() {
        JsonObject object = new JsonObject();
        object.addProperty("game", game);
        object.addProperty("event", gameEvent.name());
        JsonObject data = new JsonObject();
        JsonObject frame = new JsonObject();
        frame.addProperty("headline", this.headline);
        frame.addProperty("subline", this.subline );
        data.add("frame", frame);
        object.add("data", data);
        return object;
    }

    public String toJsonString(){

        return buildJson().toString();
    }

}
