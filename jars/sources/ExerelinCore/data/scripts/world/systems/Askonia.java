package data.scripts.world.systems;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.fleet.FleetMemberType;

public class Askonia {

	public void generate(SectorAPI sector) {
		
		StarSystemAPI system = sector.createStarSystem("Askonia");
		LocationAPI hyper = Global.getSector().getHyperspace();
		
		system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");
		
		// create the star and generate the hyperspace anchor for this system
		PlanetAPI star = system.initStar("askonia", // unique id for this star 
										 "star_red", // id in planets.json
										 1000f); 		// radius (in pixels at default zoom)
		
		system.setLightColor(new Color(255, 210, 200)); // light color in entire system, affects all entities
		
		/*
		 * addPlanet() parameters:
		 * 1. Unique id for this planet (or null to have it be autogenerated)
		 * 2. What the planet orbits (orbit is always circular)
		 * 3. Name
		 * 4. Planet type id in planets.json
		 * 5. Starting angle in orbit, i.e. 0 = to the right of the star
		 * 6. Planet radius, pixels at default zoom
		 * 7. Orbit radius, pixels at default zoom
		 * 8. Days it takes to complete an orbit. 1 day = 10 seconds.
		 */
		PlanetAPI a1 = system.addPlanet("sindria", star, "Sindria", "rocky_metallic", 0, 150, 2900, 100);
		
		a1.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "sindria"));
		a1.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "sindria"));
		a1.getSpec().setGlowColor(new Color(255,255,255,255));
		a1.getSpec().setUseReverseLightForGlow(true);
		a1.applySpecChanges();
		a1.setCustomDescriptionId("planet_sindria");
		
		SectorEntityToken relay = system.addCustomEntity("sindria_relay", // unique id
				 "Sindria Relay", // name - if null, defaultName from custom_entities.json will be used
				 "comm_relay", // type of object, defined in custom_entities.json
				 "sindrian_diktat"); // faction
		// synced orbit w/ Sindria
		relay.setCircularOrbit( system.getEntityById("askonia"), 150, 3200, 100);
		
		PlanetAPI a2 = system.addPlanet("salus", star, "Salus", "gas_giant", 230, 350, 7000, 250);
		
		PlanetAPI a2a = system.addPlanet("cruor", a2, "Cruor", "rocky_unstable", 45, 80, 700, 25);
			a2a.setInteractionImage("illustrations", "desert_moons_ruins");
		PlanetAPI a2b = system.addPlanet("volturn", a2, "Volturn", "water", 110, 120, 1400, 45);
	
		PlanetAPI a3 = system.addPlanet("umbra", star, "Umbra", "rocky_ice", 280, 150, 12000, 650);
		
		
		a2.setCustomDescriptionId("planet_salus");
		a2.getSpec().setPlanetColor(new Color(255,215,190,255));
		a2.getSpec().setAtmosphereColor(new Color(160,110,45,140));
		a2.getSpec().setCloudColor(new Color(255,164,96,200));
		a2.getSpec().setTilt(15);
		a2.applySpecChanges();
		
		a2a.setCustomDescriptionId("planet_cruor");
		a2a.setInteractionImage("illustrations", "desert_moons_ruins");
		
		a2b.setCustomDescriptionId("planet_volturn");
		a2b.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
		a2b.getSpec().setGlowColor(new Color(255,255,255,255));
		a2b.getSpec().setUseReverseLightForGlow(true);
		a2b.applySpecChanges();
		a2b.setInteractionImage("illustrations", "space_bar");
		
		a3.setCustomDescriptionId("planet_umbra");
		a3.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "asharu"));
		a3.getSpec().setGlowColor(new Color(255,255,255,255));
		a3.getSpec().setUseReverseLightForGlow(true);
		a3.applySpecChanges();
		a3.setInteractionImage("illustrations", "pirate_station");
		
//		system.addOrbitalJunk(a1,
//				 "orbital_junk", // from custom_entities.json 
//				 30, // num of junk
//				 12, 20, // min/max sprite size (assumes square)
//				 225, // orbit radius
//				 70, // orbit width
//				 10, // min orbit days
//				 20, // max orbit days
//				 60f, // min spin (degress/day)
//				 360f); // max spin (degrees/day)
		
		/*
		 * addAsteroidBelt() parameters:
		 * 1. What the belt orbits
		 * 2. Number of asteroids
		 * 3. Orbit radius
		 * 4. Belt width
		 * 6/7. Range of days to complete one orbit. Value picked randomly for each asteroid. 
		 */
		system.addAsteroidBelt(a2, 50, 1100, 128, 40, 80);
		
		
		/*
		 * addRingBand() parameters:
		 * 1. What it orbits
		 * 2. Category under "graphics" in settings.json
		 * 3. Key in category
		 * 4. Width of band within the texture
		 * 5. Index of band
		 * 6. Color to apply to band
		 * 7. Width of band (in the game)
		 * 8. Orbit radius (of the middle of the band)
		 * 9. Orbital period, in days
		 */
		system.addRingBand(a2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 40f);
		system.addRingBand(a2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 60f);
		system.addRingBand(a2, "misc", "rings1", 256f, 2, Color.white, 256f, 1100, 80f);
		
//		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 1700, 50f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 1700, 70f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 1, Color.white, 256f, 1700, 90f);
//		system.addRingBand(a2, "misc", "rings1", 256f, 1, Color.white, 256f, 1700, 110f);
		
		system.addRingBand(a2, "misc", "rings1", 256f, 3, Color.white, 256f, 1800, 70f);
		system.addRingBand(a2, "misc", "rings1", 256f, 3, Color.white, 256f, 1800, 90f);
		system.addRingBand(a2, "misc", "rings1", 256f, 3, Color.white, 256f, 1800, 110f);
		
		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 2150, 50f);
		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 2150, 70f);
		system.addRingBand(a2, "misc", "rings1", 256f, 0, Color.white, 256f, 2150, 80f);
		system.addRingBand(a2, "misc", "rings1", 256f, 1, Color.white, 256f, 2100, 90f);
		
		
		
		JumpPointAPI jumpPoint = Global.getFactory().createJumpPoint("askonia_jump_point_alpha", "Sindria Jump Point");
		OrbitAPI orbit = Global.getFactory().createCircularOrbit(a1, 0, 500, 30);
		jumpPoint.setOrbit(orbit);
		jumpPoint.setRelatedPlanet(a1);
		jumpPoint.setStandardWormholeToHyperspaceVisual();
		system.addEntity(jumpPoint);
		
		//SectorEntityToken station = system.addOrbitalStation("diktat_cnc", a1, 45, 300, 50, "Command & Control", "sindrian_diktat");
		//initStationCargo(station);
		
		SectorEntityToken station = system.addCustomEntity("diktat_cnc", "Command & Control", "station_side02", "sindrian_diktat");
		station.setCircularOrbitPointingDown(system.getEntityById("sindria"), 45, 300, 50);		
//		station.setCustomDescriptionId("station_ragnar");
		
		// example of using custom visuals below
//		a1.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "hull_breach", 800, 800));
//		jumpPoint.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "space_wreckage", 1200, 1200));
//		station.setCustomInteractionDialogImageVisual(new InteractionDialogImageVisual("illustrations", "cargo_loading", 1200, 1200));
		
		// generates hyperspace destinations for in-system jump points
		system.autogenerateHyperspaceJumpPoints(true, true);
		
		
		/*
		DiktatPatrolSpawnPoint patrolSpawn = new DiktatPatrolSpawnPoint(sector, system, 5, 3, a1);
		system.addScript(patrolSpawn);
		for (int i = 0; i < 5; i++)
			patrolSpawn.spawnFleet();

		DiktatGarrisonSpawnPoint garrisonSpawn = new DiktatGarrisonSpawnPoint(sector, system, 30, 1, a1, a1);
		system.addScript(garrisonSpawn);
		garrisonSpawn.spawnFleet();
		
		
		system.addScript(new IndependentTraderSpawnPoint(sector, hyper, 1, 10, hyper.createToken(-6000, 2000), station));
		*/
	}
	
	
	private void initStationCargo(SectorEntityToken station) {
		CargoAPI cargo = station.getCargo();
		addRandomWeapons(cargo, 5);
		
		cargo.addCrew(CrewXPLevel.VETERAN, 20);
		cargo.addCrew(CrewXPLevel.REGULAR, 500);
		cargo.addMarines(200);
		cargo.addSupplies(1000);
		cargo.addFuel(500);
		
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "conquest_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "heron_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "heron_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "shepherd_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "shepherd_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "crig_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "ox_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "monitor_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, "monitor_Hull"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, "gladius_wing"));
		cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, "gladius_wing"));
	}
	
	private void addRandomWeapons(CargoAPI cargo, int count) {
		List weaponIds = Global.getSector().getAllWeaponIds();
		for (int i = 0; i < count; i++) {
			String weaponId = (String) weaponIds.get((int) (weaponIds.size() * Math.random()));
			int quantity = (int)(Math.random() * 4f + 2f);
			cargo.addWeapons(weaponId, quantity);
		}
	}
	
}
