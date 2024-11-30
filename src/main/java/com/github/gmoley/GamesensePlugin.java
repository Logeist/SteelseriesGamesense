package com.github.gmoley;

import com.google.gson.JsonObject;

import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ExecutorServiceExceptionLogger;
import net.runelite.client.util.OSType;
import okhttp3.*;


import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@PluginDescriptor(
        name = "Steelseries Gamesense"
)
public class GamesensePlugin extends Plugin {
    private String sse3Address;
    public static final String game = "RUNELITE"; //required to identify the game on steelseries client
    // made it static so it only needs change in one place if ever needed

    //help vars to determine what should change
    private int lastXp = 0;
    private int lastEnergyTens = -1;
    private int currentHp = 0;
    private int currentPrayer = 0;
    private int tickCount = 0;
    private int lastSpecEnergy = 0;

    private StringBuilder headlineBuilder;
    private StringBuilder sublineBuilder;

    @Inject
    private Client client;
    @Inject
    private OkHttpClient okHttpClient;
    @Inject
    private GameSenseConfig config;
    private ScheduledExecutorService scheduledExecutorService;
    private final ConcurrentLinkedQueue<StatChanged> statChangesQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<VarbitChanged> varbitChangesQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, String> statValues = new HashMap<>();

    @Provides
    GameSenseConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GameSenseConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("GamesensePlugin started!");
        scheduledExecutorService = new ExecutorServiceExceptionLogger(Executors.newSingleThreadScheduledExecutor());
        FindSSE3Port();    //finding the steelseries client port
        initGamesense(); //initialise the events that are displayable on the keyboard
        scheduledExecutorService.scheduleAtFixedRate(this::processStatChanges, 3, 1, TimeUnit.SECONDS);
        scheduledExecutorService.scheduleAtFixedRate(this::processVarbitchanges, 3, 1, TimeUnit.SECONDS);
        scheduledExecutorService.scheduleAtFixedRate(this::sendEnergy, 3, 1, TimeUnit.SECONDS);
        scheduledExecutorService.scheduleAtFixedRate(this::sendCombinedEvent, 5, 1, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("GamesensePlugin stopped!");
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
    }

    @Subscribe
   public void onVarbitChanged(VarbitChanged varbitChanged) {
        varbitChangesQueue.add(varbitChanged);

    }

   @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        statChangesQueue.add(statChanged);
    }

    private void processVarbitchanges() {
        while (!varbitChangesQueue.isEmpty()) {
            VarbitChanged varbitChanged = varbitChangesQueue.poll();
            if (varbitChanged != null) {
                if (varbitChanged.getVarpId() == VarPlayer.CANNON_AMMO) {
                    int remainingCannonballs = varbitChanged.getValue();
                    statValues.put("Cannonballs", "Cbs:" + remainingCannonballs);
                    log.info("Cannonballs remaining: {}", remainingCannonballs);
                } else if (varbitChanged.getVarpId() == VarPlayer.CANNON_STATE) {
                    boolean cannonPlaced = varbitChanged.getValue() == 4;
                    if (!cannonPlaced) {
                        statValues.remove("Cannonballs");
                    }
                } else if (varbitChanged.getVarpId() == VarPlayer.SPECIAL_ATTACK_PERCENT) {
                    int currentSpec = varbitChanged.getValue() / 10;
                    if (config.useCombinedEvent()) {
                        statValues.put("Spec", "Spec:" + currentSpec);
                        log.info("Spec: {}", currentSpec);
                    } else if (config.useOled()) {
                        GameEvent eventOLED = new GameEvent(TrackedStats.SPECIAL_ATTACK, currentSpec, getDisplayName());
                        executePost("game_event", eventOLED.buildJsonOLED());
                        log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
                    } else {
                        GameEvent event = new GameEvent(TrackedStats.SPECIAL_ATTACK, currentSpec);
                        executePost("game_event", event.buildJson()); // Update the special attack
                    }
                }
            }
        }
    }

    private void processStatChanges() {
        while (!statChangesQueue.isEmpty()) {
            StatChanged statChanged = statChangesQueue.poll();
            if (statChanged != null) {
                if (statChanged.getSkill() == Skill.HITPOINTS) {
                    int currentHp = statChanged.getBoostedLevel();
                    int max = statChanged.getLevel();
                    int percent = currentHp * 100 / max;
                    if (config.useCombinedEvent() && config.useOled()) {
                        statValues.put("HP", "HP:" + currentHp);
                    } else if (config.useOled()) {
                        GameEvent eventOLED = new GameEvent(TrackedStats.HEALTH, currentHp, getDisplayName());
                        executePost("game_event ", eventOLED.buildJsonOLED());
                        log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
                    } else {
                        GameEvent event = new GameEvent(TrackedStats.HEALTH, percent);
                        executePost("game_event ", event.buildJson());
                    }

                } else if (statChanged.getSkill() == Skill.PRAYER) {
                    int currentPrayer = statChanged.getBoostedLevel();
                    int max = statChanged.getLevel();
                    int percent = currentPrayer * 100 / max;

                    if (config.useCombinedEvent() && config.useOled()) {
                        statValues.put("Prayer", "Pray:" + currentPrayer);
                    } else if (config.useOled()) {
                        GameEvent eventOLED = new GameEvent(TrackedStats.PRAYER, currentPrayer, getDisplayName());
                        executePost("game_event ", eventOLED.buildJsonOLED());
                        log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
                    } else {
                        GameEvent event = new GameEvent(TrackedStats.PRAYER, percent);
                        executePost("game_event ", event.buildJson());
                    }
                } else {
                    int currentXP = client.getSkillExperience(statChanged.getSkill());
                    int currentLevel = Experience.getLevelForXp(currentXP);
                    int currentLevelXP = Experience.getXpForLevel(currentLevel);
                    int nextLevelXP = currentLevel >= Experience.MAX_VIRT_LEVEL ? Experience.MAX_SKILL_XP : Experience.getXpForLevel(currentLevel + 1);
                    int percent = (int) (Math.min(1.0, (currentXP - currentLevelXP) / (double) (nextLevelXP - currentLevelXP)) * 100);

                    if (config.useCombinedEvent() && config.useOled()) {
                        if (currentXP != 0) {
                        statValues.put("XP", String.format("%s:%s", getShortenedSkill(statChanged.getSkill()), percent + "%")); //QuantityFormatter.quantityToRSDecimalStack(currentXP)
                        } else {
                            statValues.remove("XP");
                        }
                    } else if (config.useOled()) {
                        GameEvent eventOLED = new GameEvent(TrackedStats.CURRENTSKILL, percent, getDisplayName());
                        executePost("game_event ", eventOLED.buildJsonOLED());
                        log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
                    } else {
                        GameEvent event = new GameEvent(TrackedStats.CURRENTSKILL, percent);
                        executePost("game_event ", event.buildJson());
                    }
                }
            }
        }

    }

    private void sendCombinedEvent() {
        if (config.useCombinedEvent()) {
            headlineBuilder = new StringBuilder();
            sublineBuilder = new StringBuilder();

            for (Map.Entry<String, String> entry : statValues.entrySet()) {
                if (entry.getKey().equals("HP") || entry.getKey().equals("Prayer")) {
                    headlineBuilder.append(entry.getValue()).append(" ");
                } else {
                    sublineBuilder.append(entry.getValue()).append(" ");
                }
            }
            if (isLoggedIn()) {
                if (headlineBuilder.length() > 0 || sublineBuilder.length() > 0) {
                    GameEvent omniEvent = new GameEvent(TrackedStats.COMBINED, headlineBuilder.toString().trim(), sublineBuilder.toString().trim());
                    log.info("headline: {}\nsubline: {}", headlineBuilder.toString().trim(), sublineBuilder.toString().trim());
                    executePost("game_event", omniEvent.buildJsonOLEDCombined());
                    log.info("sent OLED event:\n{}", omniEvent.buildJsonOLEDCombined());
                }
            }
        }
    }

    private void sendEnergy() {
        if (isLoggedIn()) {
            int currentEnergy = client.getEnergy();
            int currentEnergyRounded = currentEnergy / 100;
            if (currentEnergyRounded != lastEnergyTens) {
                lastEnergyTens = currentEnergyRounded;
                if (config.useCombinedEvent()) {
                    statValues.put("Run", "Run:" + currentEnergyRounded);
                } else if (config.useOled() && !config.useCombinedEvent()) {
                    GameEvent eventOLED = new GameEvent(TrackedStats.RUN_ENERGY,  currentEnergyRounded, getDisplayName());
                    executePost("game_event", eventOLED.buildJsonOLED());
                    log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
                } else {
                    GameEvent event = new GameEvent(TrackedStats.RUN_ENERGY, currentEnergyRounded);
                    executePost("game_event", event.buildJson()); // Update the run energy
                }
            }
        }
    }

    //find the port to which we should connect
    private void FindSSE3Port() {

        // Open coreProps.json to parse what port SteelSeries Engine 3 is listening on.
        String jsonAddressStr = "";
        String corePropsFileName;
        // Check if we should be using the Windows path to coreProps.json


        if (OSType.getOSType().equals(OSType.Windows)) {
            corePropsFileName = System.getenv("PROGRAMDATA") +
                    "\\SteelSeries\\SteelSeries Engine 3\\coreProps.json";
        } else {
            // Mac path to coreProps.json
            corePropsFileName = "/Library/Application Support/" +
                    "SteelSeries Engine 3/coreProps.json";
        }

        try {
            BufferedReader coreProps = new BufferedReader(new FileReader(corePropsFileName));
            jsonAddressStr = coreProps.readLine();
            System.out.println("Opened coreProps.json and read: " + jsonAddressStr);
            coreProps.close();
        } catch (FileNotFoundException e) {
            System.out.println("coreProps.json not found");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Unhandled exception.");
        }
        // Save the address to SteelSeries Engine 3 for game events.
        if (!jsonAddressStr.equals("")) {
            SseAddressBuilder urlBuilder = new SseAddressBuilder(jsonAddressStr);
            sse3Address = urlBuilder.getUrl();
        }

    }

    private void gameRegister() {
        JsonObject object = new JsonObject();
        object.addProperty("game", game);
        object.addProperty("game_display_name", "Old School Runescape");
        object.addProperty("developer", "Gmoley");
        executePost("game_metadata", object);
    }

    private void registerStat(TrackedStats event, int IconId) {
        StatRegister statRegister = new StatRegister(event, 0, 100, IconId);
        executePost("bind_game_event", statRegister.buildJson());
        if (config.useOled()) {
            StatRegister statRegisterOLED = new StatRegister(event, IconId);
            executePost("bind_game_event", statRegisterOLED.buildJsonOLED());
            log.info("sent OLED event:\n {}", statRegisterOLED.buildJsonOLED().toString());
        }

    }

    private void initGamesense() {
        gameRegister();
        registerStat(TrackedStats.HEALTH, 38);
        registerStat(TrackedStats.PRAYER, 40);
        registerStat(TrackedStats.CURRENTSKILL, 13);
        registerStat(TrackedStats.RUN_ENERGY, 16);
        registerStat(TrackedStats.SPECIAL_ATTACK, 0);
        registerStat(TrackedStats.COMBINED, 0);
    }

    public String getDisplayName() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null) {
            return localPlayer.getName();
        }
        return "null name";
    }

    private boolean isLoggedIn() {
        return client.getGameState() == GameState.LOGGED_IN;
    }

    public String getShortenedSkill(Skill skill) {
        switch (skill) {
            case ATTACK:
                return "ATK";
            case DEFENCE:
                return "DEF";
            case STRENGTH:
                return "STR";
            case HITPOINTS:
                return "HP";
            case RANGED:
                return "RNG";
            case PRAYER:
                return "PRAY";
            case MAGIC:
                return "MAGE";
            case COOKING:
                return "COOK";
            case WOODCUTTING:
                return "WC";
            case FLETCHING:
                return "FLETCH";
            case FISHING:
                return "FISH";
            case FIREMAKING:
                return "FM";
            case CRAFTING:
                return "CRAFT";
            case SMITHING:
                return "SMITH";
            case MINING:
                return "MINE";
            case HERBLORE:
                return "HERB";
            case AGILITY:
                return "AGIL";
            case THIEVING:
                return "THIEV";
            case SLAYER:
                return "SLAY";
            case FARMING:
                return "FARM";
            case RUNECRAFT:
                return "RC";
            case HUNTER:
                return "HUNT";
            case CONSTRUCTION:
                return "CON";
            default:
                return "NULL";
        }
    }

    public void executePost(String extraAddress, JsonObject jsonData) {

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonData.toString());
        Request request = new Request.Builder()
                .url(sse3Address + "/" + extraAddress)
                .post(body)
                .build();
        Call call = okHttpClient.newCall(request);
        try {
            Response response = call.execute();
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
