package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroundBattleLog {
	
	public static final float ROW_HEIGHT = 24;
	public static final float TURN_NUM_WIDTH = 24;
	
	public static final String TYPE_UNIT_LOSSES = "unitLosses";
	public static final String TYPE_UNIT_MOVED = "unitMove";
	public static final String TYPE_UNIT_ROUTED = "unitRouted";
	public static final String TYPE_UNIT_DESTROYED = "unitDestroyed";
	public static final String TYPE_INDUSTRY_CAPTURED = "industryCaptured";
	public static final String TYPE_EXTERNAL_BOMBARDMENT = "externalBombardment";
	public static final String TYPE_BATTLE_END = "victory";
	public static final String TYPE_XP_GAINED = "gainXP";
	
	public final GroundBattleIntel intel;
	public final int turn;
	public final String type;
	public Map<String, Object> params = new HashMap<>();
	
	public GroundBattleLog(GroundBattleIntel intel, String type, int turn) {
		this.intel = intel;
		this.turn = turn;
		this.type = type;
	}
	
	public void writeLog(TooltipMakerAPI tooltip) {
		String str, side, locStr;
		GroundUnit unit = (GroundUnit)params.get("unit");
		IndustryForBattle loc;
		Color h = Misc.getHighlightColor();
		Color pc = getPanelColor();
		LabelAPI label;
		
		switch (type) {
			case TYPE_UNIT_MOVED:
			case TYPE_UNIT_ROUTED:
				loc = (IndustryForBattle)params.get("location");
				{
					String unitName = unit.name;
					IndustryForBattle prev = (IndustryForBattle)params.get("previous");
					if (loc == null)
						str = String.format(getString("log_unitWithdrawn"), unitName, prev.ind.getCurrentName());
					else if (prev == null)
						str = String.format(getString("log_unitDeployed"), unitName, loc.ind.getCurrentName());
					else
						str = String.format(getString(type.equals(TYPE_UNIT_ROUTED) ? "log_unitRouted": "log_unitMoved"), 
								unitName, prev.ind.getCurrentName(), loc.ind.getCurrentName());
					str = StringHelper.substituteToken(str, "$unitType", unit.type.getName());
					
					label = tooltip.addPara(str, 0);
					if (loc == null) {
						label.setHighlight(unitName, prev.ind.getCurrentName());
						label.setHighlightColors(unit.faction.getBaseUIColor(), h);
					}
					else if (prev == null) {
						label.setHighlight(unitName, loc.ind.getCurrentName());
						label.setHighlightColors(unit.faction.getBaseUIColor(), h);
					}
					else {
						label.setHighlight(unitName, loc.ind.getCurrentName(), prev.ind.getCurrentName());
						label.setHighlightColors(unit.faction.getBaseUIColor(), h, h);
					}
				}				
				break;
			
			case TYPE_UNIT_LOSSES:
				{
					float morale = (float)params.get("morale");
					str = getString("log_unitLosses");
					str = StringHelper.substituteToken(str, "$unitType", unit.type.getName());
					loc = (IndustryForBattle)params.get("location");
					label = tooltip.addPara(str, 0, h, unit.name, 
							loc != null ? loc.ind.getCurrentName() : "<unknown location>",
							(int)params.get("losses") + "", 
							String.format("%.0f", -morale * 100) + "%");
					label.setHighlightColors(unit.faction.getBaseUIColor(), h, h, 
							morale < -0.2 ? Misc.getNegativeHighlightColor() : h);
				}
				break;
			
			case TYPE_UNIT_DESTROYED:
				str = getString("log_unitDestroyed");
				str = StringHelper.substituteToken(str, "$unitType", unit.type.getName());
				side = Misc.ucFirst(StringHelper.getString(unit.isAttacker ? "attacker" : "defender"));
				loc = (IndustryForBattle)params.get("location");
				locStr = loc != null ? loc.ind.getCurrentName() : "<unknown location>";
				label = tooltip.addPara(str, 0, h, side, unit.name, locStr);
				//label.setHighlight(side, unit.name, locStr);
				//Global.getLogger(this.getClass()).info("Unit faction: " + unit.faction);
				label.setHighlightColors(pc, unit.faction.getBaseUIColor(), h);
				break;
				
			case TYPE_INDUSTRY_CAPTURED:
				Float morale = (Float)params.get("morale");
				boolean heldByAttacker = (boolean)params.get("heldByAttacker");
				side = StringHelper.getString(heldByAttacker ? "attacker" : "defender");
				loc = (IndustryForBattle)params.get("industry");
				locStr = loc != null ? loc.ind.getCurrentName() : "<unknown location>";
				
				if (morale == null) {
					str = getString("log_industryCeded");
					label = tooltip.addPara(str, 0, h, locStr, side);
					label.setHighlight(locStr, side);
					label.setHighlightColors(h, pc);
				} else {
					str = getString("log_industryCaptured");
					String moraleStr = String.format("%.0f", morale * 100) + "%";
					label = tooltip.addPara(str, 0, h, locStr, side, moraleStr);
					label.setHighlight(locStr, side, moraleStr);
					label.setHighlightColors(h, pc, pc);
				}
				break;
			case TYPE_EXTERNAL_BOMBARDMENT:
				{
					boolean isSaturation = params.containsKey("isSaturation") && (boolean)params.get("isSaturation");
					str = getString(isSaturation ? "log_satbomb" : "log_tacbomb");
					String type = StringHelper.getString(isSaturation ? "saturationBombardment" 
							: "tacticalBombardment", true);
					if (!isSaturation) {
						List<String> names = (List<String>)params.get("industries");
						str = StringHelper.substituteToken(str, "$industries", 
								StringHelper.writeStringCollection(names, false, true));
					}
					tooltip.addPara(str, 0, isSaturation ? 
							Misc.getNegativeHighlightColor() : h, type);
				}
				
				break;
				
			case TYPE_BATTLE_END:
				Boolean isAttacker = (Boolean)params.get("attackerIsWinner");
				if (isAttacker != null) {
					str = getString("log_victory");
					String victorStr = StringHelper.getString(isAttacker ? "attacker" : "defender", true);
					FactionAPI winner = intel.getSide(isAttacker).getFaction();
					label = tooltip.addPara(str, 0, h, victorStr, winner.getDisplayName());
					label.setHighlight(victorStr, winner.getDisplayName());
					label.setHighlightColors(pc, winner.getBaseUIColor());
				} else {
					str = getString("log_ended");
					tooltip.addPara(str, 0);
				}
				break;
		}
	}
	
	public void writeLog(CustomPanelAPI outer, TooltipMakerAPI scroll, float width) {
		CustomPanelAPI panel = outer.createCustomPanel(width - 6, ROW_HEIGHT, 
				new FramedCustomPanelPlugin(0.25f, getPanelColor(), true));
		
		TooltipMakerAPI turnNumHolder = panel.createUIElement(TURN_NUM_WIDTH, ROW_HEIGHT, false);
		turnNumHolder.setParaSmallInsignia();
		turnNumHolder.addPara(turn + "", 0);
		panel.addUIElement(turnNumHolder).inTL(2, 2);
		
		TooltipMakerAPI text = panel.createUIElement(width - TURN_NUM_WIDTH - 6, ROW_HEIGHT, false);
		writeLog(text);
		panel.addUIElement(text).rightOfTop(turnNumHolder, 2);
		
		scroll.addCustom(panel, 3);
	}
	
	public Color getPanelColor() {
		GroundUnit unit = (GroundUnit)params.get("unit");
		IndustryForBattle ind = (IndustryForBattle)params.get("industry");
		switch (type) {
			case TYPE_UNIT_LOSSES:
			case TYPE_UNIT_ROUTED:
			case TYPE_EXTERNAL_BOMBARDMENT:
				return Misc.getBallisticMountColor();
			case TYPE_UNIT_DESTROYED:
				return intel.getHighlightColorForSide(!unit.isAttacker);
			case TYPE_INDUSTRY_CAPTURED:
				return intel.getHighlightColorForSide((boolean)params.get("heldByAttacker"));
			case TYPE_BATTLE_END:
				Boolean isAttacker = (Boolean)params.get("attackerIsWinner");
				if (isAttacker != null) return intel.getHighlightColorForSide(isAttacker);
				return Misc.getBasePlayerColor();
			default:
				return Misc.getBasePlayerColor();
		}
	}
}
