package exerelin.ungp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BuffManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import data.scripts.ungprules.impl.UNGP_BaseRuleEffect;
import data.scripts.ungprules.tags.UNGP_PlayerFleetTag;
import data.scripts.utils.UNGP_BaseBuff;
import java.util.List;

public class CivilianShips extends UNGP_BaseRuleEffect implements UNGP_PlayerFleetTag {
    public static final float BASE_CR_REDUCTION = 0.2f;
	
	protected int difficulty;
	protected float crPenalty;
	protected float maintMult;
	protected float milDP, civDP;
	
	/**
	 * The degree to which military ships out-DP civilian ones. Returns 0 when
	 * civilian DP >= military DP, 1 when all ships are military.
	 * @return 0 to 1.
	 */
	protected float getMilExcess() {
		if (milDP == 0) return 0;
		float value = (milDP - civDP)/milDP;
		if (value < 0) return 0;
		return value;
	}
	
	@Override
	public void updateDifficultyCache(int difficulty) {
		this.difficulty = difficulty;
		Global.getLogger(this.getClass()).info(String.format("Updating cache"));
		crPenalty = getValueByDifficulty(0, difficulty);
		maintMult = getValueByDifficulty(1, difficulty);
	}
	
	@Override
	public float getValueByDifficulty(int index, int difficulty) {
		float excess = getMilExcess();
		crPenalty = BASE_CR_REDUCTION * (difficulty/10) * excess;
		maintMult = 1 + excess * (difficulty/10);
		if (index == 0) return crPenalty;
		else if (index == 1) return maintMult;
		return 0;
	}

    protected class MilitaryDebuff extends UNGP_BaseBuff {
        public MilitaryDebuff(String id, float dur) {
            super(id, dur);
        }

        @Override
        public void apply(FleetMemberAPI member) {
            decreaseMaxCR(member.getStats(), id, crPenalty, rule.getName());
			member.getStats().getSuppliesPerMonth().modifyMult(id, maintMult, rule.getName());
        }
    }
	
	
    @Override
    public void applyPlayerFleetStats(CampaignFleetAPI fleet) {
		civDP = 0;
		milDP = 0;
		
        final List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : members) {
			if (member.isMothballed()) continue;
            if (member.getVariant().hasHullMod(HullMods.CIVGRADE) || member.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS)) {
				civDP += member.getDeploymentPointsCost();
			}
			else milDP += member.getDeploymentPointsCost();
        }
		updateDifficultyCache(difficulty);
		
		//Global.getLogger(this.getClass()).info(String.format("Applying stats: %s civ, %s mil", civDP, milDP));
        
		boolean needsSync = false;
		for (FleetMemberAPI member : members) {
			String buffId = rule.getBuffID();
			boolean civ = member.getVariant().hasHullMod(HullMods.CIVGRADE) || member.getVariant().hasHullMod(HullMods.MILITARIZED_SUBSYSTEMS);
			if (civ) {
				member.getBuffManager().removeBuff(buffId);
				continue;
			}
			
            float buffDur = 0.1f;
			BuffManagerAPI.Buff test = member.getBuffManager().getBuff(buffId);
			if (test instanceof MilitaryDebuff) {
				MilitaryDebuff buff = (MilitaryDebuff) test;
				buff.setDur(buffDur);
			} else {
				member.getBuffManager().addBuff(new MilitaryDebuff(buffId, buffDur));
				needsSync = true;
			}
		}
		if (needsSync) {
			fleet.forceSync();
		}
    }

    @Override
    public void unapplyPlayerFleetStats(CampaignFleetAPI fleet) {
    }
	
	@Override
    public String getDescriptionParams(int index) {
        if (index == 1) return getPercentString(BASE_CR_REDUCTION * 100);
		if (index == 0) return 2 + "×";
        return null;
    }

    @Override
    public String getDescriptionParams(int index, int difficulty) {
        return getDescriptionParams(index);
    }
}