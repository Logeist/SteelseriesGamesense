package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javax.inject.Inject;
import javax.swing.*;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

@Slf4j
@PluginDescriptor(
	name = "Steelseries Gamesense"
)
public class GamesensePlugin extends Plugin
{
	private String sse3Address;
	public static final String game = "RUNELITE"; //required to identify the game on steelseries client made it static so it only needs change in one place if ever needed (probably not)

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

			executePost("game_event ",event.toJsonString());

		} if  (statChanged.getSkill() == Skill.PRAYER){

			int lvl = statChanged.getBoostedLevel();
			int max = statChanged.getLevel();
			int percent = lvl *100 / max;
			currentPrayer = lvl;
			GameEvent event = new GameEvent(TrackedStats.PRAYER,percent);
			executePost("game_event ",event.toJsonString());
		}
		//if there was a change in XP we have had an xp drop
		if (statChanged.getXp() != lastXp) {
			if (statChanged.getSkill() == Skill.PRAYER && currentPrayer ==statChanged.getBoostedLevel()){}
			else if (statChanged.getSkill() == Skill.HITPOINTS && currentHp ==statChanged.getBoostedLevel()){}
			else {
				lastXp = statChanged.getXp();
				int start = getStartXpOfLvl(statChanged.getLevel());
				int end = getEndXPOfLvl(statChanged.getLevel());
				int percent = (lastXp-start) *100 / (end-start);

				if (percent > 100) {percent = 100;}
				GameEvent event = new GameEvent(TrackedStats.CURRENTSKILL,percent);
				executePost("game_event ",event.toJsonString());
			}
		}




	}
	private void sendEnergy(){
		GameEvent event = new GameEvent(TrackedStats.RUN_ENERGY,client.getEnergy());
		executePost("game_event ",event.toJsonString());//update the run energy
	}
	private void sendSpecialAttackPercent(){
		GameEvent event = new GameEvent(TrackedStats.SPECIAL_ATTACK,client.getVar(VarPlayer.SPECIAL_ATTACK_PERCENT)/10);
		executePost("game_event ",event.toJsonString());//update the special attack

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

				JsonObject obj = new Gson().fromJson(jsonAddressStr,JsonObject.class);
				sse3Address = "http://" + obj.get("address").getAsString();
			}

	}

	private void gameRegister(){
		JsonObject object = new JsonObject();
		object.addProperty("game",game);
		object.addProperty("game_display_name","Old School Runescape");
		object.addProperty("developer","Gmoley");
		executePost("game_metadata",object.toString());
	}
	private void registerStat(TrackedStats event, int IconId){
		StatRegister statRegister = new StatRegister(event,0,100, IconId);
		executePost("register_game_event",statRegister.toJsonString());

	}

	private void initGamesense(){

			gameRegister();
			registerStat(TrackedStats.HEALTH,38);
			registerStat(TrackedStats.PRAYER,40);
			registerStat(TrackedStats.CURRENTSKILL,13);
			registerStat(TrackedStats.RUN_ENERGY,16);
			registerStat(TrackedStats.SPECIAL_ATTACK,0);
	}


	public void executePost(String extraAddress, String jsonData)  {

		RequestBody body = RequestBody.create(MediaType.parse("application/json"),jsonData);
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
	private int getEndXPOfLvl(int lvl){
		int xp = getStartXpOfLvl(lvl) + getXpForLvl(lvl);

		return xp;
	}

	private int getStartXpOfLvl(int lvl){
		int total =0;
		for (int i =1;i<=lvl;i++){
			total += getXpForLvl(i);
		}

		return total;
	}

	private int getXpForLvl(int lvl){
		double exp = (float)(lvl-1)/7;
		double amount = 1.0/4.0 *((lvl-1) + 300*Math.pow(2.0,exp));

		return (int) amount;
	}
}
