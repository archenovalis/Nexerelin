package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ColonyManager;
import exerelin.utilities.StringHelper;


public class Nex_GrantAutonomy extends BaseCommandPlugin {
	
	public static final String MEMORY_KEY_SUSPEND = "$nex_autonomySuspended";
	public static final int REVOKE_UNREST = 2;
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		
		switch(arg)
		{
			case "init":
				memoryMap.get(MemKeys.LOCAL).set("$nex_revokeAutonomyUnrest", REVOKE_UNREST, 0);
				break;
			case "grant":
				grantAutonomy(market);
				break;
			case "revoke":
				revokeAutonomy(market);
				break;
			case "suspend":
				suspendAutonomy(market);
				break;
			case "isAutonomous":
				return !market.isPlayerOwned();
		}
		return true;
	}
	
	public static void grantAutonomy(MarketAPI market) {
		market.setPlayerOwned(false);
		ColonyManager.getManager().checkGatheringPoint(market);
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
		market.getMemoryWithoutUpdate().unset(MEMORY_KEY_SUSPEND);
	}
	
	public static void revokeAutonomy(MarketAPI market) {
		market.setPlayerOwned(true);
		RecentUnrest.get(market, true).add(REVOKE_UNREST, 
				StringHelper.getString("nex_colonies", "autonomyRevoked"));
		FactionAPI player = Global.getSector().getPlayerFaction();
		ColonyManager.reassignAdminIfNeeded(market, player, player);
		market.getMemoryWithoutUpdate().unset(MEMORY_KEY_SUSPEND);
	}
	
	public static void suspendAutonomy(MarketAPI market) {
		market.setPlayerOwned(true);
		
		// already suspended autonomy?
		if (market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_SUSPEND))
			return;
		market.getMemoryWithoutUpdate().set(MEMORY_KEY_SUSPEND, true);
		
		final MarketAPI marketF = market; 
		DelayedActionScript script = new DelayedActionScript(0) {
			public void doAction() {
				if (marketF.isPlayerOwned() && marketF.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_SUSPEND))
				{
					marketF.setPlayerOwned(false);
					marketF.getMemoryWithoutUpdate().unset(MEMORY_KEY_SUSPEND);
					// in case player messed with admin assignment
					FactionAPI player = Global.getSector().getPlayerFaction();
					ColonyManager.reassignAdminIfNeeded(marketF, player, player);
				}
			}
		};
		Global.getSector().addScript(script);
	}
	
	
}