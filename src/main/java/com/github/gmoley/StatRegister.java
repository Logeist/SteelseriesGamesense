package com.github.gmoley;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class StatRegister {
    private String game ;
    private TrackedStats gameEvent;
    private int minvalue;
    private int maxvalue;
    private int iconId;

    public StatRegister(TrackedStats gameEvent, int minvalue, int maxvalue, int iconId) {
        this.game = GamesensePlugin.game;
        this.gameEvent = gameEvent;
        this.minvalue = minvalue;
        this.maxvalue = maxvalue;
        this.iconId = iconId;
    }

    public StatRegister(TrackedStats gameEvent, int iconId) {
        this(gameEvent,0,100,iconId);
    }

    public JsonObject buildJson(){
        JsonObject object = new JsonObject();
        object.addProperty("game",game);
        object.addProperty("event",gameEvent.name());
        object.addProperty("min_value",minvalue);
        object.addProperty("max_value",maxvalue);
        object.addProperty("icon_id",iconId);
        return object;
    }

    public JsonObject buildJsonOLED() {
        JsonObject object = new JsonObject();
        object.addProperty("game", game);
        object.addProperty("event", gameEvent.name());
        object.addProperty("value_optional", true);

        JsonObject line1 = new JsonObject();
        line1.addProperty("has-text", true);
        line1.addProperty("context-frame-key", "headline");
        line1.addProperty("bold", true);

        JsonObject line2 = new JsonObject();
        line2.addProperty("has-text", true);
        line2.addProperty("context-frame-key", "subline");

        JsonArray lines = new JsonArray();
        lines.add(line1);
        lines.add(line2);

        JsonObject data = new JsonObject();
        data.add("lines", lines);

        JsonArray datas = new JsonArray();
        datas.add(data);

        JsonObject handler = new JsonObject();
        handler.addProperty("device-type", "screened");
        handler.addProperty("mode", "screen");
        handler.add("datas", datas);

        JsonArray handlers = new JsonArray();
        handlers.add(handler);

        object.add("handlers", handlers);

        return object;
    }

    public String toJsonString(){

        return buildJson().toString();
    }



}
