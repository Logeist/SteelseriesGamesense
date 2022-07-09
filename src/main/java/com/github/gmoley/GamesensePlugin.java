package com.github.gmoley;

import com.google.gson.JsonObject;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.OSType;
import okhttp3.*;


import java.io.*;


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
	private int currentHp =0;
	private int currentPrayer =0;
	@Inject
	private Client client;
	@Inject
	private OkHttpClient okHttpClient;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Example started!");
		FindSSE3Port();	//finding the steelseries client port
		initGamesense(); //initialise the events that are displayable on the keyboard
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Example stopped!");
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
		GameEvent event = new GameEvent(TrackedStats.RUN_ENERGY,client.getEnergy());
		executePost("game_event ",event.buildJson());//update the run energy
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

	}

	private void initGamesense(){

			gameRegister();
			registerStat(TrackedStats.HEALTH,38);
			registerStat(TrackedStats.PRAYER,40);
			registerStat(TrackedStats.CURRENTSKILL,13);
			registerStat(TrackedStats.RUN_ENERGY,16);
			registerStat(TrackedStats.SPECIAL_ATTACK,0);
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
