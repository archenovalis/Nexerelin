package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import java.awt.Color;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.missions.BuildStation;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import exerelin.plugins.ExerelinCampaignPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

@Log4j
public class RemnantFragments extends HubMissionWithBarEvent implements FleetEventListener, DiscoverEntityListener {
	
	public static final int REQUIRED_HACK_SCORE = 5;
	public static final int MAX_SHARDS = 6;
	public static final float MOTHERSHIP_ORBIT_DIST = 12000;
	
	public static enum Stage {
		GO_TO_SYSTEM,
		FOLLOW_MOTHERSHIP,
		BATTLE,
		SALVAGE_MOTHERSHIP,
		RETURN,
		COMPLETED,
		FAILED
	}	
	
	protected StarSystemAPI system;
	protected SectorEntityToken point1, point2;
	protected SectorEntityToken mothership;
	protected SectorEntityToken derelictShip;
	protected CampaignFleetAPI attacker;
	protected CampaignFleetAPI ally;	
	
	protected PersonAPI engineer;
	protected String aiCore;
	protected boolean distressSent;
	protected float attackerBaseFP;
	protected boolean shardsDeployed;
	protected boolean wonBattle;
	
	// runcode exerelin.campaign.intel.missions.remnant.RemnantFragments.fixDebug()
	public static void fixDebug() {
		RemnantFragments mission = (RemnantFragments)Global.getSector().getMemoryWithoutUpdate().get("$nex_remFragments_ref");
		mission.fixDebug2();
	}
	
	public void fixDebug2() {
		LocData loc = new LocData(null, null, Global.getSector().getCurrentLocation());
		loc.loc = new BaseThemeGenerator.EntityLocation();
		loc.loc.type = BaseThemeGenerator.LocationType.OUTER_SYSTEM;
		loc.loc.location = new Vector2f(10000, 10000);
		loc.loc.orbit = null;
		
		SectorEntityToken node = spawnMissionNode(loc);
		makeImportant(node, "$nex_remFragments_target2");
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		if (!setGlobalReference("$nex_remFragments_ref")) {
			return false;
		}
		
		// pick star system
		requireSystemInterestingAndNotUnsafeOrCore();
		requireSystemNotHasPulsar();
		requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE, Tags.THEME_REMNANT);
		search.systemReqs.add(new BuildStation.SystemUninhabitedReq());
		preferSystemWithinRangeOf(createdAt.getLocationInHyperspace(), 25);
		preferSystemUnexplored();
		system = pickSystem();
		if (system == null) return false;
		
		// Mothership's original location, before it moved away. Has dead ships and debris.
		LocData loc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, system);
		point1 = spawnMissionNode(loc);
		if (!setEntityMissionRef(point1, "$nex_remFragments_ref")) return false;
		makeImportant(point1, "$nex_remFragments_target", Stage.GO_TO_SYSTEM);
		
		// Mothership's new location.
		// null EntityLocationType keeps LocData.updateLocIfNeeded from trying to regenerate the location
		EntityLocation el = new EntityLocation();
		el.type = LocationType.OUTER_SYSTEM;
		int tries = 0;
		do {
			el.location = MathUtils.getPointOnCircumference(system.getCenter().getLocation(), 
					MOTHERSHIP_ORBIT_DIST, genRandom.nextFloat() * 360f);
			tries++;
		} while (isNearCorona(system, el.location) && tries < 10);
		loc = new LocData(el, system);
		point2 = spawnMissionNode(loc);
		if (!setEntityMissionRef(point2, "$nex_remFragments_ref")) return false;
		makeImportant(point2, "$nex_remFragments_target2", Stage.FOLLOW_MOTHERSHIP);
		point2.setCircularOrbit(system.getCenter(), Misc.getAngleInDegrees(el.location), 
				MOTHERSHIP_ORBIT_DIST, MOTHERSHIP_ORBIT_DIST/24);
		
		//point2.addTag(Tags.NON_CLICKABLE);
		// hide point2 till it's needed
		point2.setDiscoverable(true);
		point2.setSensorProfile(0f);
		
		makeImportant(getPerson(), "$nex_remFragments_return", Stage.RETURN);
		
		setStoryMission();
				
		//makeImportant(station, "$nex_remFragments_target", Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE, Stage.BATTLE_DEFECTED);
		//makeImportant(dissonant, "$nex_remM1_returnHere", Stage.RETURN_CORES);
		
		setStartingStage(Stage.GO_TO_SYSTEM);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);
		
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_remFragments_missionCompleted", true);
		endTrigger();
		
		beginStageTrigger(Stage.FAILED);
		triggerSetGlobalMemoryValue("$nex_remFragments_missionFailed", true);
		endTrigger();
		
		// trigger: spawn broken Pather ship and wrecks around the first point
		beginWithinHyperspaceRangeTrigger(system.getHyperspaceAnchor(), 3, false, Stage.GO_TO_SYSTEM);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnPatherDerelict();
				spawnMothership();
			}
		});
		triggerSpawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, new LocData(point1, false));
		triggerSpawnShipGraveyard(Factions.LUDDIC_PATH, 4, 6, new LocData(point1, false));
		triggerSpawnShipGraveyard(Factions.SCAVENGERS, 2, 3, new LocData(point1, false));
		endTrigger();
				
		// trigger: spawn mothership? should this be in the previous part?
		beginStageTrigger(Stage.FOLLOW_MOTHERSHIP);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				if (mothership == null) spawnMothership();
				point2.setDiscoverable(false);
				point2.setSensorProfile(null);
			}
		});
		endTrigger();
		
		// trigger: spawn fleet
		beginStageTrigger(Stage.BATTLE, Stage.RETURN);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnAttackFleet();
			}
		});
		endTrigger();
		
		engineer = Global.getSector().getFaction(Factions.INDEPENDENT).createRandomPerson(genRandom);
		engineer.setRankId(Ranks.CITIZEN);
		engineer.setPostId(Ranks.POST_SCIENTIST);
				
		setRepPersonChangesVeryHigh();
		setRepFactionChangesHigh();
		setCreditReward(CreditReward.VERY_HIGH);	
		
		return true;
	}
	
	/**
	 * Spawns the Pather or TT fleet that attacks the player.
	 */
	public void spawnAttackFleet() {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		
		String factionId = Factions.LUDDIC_PATH;
		float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());
		int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());
		log.info("Player strength: " + playerStr);
		int fp = Math.round(playerStr + capBonus)/4;
		if (fp < 60) fp = 60;
		
		attackerBaseFP = fp;
		
		if (distressSent) {
			factionId = Factions.TRITACHYON;
		} else {
			fp *= 1.5f;
		}
		
		FleetParamsV3 params = new FleetParamsV3(system.getLocation(),
				factionId,
				null,	// quality override
				FleetTypes.TASK_FORCE,
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0.3f);	// quality mod
		params.random = this.genRandom;
		if (distressSent) {
			params.averageSMods = 2;
		}
				
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		attacker = fleet;
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		fleet.getCommanderStats().setSkillLevel(Skills.SENSORS, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		fleet.getMemoryWithoutUpdate().set("$genericHail", true);
		fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_RemFragmentsHail");
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		makeImportant(fleet, "$nex_remFragments_attacker", Stage.BATTLE);
		Misc.addDefeatTrigger(fleet, "Nex_RemFragments_AttackFleetDefeated");
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_remFragments", true, 999);
		if (distressSent) {
			Misc.makeLowRepImpact(fleet, "nex_remFragments");
		} else {
			fleet.getMemoryWithoutUpdate().set("$LP_titheAskedFor", true);
		}
				
		float dist = player.getMaxSensorRangeToDetect(fleet);
		system.addEntity(fleet);
		
		Vector2f pos;
		int tries = 0;
		do {
			pos = MathUtils.getPointOnCircumference(player.getLocation(), dist, genRandom.nextFloat() * 360);
			tries++;
		} while (isNearCorona(system, pos) && tries < 10);
		
		fleet.setLocation(pos.x, pos.y);
		
		fleet.getMemoryWithoutUpdate().set(ExerelinCampaignPlugin.MEM_KEY_BATTLE_PLUGIN, new FragmentsBattleCreationPlugin());
				
		// fleet assignments
		String targetName = StringHelper.getString("yourFleet");
		fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, mothership, 1);
		fleet.addAssignment(FleetAssignment.INTERCEPT, player, 3,
				StringHelper.getFleetAssignmentString("intercepting", targetName));
		
		if (currentStage == Stage.RETURN) {
			// player has scuttled mothership, do nothing except engage the player
		}
		else if (currentStage == Stage.BATTLE)
		{
			// if we win, loot or destroy the mothership
			String actionStr = StringHelper.getFleetAssignmentString(distressSent ? "scavenging" : "attacking", mothership.getName());
			
			fleet.addAssignment(FleetAssignment.DELIVER_CREW, mothership, 100, actionStr);
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, mothership, 0.5f, actionStr, new Script() {
				@Override
				public void run() {
					destroyMothership();
					if (currentStage == Stage.BATTLE || currentStage == Stage.SALVAGE_MOTHERSHIP) {
						setCurrentStage(Stage.FAILED, null, null);
					}
				}
			});
		}
		// finally, return to source
		Misc.giveStandardReturnToSourceAssignments(fleet, false);
		
		// old trigger spawning
		/*
		if (distressSent) {
			triggerCreateFleet(FleetSize.LARGE, FleetQuality.SMOD_2, Factions.TRITACHYON, FleetTypes.TASK_FORCE, point2);
		} else {
			triggerCreateFleet(FleetSize.HUGE, FleetQuality.HIGHER, Factions.LUDDIC_PATH, FleetTypes.TASK_FORCE, point2);
		}
		
		triggerPickLocationAroundEntity(point2, 1500);
		triggerSpawnFleetAtPickedLocation("$nex_remFragments_attackFleet", null);
		triggerMakeFleetIgnoreOtherFleets();
		triggerMakeFleetGoAwayAfterDefeat();
		triggerSetStandardAggroNonPirateFlags();
		triggerFleetMakeFaster(true, 1, false);		
		triggerSetFleetMissionRef("$nex_remFragments_ref"); // so they can be made unimportant
		triggerFleetAddDefeatTrigger("Nex_RemFragments_AttackFleetDefeated");
		triggerFleetPatherNoDefaultTithe();
		triggerSetFleetGenericHailPermanent("Nex_RemFragmentsHail");
		triggerFleetInterceptPlayerOnSight(false, Stage.BATTLE, Stage.RETURN);
		endTrigger();
		*/
	}
	
	/**
	 * Pather derelict in hyperspace above the system.
	 */
	protected void spawnPatherDerelict() {
		List<ShipRolePick> picks = Global.getSector().getFaction(Factions.LUDDIC_PATH).pickShip(ShipRoles.COMBAT_CAPITAL, 
				ShipPickParams.all(), null, genRandom);
		String variantId = picks.get(0).variantId;
		
		DerelictShipData params = new DerelictShipData(
				new ShipRecoverySpecial.PerShipData(variantId, 
						ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);

		SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(Global.getSector().getHyperspace(), 
				Entities.WRECK, Factions.NEUTRAL, params);
		ship.setDiscoverable(true);
		SectorEntityToken orbitFocus = getClosestJumpPointHyperspaceEnd(system);
		if (orbitFocus == null) orbitFocus = system.getHyperspaceAnchor();
		ship.setCircularOrbit(orbitFocus, genRandom.nextFloat() * 360f, 150f, 365f);
		makeImportant(ship, "$nex_remFragments_lpWreck_important", Stage.GO_TO_SYSTEM, Stage.FOLLOW_MOTHERSHIP);
		ship.getMemoryWithoutUpdate().set("$nex_remFragments_lpWreck", true);
	}
	
	public SectorEntityToken getClosestJumpPointHyperspaceEnd(StarSystemAPI system) {
		SectorEntityToken best = null;
		float bestDistSq = 500000000;
		for (SectorEntityToken jump : system.getJumpPoints()) {
			float distSq = MathUtils.getDistanceSquared(jump.getLocation(), point1.getLocation());
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = jump;
			}
		}
		//log.info("Best jump point is " + best);
		
		if (best == null) return null;
		JumpPointAPI bestJ = (JumpPointAPI)best;
		return bestJ.getDestinations().get(0).getDestination();
	}
	
	protected void spawnMothership() {
		mothership = BaseThemeGenerator.addSalvageEntity(system, Entities.DERELICT_MOTHERSHIP, Factions.NEUTRAL);
		mothership.setDiscoverable(true);
		mothership.setLocation(point2.getLocation().x, point2.getLocation().y);
		mothership.setOrbit(point2.getOrbit().makeCopy());
		makeImportant(mothership, "$nex_remFragments_mothership", Stage.GO_TO_SYSTEM, Stage.FOLLOW_MOTHERSHIP, 
				Stage.BATTLE, Stage.SALVAGE_MOTHERSHIP);
		mothership.setInteractionImage("illustrations", "abandoned_station");
		mothership.getMemoryWithoutUpdate().set("$defenderFleetDefeated", true);
		CargoAPI temp = Global.getFactory().createCargo(true);
		temp.addCommodity(Commodities.GAMMA_CORE, 1);
		BaseSalvageSpecial.addExtraSalvage(temp, mothership.getMemoryWithoutUpdate(), -1);
	}
	
	protected void destroyMothership() {
		spawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, new LocData(mothership, false));
		Misc.fadeAndExpire(mothership);
	}
	
	protected void spawnAndJoinShards(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.OMEGA, getString("fragments_fleetName"), true);
		ally = fleet;
		fleet.setNoFactionInName(true);
		
		int shards = (int)(attackerBaseFP/45);
		if (shards < 2) shards = 2;
		if (shards > MAX_SHARDS) shards = MAX_SHARDS;		
		
		for (int i=0; i < shards; i++) {
			boolean left = i < shards/2;	// left shards will spawn on left side
			FleetMemberAPI member = fleet.getFleetData().addFleetMember(left ? "shard_left_Attack": "shard_right_Attack");
			if (aiCore != null) {
				member.setCaptain(Misc.getAICoreOfficerPlugin(aiCore).createPerson(aiCore, Factions.OMEGA, genRandom));
			}
			member.setVariant(member.getVariant().clone(), false, false);
			member.getVariant().setSource(VariantSource.REFIT);
			member.getVariant().addTag(Tags.SHIP_LIMITED_TOOLTIP);
			member.getVariant().addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS);
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		}
		fleet.setFaction(Factions.PLAYER, true);
		
		fleet.getMemoryWithoutUpdate().set("$ignorePlayerCommRequests", true);	// would be awkward for fleet to talk
		
		system.addEntity(fleet);
		fleet.setLocation(player.getLocation().x, player.getLocation().y);
		
		//FleetInteractionDialogPluginImpl fidpi = (FleetInteractionDialogPluginImpl)dialog.getPlugin();
		player.getBattle().join(fleet);
		fleet.getBattle().uncombine();
		fleet.getBattle().genCombined();
		
		shardsDeployed = true;
	}
	
	/**
	 * Called when the attack fleet is defeated in combat, or despawned.
	 * @param dialog
	 * @param memoryMap
	 */
	public void reportFleetDefeated(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if (wonBattle) return;
		wonBattle = true;
		setCurrentStage(Stage.SALVAGE_MOTHERSHIP, dialog, memoryMap);
		mothership.getMemoryWithoutUpdate().set("$nex_remFragments_canSalvage", true);
		
		Misc.giveStandardReturnToSourceAssignments(attacker, true);
		if (ally != null)
			ally.addAssignment(FleetAssignment.ORBIT_PASSIVE, mothership, 999999);
	}
	
	public int getSkillValueForHack(SkillLevelAPI skill) {
		if (!skill.getSkill().getGoverningAptitudeId().equals(Skills.APT_TECHNOLOGY)) return 0;
		switch (skill.getSkill().getId()) {
			case Skills.ELECTRONIC_WARFARE:
			case Skills.AUTOMATED_SHIPS:
				return 3;
			case Skills.CYBERNETIC_AUGMENTATION:
				return 2;
			case Skills.GUNNERY_IMPLANTS:
				if (skill.getLevel() >= 2) return 2; 
				else return 1;
			default:
				return 1;
		}
	}
	
	public int getHackScore() {
		int score = 0;
		for (SkillLevelAPI skill : Global.getSector().getCharacterData().getPerson().getStats().getSkillsCopy())
		{
			score += getSkillValueForHack(skill);
		}
		return score;
	}
	
	protected boolean haveAICore(String id) {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		if (id == null)
			return cargo.getCommodityQuantity(Commodities.ALPHA_CORE) >= 1 || cargo.getCommodityQuantity(Commodities.BETA_CORE) >= 1;
		else
			return cargo.getCommodityQuantity(id) >= 1;
	}
	
	protected boolean canSpawnShards() {
		if (this.aiCore == null) return false;
		return MathUtils.getDistance(Global.getSector().getPlayerFleet(), mothership) < 100;
	}
	
	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}
		
	protected void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		cleanup();
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remFragments_engineer_name", engineer.getNameString());
		set("$nex_remFragments_engineer_heOrShe", engineer.getHeOrShe());
		set("$nex_remFragments_engineer_HeOrShe", Misc.ucFirst(engineer.getHeOrShe()));
		
		set("$nex_remFragments_targetSystem", system.getNameWithLowercaseType());
		set("$nex_remFragments_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remFragments_stage", getCurrentStage());
		
		set("$nex_remFragments_giverName", getPerson().getNameString());
		set("$nex_remFragments_giverFirstName", getPerson().getName().getFirst());
		
		set("$nex_remFragments_bribeHighAmt", creditReward * 2f);
		set("$nex_remFragments_bribeHighStr", Misc.getWithDGS(creditReward * 2f));
		set("$nex_remFragments_bribeMediumAmt", creditReward * 1f);
		set("$nex_remFragments_bribeMediumStr", Misc.getWithDGS(creditReward * 1f));
		
		set("$nex_remFragments_shardsDeployed", shardsDeployed);
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		switch (action) {
			case "pursue_mothership":
				setCurrentStage(Stage.FOLLOW_MOTHERSHIP, dialog, memoryMap);
				Misc.fadeAndExpire(point1);
				return true;
			case "prepEngineer":
				dialog.getInteractionTarget().setActivePerson(engineer);
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				return true;
			case "setDistress":
				distressSent = true;
				return true;
			case "setNoDistress":
				distressSent = false;
				return true;
			case "setHackOptions":
				MemoryAPI mem = memoryMap.get(MemKeys.LOCAL);
				mem.set("$nex_remFragments_canHack", getHackScore() > REQUIRED_HACK_SCORE, 0);
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoHackAI", haveAICore(null));
				return true;
			case "setAIOptions":
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoAlpha", haveAICore(Commodities.ALPHA_CORE));
				dialog.getOptionPanel().setEnabled("nex_remFragments_engineerConvoBeta", haveAICore(Commodities.BETA_CORE));
				return true;
			case "plugAlpha":
				aiCore = Commodities.ALPHA_CORE;
				return true;
			case "plugBeta":
				aiCore = Commodities.BETA_CORE;
				return true;
			case "plugGamma":
				aiCore = Commodities.GAMMA_CORE;
				return true;
			case "battle":
				setCurrentStage(Stage.BATTLE, dialog, memoryMap);
				return true;
			case "evacuate":
				setCurrentStage(Stage.RETURN, dialog, memoryMap);
				destroyMothership();
				return true;
			case "setShardSpawnEnabled":
				boolean inRange = canSpawnShards();
				memoryMap.get(MemKeys.LOCAL).set("$nex_remFragments_inSpawnRange", inRange, 0);
				//dialog.getOptionPanel().setEnabled("nex_remFragments_spawnShards", inRange);
				return true;
			case "acceptedBribe":
				setCurrentStage(Stage.FAILED, dialog, memoryMap);
				return true;
			case "enableSalvage":
				reportFleetDefeated(dialog, memoryMap);
				return true;
			case "spawnAndJoinShards":
				spawnAndJoinShards(dialog, memoryMap);
				return true;
			case "victory":
				reportFleetDefeated(dialog, memoryMap);
				return true;
			case "despawnShards":
				if (ally != null) {
					Misc.giveStandardReturnToSourceAssignments(ally, true);
				}
				setCurrentStage(Stage.RETURN, dialog, memoryMap);
				return true;
			case "complete":
				setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
				return true;
			default:
				break;
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
	}
		
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		String sysName = system.getNameWithLowercaseType();
		
		String str = getString("fragments_boilerplateDesc");
		str = StringHelper.substituteToken(str, "$name", getPerson().getName().getLast());
		info.addPara(str, opad);
		
		if (ExerelinModPlugin.isNexDev) {
			//info.addPara("[debug] We are now in stage: " + currentStage, opad);
		}
		
		if (currentStage == Stage.GO_TO_SYSTEM) 
		{
			info.addPara(getString("fragments_startDesc"), opad, h, sysName);
		} 
		else if (currentStage == Stage.FOLLOW_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_pursueDesc"), opad, h, sysName);
		} 
		else if (currentStage == Stage.BATTLE) 
		{
			info.addPara(getString("fragments_fightDesc"), opad);
		}
		else if (currentStage == Stage.SALVAGE_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_salvageDesc"), opad);
		}
		else if (currentStage == Stage.RETURN) 
		{
			str = getString("fragments_returnDesc");
			str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
			info.addPara(str, opad, h, getPerson().getMarket().getName());
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		//info.addPara("[debug] Current stage: " + currentStage, tc, pad);
		String sysName = system.getNameWithLowercaseTypeShort();
		
		if (currentStage == Stage.GO_TO_SYSTEM || currentStage == Stage.FOLLOW_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_startNextStep"), pad, tc, Misc.getHighlightColor(), sysName);
			return true;
		}
		else if (currentStage == Stage.BATTLE) 
		{
			info.addPara(getString("fragments_fightNextStep"), tc, pad);
			return true;
		}
		else if (currentStage == Stage.SALVAGE_MOTHERSHIP) 
		{
			info.addPara(getString("fragments_salvageNextStep"), tc, pad);
			return true;
		}
		else if (currentStage == Stage.RETURN) 
		{
			String str = getString("fragments_returnNextStep");
			str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
			info.addPara(str, pad, tc, Misc.getHighlightColor(), getPerson().getMarket().getName());
			return true;
		}
		
		return false;
	}

	@Override
	public String getBaseName() {
		return getString("fragments_name");
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}
		
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
		if (fleet == attacker) {
			reportFleetDefeated(null, null);
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		
	}
		
	@Override
	public void reportEntityDiscovered(SectorEntityToken entity) {
		if (entity == mothership) {
			system.removeEntity(point2);
		}
	}
}





