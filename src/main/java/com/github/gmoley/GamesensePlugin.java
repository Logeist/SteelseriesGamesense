package com.github.gmoley;

import com.google.gson.JsonObject;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ExecutorServiceExceptionLogger;
import net.runelite.client.util.OSType;
import okhttp3.*;


import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@PluginDescriptor(
	name = "Steelseries Gamesense"
)
public class GamesensePlugin extends Plugin
{
	private String sse3Address;
	public static final String game = "RUNELITE"; //required to identify the game on steelseries client
	// made it static so it only needs change in one place if ever needed

	//help vars to determine what should change
	private int lastXp =0;
	private int lastEnergyTens = -1;
	private int currentHp =0;
	private int currentPrayer =0;
	@Inject
	private Client client;
	@Inject
	private OkHttpClient okHttpClient;
	@Inject
	private GameSenseConfig config;
	private ScheduledExecutorService scheduledExecutorService;

	@Provides
	GameSenseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GameSenseConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("GamesensePlugin started!");
		FindSSE3Port();	//finding the steelseries client port
		initGamesense(); //initialise the events that are displayable on the keyboard
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("GamesensePlugin stopped!");
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged){

		if (statChanged.getSkill() == Skill.HITPOINTS){
			int lvl = statChanged.getBoostedLevel();
			int max = statChanged.getLevel();
			int percent = lvl *100 / max;
			currentHp = lvl;

			GameEvent event = new GameEvent(TrackedStats.HEALTH,percent);

			executePost("game_event ",event.buildJson());
            log.info("Health changed to: {}", currentHp);
			log.info("config.useOled() is: {}", config.useOled());

			if (config.useOled()) {
				GameEvent eventOLED = new GameEvent(TrackedStats.HEALTH, currentHp, getDisplayName());
				executePost("game_event ", eventOLED.buildJsonOLED());
				log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
			}

		} if  (statChanged.getSkill() == Skill.PRAYER){

			int lvl = statChanged.getBoostedLevel();
			int max = statChanged.getLevel();
			int percent = lvl *100 / max;
			currentPrayer = lvl;
			GameEvent event = new GameEvent(TrackedStats.PRAYER,percent);
			executePost("game_event ",event.buildJson());
		}
		//if there was a change in XP we have had an xp drop
		if (statChanged.getXp() != lastXp) {
			if (statChanged.getSkill() == Skill.PRAYER && currentPrayer ==statChanged.getBoostedLevel()){}
			else if (statChanged.getSkill() == Skill.HITPOINTS && currentHp ==statChanged.getBoostedLevel()){}
			else {
				int currentXP = client.getSkillExperience(statChanged.getSkill());
				int currentLevel = Experience.getLevelForXp(currentXP);
				int currentLevelXP = Experience.getXpForLevel(currentLevel);
				int nextLevelXP = currentLevel >= Experience.MAX_VIRT_LEVEL ? Experience.MAX_SKILL_XP : Experience.getXpForLevel(currentLevel + 1);
				int percent = (int) (Math.min(1.0, (currentXP - currentLevelXP) / (double)(nextLevelXP - currentLevelXP))*100);
				GameEvent event = new GameEvent(TrackedStats.CURRENTSKILL,  percent);
				executePost("game_event ",event.buildJson());

			}
		}




	}
	private void sendEnergy(){
		int currentEnergy = client.getEnergy();
		int currentEnergyTens = currentEnergy / 10;
		if (currentEnergyTens != lastEnergyTens) {
			lastEnergyTens = currentEnergyTens;
			GameEvent event = new GameEvent(TrackedStats.RUN_ENERGY, currentEnergy);
			executePost("game_event", event.buildJson()); // Update the run energy
			if (config.useOled()) {
				GameEvent eventOLED = new GameEvent(TrackedStats.RUN_ENERGY, currentEnergy, getDisplayName());
				executePost("game_event", eventOLED.buildJsonOLED());
				log.info("sent OLED event:\n{}", eventOLED.buildJsonOLED());
			}
		}
	}
	private void sendSpecialAttackPercent(){
		GameEvent event = new GameEvent(TrackedStats.SPECIAL_ATTACK,client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT)/10);

		executePost("game_event ",event.buildJson());//update the special attack

	}

	@Subscribe
	public void onGameTick(GameTick tick){
		sendEnergy();
		sendSpecialAttackPercent();
	}




	//find the port to which we should connect
private void FindSSE3Port() {

		// Open coreProps.json to parse what port SteelSeries Engine 3 is listening on.
		String jsonAddressStr = "";
		String corePropsFileName;
		// Check if we should be using the Windows path to coreProps.json



		if(OSType.getOSType().equals(OSType.Windows)) {
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
			if(!jsonAddressStr.equals("")) {
				SseAddressBuilder urlBuilder = new SseAddressBuilder(jsonAddressStr);
				sse3Address = urlBuilder.getUrl();
			}

	}

	private void gameRegister(){
		JsonObject object = new JsonObject();
		object.addProperty("game",game);
		object.addProperty("game_display_name","Old School Runescape");
		object.addProperty("developer","Gmoley");
		executePost("game_metadata",object);
	}
	private void registerStat(TrackedStats event, int IconId){
		StatRegister statRegister = new StatRegister(event,0,100, IconId);
		executePost("register_game_event",statRegister.buildJson());
		if(config.useOled()) {
			StatRegister statRegisterOLED = new StatRegister(event, IconId);
			executePost("register_game_event", statRegisterOLED.buildJsonOLED());
			log.info("sent OLED event:\n {}", statRegisterOLED.buildJsonOLED().toString());
		}

	}

	private void initGamesense(){
		scheduledExecutorService = new ExecutorServiceExceptionLogger(Executors.newSingleThreadScheduledExecutor());
		gameRegister();
		scheduledExecutorService.schedule(() -> {
			registerStat(TrackedStats.HEALTH,38);
			log.info("Registered Health after delay");
		}, 500, TimeUnit.MILLISECONDS);

		scheduledExecutorService.schedule(() -> {
			registerStat(TrackedStats.PRAYER,40);
			log.info("Registered Prayer after delay");
		}, 1000, TimeUnit.MILLISECONDS);

		scheduledExecutorService.schedule(() -> {
			registerStat(TrackedStats.CURRENTSKILL,13);
			log.info("Registered CurrentSkill after delay");
		}, 1500, TimeUnit.MILLISECONDS);

		scheduledExecutorService.schedule(() -> {
			registerStat(TrackedStats.RUN_ENERGY,16);
			log.info("Registered Run Energy after delay");
		}, 2000, TimeUnit.MILLISECONDS);

		scheduledExecutorService.schedule(() -> {
			registerStat(TrackedStats.SPECIAL_ATTACK,0);
			log.info("Registered Run Energy after delay");
		}, 2500, TimeUnit.MILLISECONDS);

		scheduledExecutorService.shutdown();
	}

	public String getDisplayName() {
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null) {
			return localPlayer.getName();
		}
		return "null name";
	}

	public void executePost(String extraAddress, JsonObject jsonData)  {

		RequestBody body = RequestBody.create(MediaType.parse("application/json"),jsonData.toString());
		Request request = new Request.Builder()
				.url(sse3Address +"/"+ extraAddress)
				.post(body)
				.build();
	Call call = okHttpClient.newCall(request);
	try{
		Response response = call.execute();
		response.close();
	} catch (IOException e){
		e.printStackTrace();
	}
	}
}
