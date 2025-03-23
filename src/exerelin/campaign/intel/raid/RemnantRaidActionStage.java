package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.econ.RaidCondition;
import java.util.List;

public class RemnantRaidActionStage extends NexRaidActionStage {

	public RemnantRaidActionStage(RaidIntel raid, StarSystemAPI system) {
		super(raid, system);
	}
	
	@Override
	public void giveReturnOrdersToStragglers(List<RouteManager.RouteData> stragglers) 
	{
		((RemnantRaidIntel)intel).giveReturnOrdersToStragglers(this, stragglers);
		RaidCondition.removeRaidFromConditions(system, intel);
	}
}
