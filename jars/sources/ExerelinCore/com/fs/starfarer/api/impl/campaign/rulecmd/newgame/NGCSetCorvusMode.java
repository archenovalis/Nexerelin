package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;


public class NGCSetCorvusMode extends BaseCommandPlugin {
	 
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		boolean setting = params.get(0).getBoolean(memoryMap);
		ExerelinSetupData.getInstance().corvusMode = setting;
		MemoryAPI memory = memoryMap.get(MemKeys.LOCAL);
		memory.set("$corvusMode", setting, 0);
		
		// disable Prism if SCY will create its own
		if (setting == true && ExerelinSetupData.getInstance().getAllFactions().contains("SCY"))
		{
			ExerelinSetupData.getInstance().prismMarketPresent = false;
			memory.set("$prismMarketPresent", false, 0);
		}
		
		return true;
	}
}