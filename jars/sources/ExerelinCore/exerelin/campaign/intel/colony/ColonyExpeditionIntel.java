package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PEAvertInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.procgen.NameGenData;
import com.fs.starfarer.api.impl.campaign.procgen.ProcgenUsedNames;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import static exerelin.campaign.fleets.InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

public class ColonyExpeditionIntel extends OffensiveFleetIntel implements RaidDelegate {
	
	public static final String MEMORY_KEY_COLONY = "$nex_npcColony";
	public static final float INVADE_STRENGTH = 200;
	public static final float QUEUE_JUMP_REP_PENALTY = 0.15f;
	public static final float QUEUE_JUMP_REP_PENALTY_EARLY = 0.05f;
	public static final String BUTTON_AVERT = "BUTTON_CHANGE_ORDERS";
	
	public static Logger log = Global.getLogger(ColonyExpeditionIntel.class);
	
	protected PlanetAPI planet;
	protected String originalName;
	protected String newName;
	protected ColonyOutcome colonyOutcome;
	protected boolean hostileMode = false;
	protected FactionAPI lastOwner;
		
	public ColonyExpeditionIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
		planet = (PlanetAPI)target.getPrimaryEntity();
		originalName = planet.getName();
		this.target = null;
		//targetFaction = null;
	}
	
	// We don't store the actual target market anywhere, to prevent a bug where the market is cloned in save
	// Instead, get the market from the planet entity when we need it
	// See http://fractalsoftworks.com/forum/index.php?topic=15563
	@Override
	public MarketAPI getTarget() {
		return planet.getMarket();
	}
	
	public PlanetAPI getTargetPlanet() {
		return planet;
	}
	
	@Override
	public void init() {
		log.info("Creating colony expedition intel");
		
		SectorEntityToken gather = from.getPrimaryEntity();
		addStage(new ColonyOrganizeStage(this, from, orgDur));
		
		float successMult = 0.4f;
		ColonyAssembleStage assemble = new ColonyAssembleStage(this, gather);
		assemble.addSource(from);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), planet);

		NexTravelStage travel = new NexTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new ColonyActionStage(this);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new ColonyReturnStage(this));
		addIntelIfNeeded();
	}
	
	protected String getDescString() {
		return getString("intelDesc");
	}
	
	@Override
	protected void addArrivedBullet(TooltipMakerAPI info, Color color, float pad) 
	{
		super.addArrivedBullet(info, color, pad);
		if (getTarget().isInEconomy() && hostileMode) {
			String str = getString("intelBulletArrivedHostile");
			str = StringHelper.substituteToken(str, "$market", getTarget().getName());
			info.addPara(str, color, 0);
		}
	}
	
	public void addInitialDescSection(TooltipMakerAPI info, float initPad) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		
		FactionAPI attacker = getFaction();
		FactionAPI defender = getTarget().getFaction();
		if (lastOwner != null) defender = lastOwner;
		String has = attacker.getDisplayNameHasOrHave();
		String is = attacker.getDisplayNameIsOrAre();
		String locationName = planet.getContainingLocation().getNameWithLowercaseType();
		
		String strDesc = getRaidStrDesc();
		
		String string = getDescString();
		String attackerName = attacker.getDisplayNameWithArticle();
		String defenderName = defender.getDisplayNameWithArticle();
		String planetType = Misc.lcFirst(planet.getTypeNameWithLowerCaseWorld());
		int numFleets = (int) getOrigNumFleets();
				
		Map<String, String> sub = new HashMap<>();
		sub.put("$theFaction", attackerName);
		sub.put("$TheFaction", Misc.ucFirst(attackerName));
		sub.put("$market", planet.getName());
		sub.put("$aOrAn", planet.getSpec().getAOrAn());
		sub.put("$planetType", planetType);
		sub.put("$isOrAre", attacker.getDisplayNameIsOrAre());
		sub.put("$location", locationName);
		sub.put("$strDesc", strDesc);
		sub.put("$numFleets", numFleets + "");
		sub.put("$fleetsStr", numFleets > 1 ? StringHelper.getString("fleets") : StringHelper.getString("fleet"));
		string = StringHelper.substituteTokens(string, sub);
		
		LabelAPI label = info.addPara(string, opad);
		label.setHighlight(attacker.getDisplayNameWithArticleWithoutArticle(), planet.getName(),
				planetType, strDesc, numFleets + "");
		label.setHighlightColors(attacker.getBaseUIColor(), h, planet.getSpec().getIconColor(), h, h);
		
		if (getTarget().isInEconomy() && getTarget().getFaction() != attacker) {
			string = getString("intelDescAlreadyHeld");
			string = StringHelper.substituteToken(string, "$market", getTarget().getName());
			string = StringHelper.substituteToken(string, "$theOtherFaction", defenderName, true);
			info.addPara(string, opad, defender.getBaseUIColor(), defender.getDisplayNameWithArticleWithoutArticle());
		}
		
		if (outcome == null && getTarget().isInEconomy()) {
			addStandardStrengthComparisons(info, getTarget(), defender, true, false, 
					getString("raidName"), getString("raidNamePossessive"));
		}
	}
	
	// intel long description in intel screen
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		//super.createSmallDescription(info, width, height);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		info.addImage(getFactionForUIColors().getLogo(), width, 128, opad);
		
		addInitialDescSection(info, opad);
		
		FactionAPI attacker = getFaction();
		FactionAPI defender = getTarget().getFaction();
		String string;
		
		info.addSectionHeading(StringHelper.getString("status", true), 
				   attacker.getBaseUIColor(), attacker.getDarkUIColor(), Alignment.MID, opad);
		
		// write our own status message for certain cancellation cases
		String targetFactionNameWithArticle = defender.getDisplayNameWithArticle();
		Map<String, String> sub = new HashMap<>();
		sub.put("$market", planet.getName());
		sub.put("$faction", faction.getDisplayName());
		sub.put("$theOtherFaction", targetFactionNameWithArticle);
		sub.put("$TheOtherFaction", Misc.ucFirst(targetFactionNameWithArticle));
		
		if (colonyOutcome != null && colonyOutcome != ColonyOutcome.FAIL && colonyOutcome != ColonyOutcome.AVERTED) {
			String textKey = "";
			switch (colonyOutcome) {
				case QUEUE_JUMPED:
					textKey = "intelOutcomeQueueJumped";
					break;
				case QUEUE_JUMPED_EARLY:
					textKey = "intelOutcomeQueueJumpedEarly";
					break;
				case INVADE_SUCCESS:
					textKey = newName = "intelOutcomeInvadeSuccess";
					break;
				case INVADE_FAILED:
					textKey = "intelOutcomeInvadeFailed";
					break;
				case SUCCESS:
					textKey = newName != null ? "intelOutcomeSuccessWithRename" : "intelOutcomeSuccess";
					sub.put("$oldName", originalName);
					break;
			}
			string = StringHelper.getStringAndSubstituteTokens("nex_colonyFleet", textKey, sub);
			info.addPara(string, opad);
			return;
		}
		
		for (RaidStage stage : stages) {
			stage.showStageInfo(info);
			if (getStageIndex(stage) == failStage) break;
		}
		
		if (getCurrentStage() == 0 && !isFailed()) {
			FactionAPI pf = Global.getSector().getPlayerFaction();
			ButtonAPI button = info.addButton(StringHelper.getString("avert", true), BUTTON_AVERT, 
				  	pf.getBaseUIColor(), pf.getDarkUIColor(),
				  (int)(width), 20f, opad * 2f);
			button.setShortcut(Keyboard.KEY_T, true);
		}
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_AVERT) {
			ui.showDialog(null, new ColonyAvertInteractionDialogPlugin(this, ui));
		}
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		boolean isColonyFleet = extra.fleetType.equals("nex_colonyFleet");
		float distance = ExerelinUtilsMarket.getHyperspaceDistance(market, getTarget());
		
		float myFP = extra.fp;
		if (!useMarketFleetSizeMult)
			myFP *= InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		
		float combat = myFP;
		float tanker = myFP * (0.1f + random.nextFloat() * 0.05f)
				+ TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000;
		float liner = isColonyFleet ? 25 : 0;
		float transport = isColonyFleet ? 10: 0;
		float freighter = 15 + myFP * (0.1f + random.nextFloat() * 0.05f);
		
		float totalFp = combat + tanker + transport + liner + freighter;
		
		FleetParamsV3 params = new FleetParamsV3(
				market, 
				locInHyper,
				factionId,
				route == null ? null : route.getQualityOverride(),
				extra.fleetType,
				combat, // combatPts
				freighter, // freighterPts 
				tanker, // tankerPts
				transport, // transportPts
				liner, // linerPts
				0f, // utilityPts
				0f // qualityMod, won't get used since routes mostly have quality override set
				);
		
		if (!useMarketFleetSizeMult)
			params.ignoreMarketFleetSizeMult = true;
		
		//params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		if (fleet == null || fleet.isEmpty()) return null;
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
			
		if (isColonyFleet) {
			mem.set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
			mem.set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
			mem.set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
		}
		
		mem.set(FleetAIFlags.WANTS_TRANSPONDER_ON, true);
		
		if (hostileMode)
			mem.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
		
		String postId = Ranks.POST_PATROL_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
	public void createColony() {
		log.info("Colonizing market " + getTarget().getName() + ", " + getTarget().getId());
		MarketAPI market = getTarget();
		String factionId = faction.getId();
		
		market.setSize(3);
		market.addCondition("population_3");
		market.setFactionId(factionId);
		market.setPlanetConditionMarketOnly(false);
		market.getMemoryWithoutUpdate().set(MEMORY_KEY_COLONY, true);
		market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, factionId);
		
		// rename generic-name worlds
		if (market.getName().startsWith(planet.getStarSystem().getBaseName() + " ")) {
			String tag = NameGenData.TAG_PLANET;
			if (planet.isMoon()) tag = NameGenData.TAG_PLANET;
			newName = ProcgenUsedNames.pickName(tag, null, null).nameWithRomanSuffixIfAny;
			market.setName(newName);
			planet.setName(newName);
			ProcgenUsedNames.notifyUsed(newName);
		}
		
		if (market.hasCondition(Conditions.DECIVILIZED))
		{
			market.removeCondition(Conditions.DECIVILIZED);
			market.addCondition(Conditions.DECIVILIZED_SUBPOP);
		}
		market.addIndustry(Industries.POPULATION);
		
		ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (config.freeMarket)
		{
			market.getMemoryWithoutUpdate().set("$startingFreeMarket", true);
			market.setFreePort(true);
		}
		
		market.getTariff().modifyFlat("generator", Global.getSector().getFaction(factionId).getTariffFraction());
		ExerelinUtilsMarket.setTariffs(market);
					
		// submarkets
		SectorManager.updateSubmarkets(market, Factions.NEUTRAL, factionId);
		market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
		for (MarketConditionAPI cond : market.getConditions())
		{
			cond.setSurveyed(true);
		}
		
		Global.getSector().getEconomy().addMarket(market, true);
		market.getPrimaryEntity().setFaction(factionId);	// http://fractalsoftworks.com/forum/index.php?topic=8581.0
		
		ExerelinUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, true);
		ColonyManager.buildIndustries(market);
	}
	
	public void notifyQueueJumpedEarly() {
		lastOwner = getTarget().getFaction();
		setOutcome(OffensiveFleetIntel.OffensiveOutcome.FAIL);
		setColonyOutcome(ColonyOutcome.QUEUE_JUMPED_EARLY);
		if (lastOwner.isPlayerFaction()) {
			NexUtilsReputation.adjustPlayerReputation(faction, 
					-ColonyExpeditionIntel.QUEUE_JUMP_REP_PENALTY_EARLY);
		}
	}
	
	@Override
	public RouteFleetAssignmentAI createAssignmentAI(CampaignFleetAPI fleet, RouteManager.RouteData route) {
		if (ExerelinUtilsFleet.getFleetType(fleet).equals("nex_colonyFleet")) {
			log.info("Adding colony expedition AI to fleet");
			ColonyExpeditionAssignmentAI ai = new ColonyExpeditionAssignmentAI(fleet, route, (ColonyActionStage)action);
			return ai;
		}
		RaidAssignmentAI raidAI = new RaidAssignmentAI(fleet, route, (ColonyActionStage)action);
		return raidAI;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = new HashSet<>();
		tags.add(StringHelper.getString("colonies", true));
		if (getTarget().getFaction().isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(getFaction().getId());
		if (!getTarget().getFaction().isNeutralFaction())
			tags.add(getTarget().getFactionId());
		return tags;
	}
	
	// called when event has completed and is preparing to be cleaned up
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		// failing at these stages suggests the colony fleet was destroyed
		if (getFailStage() == 2 || colonyOutcome == ColonyOutcome.INVADE_FAILED 
				|| colonyOutcome == ColonyOutcome.FAIL) {
			ColonyManager.getManager().incrementDeadExpeditions();
		}
		else if (colonyOutcome == ColonyOutcome.AVERTED) {
			ColonyManager.getManager().incrementAvertedExpeditions();
		}
	}
	
	public void setColonyOutcome(ColonyOutcome outcome) {
		this.colonyOutcome = outcome;
	}
	
	public ColonyOutcome getColonyOutcome() {
		return colonyOutcome;
	}
	
	// don't terminate on target-not-in-economy, etc.
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		// source captured before launch
		if (getCurrentStage() <= 0 && from.getFaction() != faction) {
			terminateEvent(OffensiveOutcome.FAIL);
		}
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("nex_colonyFleet", "colonyFleets", true);
	}
	
	@Override
	public String getActionName() {
		return getString("colonyFleet");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return getString("theColonyFleet");
	}
	
	@Override
	public String getForceType() {
		return getString("colonyFleet");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return getString("theColonyFleet");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return getString("forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return getString("forceIsOrAre");
	}
	
	protected String getString(String id) {
		return StringHelper.getString("nex_colonyFleet", id);
	}
			
	@Override
	public String getIcon() {
		return faction.getCrest();
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (colonyOutcome == ColonyOutcome.SUCCESS) return 15;
		return 7;
	}
	
	// runcode exerelin.campaign.intel.colony.ColonyExpeditionIntel.debug("jangala")
	public static void debug(String marketId) {
		MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
		MarketAPI target = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getMarket();
		
		new ColonyExpeditionIntel(market.getFaction(), market, target, 30, 3).init();
	}
	
	public enum ColonyOutcome { SUCCESS, FAIL, QUEUE_JUMPED, 
			QUEUE_JUMPED_EARLY, INVADE_SUCCESS, INVADE_FAILED, AVERTED }
}
