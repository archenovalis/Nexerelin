package exerelin.campaign;

import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
public class DirectoryScreenScript implements EveryFrameScript
{
	//private static int count = 0;
	
	public DirectoryScreenScript()
	{
		//count++;
		//Global.getLogger(DirectoryScreenScript.class).info("Number of directory screen scripts running: " + count);
	}

	@Override
	public boolean isDone()
	{
		return false;
	}

	@Override
	public boolean runWhilePaused()
	{
		return true;
	}

	
	@Override
	public void advance(float amount)
	{
		// Don't do anything while in a menu/dialog		
		if (Global.getSector().isInNewGameAdvance() || Global.getSector().getCampaignUI().isShowingDialog() 
				|| Global.getCurrentState() == GameState.TITLE)
		{
			return;
		}
		
		if (Keyboard.isKeyDown(ExerelinConfig.directoryDialogKey))
		{
			Global.getSector().getCampaignUI().showInteractionDialog(new FactionDirectoryDialog(), Global.getSector().getPlayerFleet());
		}
	}

	private static class FactionDirectoryDialog implements InteractionDialogPlugin, CoreInteractionListener
	{
		private InteractionDialogAPI dialog;
		private TextPanelAPI text;
		private OptionPanelAPI options;

		protected RuleBasedInteractionDialogPluginImpl optionsDialogDelegate;

		private enum Menu
		{
			INIT,
			DIRECTORY,
			REMOTE_COMM,
			INTEL_SCREEN,
			EXIT
		}

		@Override
		public void init(InteractionDialogAPI dialog)
		{
			FleetInteractionDialogPluginImpl.inConversation = false;
			this.dialog = dialog;
			this.options = dialog.getOptionPanel();
			this.text = dialog.getTextPanel();

			//dialog.setTextWidth(Display.getWidth() * .9f);

			//dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
			initMenu();
		}

		void initMenu()
		{
			options.clearOptions();
			options.addOption(StringHelper.getString("exerelin_factions", "factionDirectoryOption"), Menu.DIRECTORY);
			//options.addOption(StringHelper.getString("exerelin_markets", "remoteCommDirectory"), Menu.REMOTE_COMM);
			//options.addOption(StringHelper.getString("exerelin_alliances", "allianceListOption"), Menu.ALLIANCES);
			//options.addOption(StringHelper.getString("exerelin_misc", "intelScreen"), Menu.INTEL_SCREEN);
			options.addOption(Misc.ucFirst(StringHelper.getString("close")), Menu.EXIT);
			options.setShortcut(Menu.EXIT, Keyboard.KEY_ESCAPE, false, false, false, true);
			dialog.setPromptText(StringHelper.getString("exerelin_misc", "directoryOptions") + ":");
		}

		// NOTE: we use FleetInteractionDialogPluginImpl.inConversation to tell whether we're currently delegating stuff to the RuleBasedInteractionDialogPlugin

		@Override
		public void optionSelected(String optionText, Object optionData)
		{
			if (optionData == null) return;
			if (FleetInteractionDialogPluginImpl.inConversation) {
				if (optionsDialogDelegate == null)
				{
					optionsDialogDelegate = new RuleBasedInteractionDialogPluginImpl();
					optionsDialogDelegate.setEmbeddedMode(true);
					optionsDialogDelegate.init(dialog);
				}

				optionsDialogDelegate.optionSelected(optionText, optionData);
				if (!FleetInteractionDialogPluginImpl.inConversation || 
						dialog.getInteractionTarget().getMemoryWithoutUpdate().getString("$option").equals("continueCutComm")) {
					FleetInteractionDialogPluginImpl.inConversation = false;
					optionSelected(null, Menu.INIT);
				}
				return;
			}
			else if (optionText != null) {
				text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
			}

			if (optionData == Menu.INIT)
			{
				initMenu();
			}
			if (optionData == Menu.DIRECTORY)
			{
				FleetInteractionDialogPluginImpl.inConversation = true;

				optionsDialogDelegate = new RuleBasedInteractionDialogPluginImpl();
				optionsDialogDelegate.setEmbeddedMode(true);
				optionsDialogDelegate.init(dialog);

				MemoryAPI mem = optionsDialogDelegate.getMemoryMap().get(MemKeys.LOCAL);
				mem.set("$specialDialog", true, 0);

				optionsDialogDelegate.fireAll("ExerelinFactionDirectory");
			}
			else if (optionData == Menu.REMOTE_COMM)
			{
				FleetInteractionDialogPluginImpl.inConversation = true;

				optionsDialogDelegate = new RuleBasedInteractionDialogPluginImpl();
				optionsDialogDelegate.setEmbeddedMode(true);
				optionsDialogDelegate.init(dialog);

				MemoryAPI mem = optionsDialogDelegate.getMemoryMap().get(MemKeys.LOCAL);
				mem.set("$specialDialog", true, 0);

				optionsDialogDelegate.fireAll("Nex_RemoteComm");
			}
			else if (optionData == Menu.INTEL_SCREEN)
			{
				dialog.getVisualPanel().showCore(CoreUITabId.INTEL, dialog.getInteractionTarget(), this);
			}
			else if (optionData == Menu.EXIT)
			{
				FleetInteractionDialogPluginImpl.inConversation = false;
				dialog.dismiss();
			}
		}

		@Override
		public void optionMousedOver(String optionText, Object optionData)
		{
		}

		@Override
		public void advance(float amount)
		{
		}

		@Override
		public void backFromEngagement(EngagementResultAPI battleResult)
		{
		}

		@Override
		public Object getContext()
		{
			return null;
		}

		@Override
		public Map<String, MemoryAPI> getMemoryMap()
		{
			return null;
		}

		@Override
		public void coreUIDismissed() {

		}
	}
}
