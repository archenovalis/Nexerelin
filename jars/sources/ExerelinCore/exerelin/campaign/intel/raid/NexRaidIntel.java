package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.OffensiveFleetIntel;
import static exerelin.campaign.fleets.InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.util.Random;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class NexRaidIntel extends OffensiveFleetIntel {
	
	public static Logger log = Global.getLogger(NexRaidIntel.class);
		
	public NexRaidIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
	}
	
	@Override
	public void init() {
		log.info("Creating raid intel");
		
		SectorEntityToken gather = from.getPrimaryEntity();
		
		addStage(new NexOrganizeStage(this, from, orgDur));
		
		float successMult = 0.4f;
		NexRaidAssembleStage assemble = new NexRaidAssembleStage(this, gather);
		assemble.addSource(from);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());

		NexTravelStage travel = new NexTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new NexRaidActionStage(this, system);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new NexReturnStage(this));
		
		if (shouldDisplayIntel())
			queueIntelIfNeeded();
		else if (DEBUG_MODE)
		{
			Global.getSector().getCampaignUI().addMessage("Raid intel from " 
					+ from.getName() + " to " + target.getName() + " concealed due to lack of sniffer");
		}
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("exerelin_raid", "raid");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("exerelin_raid", "theRaid");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("exerelin_raid", "raidForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("exerelin_raid", "theRaidForce");
	}
	
	@Override
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("exerelin_raid", "forceHasOrHave");
	}
	
	@Override
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("exerelin_raid", "forceIsOrAre");
	}
	
	protected float getDistanceToTarget(MarketAPI market) {
		return ExerelinUtilsMarket.getHyperspaceDistance(market, target);
	}
	
	@Override
	public CampaignFleetAPI createFleet(String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
		if (random == null) random = new Random();
				
		RouteManager.OptionalFleetData extra = route.getExtra();
		
		float distance = getDistanceToTarget(market);
		
		float myFP = extra.fp;
		
		float combat = myFP;
		float tanker = myFP * (0.1f + random.nextFloat() * 0.05f)
				+ TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000;
		float transport = 0;
		float freighter = myFP * (0.1f + random.nextFloat() * 0.05f);
		
		float totalFp = combat + tanker + transport + freighter;
		
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
				0f, // linerPts
				0f, // utilityPts
				0f // qualityMod, won't get used since routes mostly have quality override set
				);
		// we don't need the variability involved in this
		// ...no, too much relies on fleet size mult (e.g. doctrine modifiers are piped through here)
		if (!InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT)
			params.ignoreMarketFleetSizeMult = true; 
		
		if (route != null) {
			params.timestamp = route.getTimestamp();
		}
		params.random = random;
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		fleet.setName(InvasionFleetManager.getFleetName(extra.fleetType, factionId, totalFp));
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_RAIDER, true);	// needed to do raids
		
		if (fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PIRATE_BEHAVIOR)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PIRATE, true);
		}
		
		String postId = Ranks.POST_PATROL_COMMANDER;
		String rankId = Ranks.SPACE_CAPTAIN;	//isInvasionFleet ? Ranks.SPACE_ADMIRAL : Ranks.SPACE_COMMANDER;
		
		fleet.getCommander().setPostId(postId);
		fleet.getCommander().setRankId(rankId);
		
		log.info("Created fleet " + fleet.getName() + " of strength " + fleet.getFleetPoints() + "/" + totalFp);
		
		return fleet;
	}
	
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
		//return StringHelper.getString("exerelin_raid", "raid", true);
		return super.getSortString();
	}
	
	@Override
	public String getName() {
		String base = Misc.ucFirst(getFaction().getPersonNamePrefix()) + " " + StringHelper.getString("exerelin_raid", "raid", true);
		if (isEnding()) {
			if (isSendingUpdate() && failStage >= 0) {
				return base + " - " + StringHelper.getString("failed");
			}
			if (action.getStatus() == RaidStageStatus.SUCCESS)
				return base + " - " + StringHelper.getString("successful", true);
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}
			
	@Override
	public String getIcon() {
		//return Global.getSettings().getSpriteName("intel", "nex_invasion");
		return faction.getCrest();
	}
}
