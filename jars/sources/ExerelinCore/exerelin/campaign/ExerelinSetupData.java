package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.ExerelinUtils;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;

import java.util.ArrayList;
import java.util.Collections;

/* This class functions as a data structure for Exerelin setup
 */

@SuppressWarnings("unchecked")
public final class ExerelinSetupData
{
    private static ExerelinSetupData instance = null;

    // Player setup defaults
    private String playerFaction = "sindrian_diktat";
    private String playerStartingShipVariant = "shuttle_Attack";

    private String[] availableFactions = null;

    // Sector Generation Defaults
    public int numSystems = 8;
    public int maxMoonsPerPlanet = 2;

    public int maxPlanets = 8;
    public int maxStations = 5;
    public int maxAsteroidBelts = 2;
    public int maxSystemSize = 16000;
    public int maxSectorSize = 12000;

    // Game defaults
    public Boolean playerOwnedStationFreeTransfer = false;
    public Boolean confirmedFreeTransfer = false;
    public boolean respawnFactions = true;
    public boolean onlyRespawnStartingFactions = false;
    public int respawnDelay = 60;
    public int numStartFactions = 3;
    public boolean omniFacPresent = true;
    public int maxFactionsInExerelinAtOnce = 3;
    public boolean isSectorPopulated = false;
    public boolean isSectorPartiallyPopulated = false;

    private ExerelinSetupData()
    {
	    // Empty constructor
    }

    public static ExerelinSetupData getInstance()
    {
	if(instance == null)
	{
	    instance = new ExerelinSetupData();
	    ExerelinConfig.loadSettings();
	}

	    return instance;
    }

    public static void resetInstance()
    {
		instance = new ExerelinSetupData();
    }

    public String getPlayerFaction()
    {
		return playerFaction;
    }

    public void setPlayerFaction(String factionId)
    {
	    playerFaction = factionId;
    }

    public String[] getPossibleFactions(Boolean getNiceNames)
    {
		ArrayList possibleFactionsList = new ArrayList();
	
		// Add built in factions
		ExerelinConfig.loadSettings();
		Collections.addAll(possibleFactionsList, this.getBuiltInFactionsList(getNiceNames));
	
		// Add modded factions
		Collections.addAll(possibleFactionsList, this.getModdedFactionsList(getNiceNames));
		return (String[])possibleFactionsList.toArray(new String[possibleFactionsList.size()]);
    }

    public void resetAvailableFactions()
    {
	    availableFactions = null;
    }

    public String[] getAvailableFactions(SectorAPI sector)
    {
	    if (availableFactions == null)
	    {
		    String[] locPossibleFaction = this.getPossibleFactions(false);
		    ArrayList confirmedFactions = new ArrayList(locPossibleFaction.length);

		    if(!onlyRespawnStartingFactions)
		    {
			    for(int i = 0; i < locPossibleFaction.length; i = i + 1)
			    {
				    FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
				    if(fac != null)
					    confirmedFactions.add(fac.getId());
				    else
					    System.out.println("EXERELIN ERROR: Couldn't determine faction for:" + locPossibleFaction[i]);
			    }
			    availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
		    }
		    else
		    {
			    ExerelinUtils.shuffleStringArray(locPossibleFaction);

			    int i = 0;
			    while(confirmedFactions.size() < Math.min(this.numStartFactions, locPossibleFaction.length - 1))
			    {
				    if(locPossibleFaction[i].equalsIgnoreCase(this.playerFaction))
				    {
					    i = i + 1;
					    continue;
				    }

				    FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
				    if(fac != null)
				    {
					    confirmedFactions.add(fac.getId());
				    }
				    else
					    System.out.println("EXERELIN ERROR: Couldn't determine faction for:" + locPossibleFaction[i]);

				    i = i + 1;
			    }
			    confirmedFactions.add(this.getPlayerFaction());

			    availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
		    }
	    }
	    return availableFactions;
    }

    public String[] getModdedFactionsList(Boolean getNiceNames)
    {
	System.out.println("EXERELIN: Getting modded factions");
	ArrayList possibleModdedFactionList = new ArrayList();

	for(int i = 0; i < ExerelinConfig.exerelinFactionConfigs.size(); i++)
	{
	    ExerelinFactionConfig exerelinFactionConfig = (ExerelinFactionConfig)ExerelinConfig.exerelinFactionConfigs.get(i);
	    if(exerelinFactionConfig.playableFaction
		    && isFactionInstalled(exerelinFactionConfig.factionId, exerelinFactionConfig.uniqueModClassName))
	    {
		if(getNiceNames)
		    possibleModdedFactionList.add(exerelinFactionConfig.factionNiceName);
		else
		    possibleModdedFactionList.add(exerelinFactionConfig.factionId);
	    }
	}
        return (String[])possibleModdedFactionList.toArray(new String[possibleModdedFactionList.size()]);
    }

    public String[] getBuiltInFactionsList(Boolean getNiceNames)
    {
        ArrayList possibleBuiltInFactionList = new ArrayList();

        for(int i = 0; i < ExerelinConfig.exerelinFactionConfigs.size(); i++)
        {
            ExerelinFactionConfig exerelinFactionConfig = (ExerelinFactionConfig)ExerelinConfig.exerelinFactionConfigs.get(i);
            if(exerelinFactionConfig.playableFaction && exerelinFactionConfig.uniqueModClassName.equalsIgnoreCase(""))
            {
                if(getNiceNames)
                    possibleBuiltInFactionList.add(exerelinFactionConfig.factionNiceName);
                else
                    possibleBuiltInFactionList.add(exerelinFactionConfig.factionId);
            }
        }

        return (String[])possibleBuiltInFactionList.toArray(new String[possibleBuiltInFactionList.size()]);
    }

    public boolean isFactionInstalled(String factionId, String factionSpecficClassName)
    {
	    try
	    {
		    Global.getSettings().getScriptClassLoader().loadClass(factionSpecficClassName);
		    System.out.println("Loaded " + factionId);
		    return true;
	    }
	    catch (ClassNotFoundException ex)
	    {
		    System.out.println("Skipped " + factionId);
		    return false;
	    }
    }

    public String getPlayerStartingShipVariant()
    {
        return this.playerStartingShipVariant;
    }

    public void setPlayerStartingShipVariant(String variant)
    {
        this.playerStartingShipVariant = variant;
    }
}
