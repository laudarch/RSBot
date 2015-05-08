import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.filter.Filter;
import org.dreambot.api.methods.input.mouse.CrosshairState;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.randoms.RandomEvent;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.Entity;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.widgets.message.Message;

import javax.print.DocFlavor;
import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@ScriptManifest(author = "NVS", name = "NVSPestControl", version = 0.1, description = "Plays Pest Control", category = Category.MINIGAME)
public class NVSPestControl extends AbstractScript {

    // Per-game variables
    private Tile squireTile;
    private Tile knightTile;
    private HashMap <String, Tile> portals = new HashMap<>();
    private boolean wOpen, swOpen, seOpen, eOpen;

    // Per user variables
    private boolean fightMiddle; // Only fight in center
    private boolean useQuickPrayer;
    // Randomly selected
    private boolean attackPortals;
    private int damageThreshold; // At this damage, we attack portals even if attackPortals is disabled
    private int distance; // Used in distance calculations
    private int portalLiveHealth; // How much hp a portal needs to be considered alive
    private int walkingInterval; // When to click next tile when walking
    private int walkingAccuracy; // Higher is less accurate
    private String randomPortal;
    private int likelihood; // Chance of activating quick prayer at a given moment
    private int responsiveness; // Lower == more responsive

    // Gangplank crossing information
    private Tile joinTile, boatTile;
    private final Tile joinNovice = new Tile(2657, 2639);
    private final Tile boatNovice = new Tile(2661, 2639);
    private final Tile joinIntermediate = new Tile(2644, 2644);
    private final Tile boatIntermediate = new Tile(2640, 2644);
    private final Tile joinVeteran = new Tile(2638, 2653);
    private final Tile boatVeteran = new Tile(2634, 2653);

    private final Area outpostArea = new Area(new Tile(2686, 2686), new Tile(2626, 2626));

    private Thread idleChecker;
    private long lastActivity = 0; // Time when last player activity was recorded
    private int idleThreshold = 2400;
    private boolean playerIdle; // True if player was idle for more than "idleThreshold" milliseconds

    private String state = "Loading...";
    private long startTime;
    private int pointsStart = -1;
    private int pointsGained = 0;

    private enum State {
        GANGPLANK, WALKCENTER, WALKSOUTH, WALKPORTAL, WALKRANDOMPORTAL, FIGHT, SLEEP
    }

    public void onStart() {

        // Fill GUI
        GUI gui = new GUI();
        while (!gui.formComplete && getClient().getInstance().getScriptManager().isRunning()) {
            if (!gui.isVisible()) gui.setVisible(true);
            sleep(1000);
        }
        if (gui.boat == 1 && (isUserVIP() || isUserSponsor())) {
            log("Intermediate boat selected.");
            joinTile = joinIntermediate;
            boatTile = boatIntermediate;
        } else if (gui.boat == 2 && (isUserVIP() || isUserSponsor())) {
            log("Veteran boat selected.");
            joinTile = joinVeteran;
            boatTile = boatVeteran;
        } else {
            log("Novice boat selected.");
            joinTile = joinNovice;
            boatTile = boatNovice;
        }
        fightMiddle = gui.fightCenter;
        useQuickPrayer = gui.quickPrayer;

        // Seed data
        attackPortals = getLocalPlayer().getLevel() > 100*getClient().seededRandom();
        damageThreshold = (int)(100*getClient().seededRandom()) - getLocalPlayer().getLevel();
        distance = (int)(Calculations.random(6, 7)*getClient().seededRandom());
        portalLiveHealth = Calculations.random(getLocalPlayer().getLevel()/6);
        walkingInterval = (int)(8 * getClient().seededRandom());
        walkingAccuracy = (int)(4.5 * getClient().seededRandom());
        randomPortal = getClient().seededRandom() < 0.95 ? "w" : getClient().seededRandom() < 1 ? "sw" :
                getClient().seededRandom() < 1.05 ? "e" : "se";
        likelihood = (int)(700*getClient().seededRandom());
        responsiveness = 300 - getLocalPlayer().getLevel();

        idleChecker = new Thread() {
            public void run() {
                while (getClient().getInstance().getScriptManager().isRunning()) {
                    try {
                        if (getLocalPlayer().isMoving() || getLocalPlayer().getAnimation() != -1)
                            lastActivity = System.currentTimeMillis();
                        playerIdle = System.currentTimeMillis() - lastActivity > idleThreshold;
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        };
        idleChecker.start();

        startTime = System.currentTimeMillis();
        getSkillTracker().start();
    }

    public void onExit() {
        idleChecker.interrupt();
    }

    private State getState() {
        if (outpostArea.contains(getLocalPlayer())) { // We're in Void Outpost
            if (boatTile.distance(getLocalPlayer()) < 2)
                return State.SLEEP;
            return State.GANGPLANK;
        } else { // In game
            if (squireTile == null) { // New game
                return State.WALKSOUTH;
            } else {
                if (nearPortal()
                        || (!getLocalPlayer().isMoving() && nearMobs())
                        || getLocalPlayer().isInCombat()
                        || (fightMiddle && knightTile.distance(getLocalPlayer()) < distance))
                    return State.FIGHT;
                if (wOpen || swOpen || seOpen || eOpen) { // Portals have opened but we aren't close to one
                    return State.WALKPORTAL;
                } else { // No portals open
                    if (getLocalPlayer().getY() > squireTile.getY() || (fightMiddle && knightTile.distance(getLocalPlayer()) >= distance)) { // Walk to center
                        return State.WALKCENTER;
                    }
                    return State.WALKRANDOMPORTAL;
                }
            }
        }
    }

    @Override
    public int onLoop() {
        switch (getState()) {
            case GANGPLANK:
                reset();
                getRandomManager().registerSolver(getRandomManager().getBreakSolver()); // Enable breaks
                if (joinTile.distance(getLocalPlayer()) > 3) {
                    state = "Walking to gangplank";
                    getWalking().walk(joinTile);
                } else {
                    state = "Clicking gangplank";
                    GameObject gangplank = getGameObjects().closest("Gangplank");
                    if (gangplank != null && gangplank.isOnScreen()) {
                        gangplank.interact("Cross");
                        sleep(responsiveness*2, responsiveness*3);
                    }
                }
                break;
            case WALKSOUTH:
                state = "Starting game";
                getRandomManager().unregisterSolver(RandomEvent.BREAK); // Disable breaks
                NPC squire = getNpcs().closest(squireFilter);
                if (squire != null) {
                    squireTile = squire.getTile();
                    knightTile = new Tile(squireTile.getX() + 1, squireTile.getY() - 15);
                    portals.put("w", new Tile(knightTile.getX() - 25, knightTile.getY()));
                    portals.put("sw", new Tile(knightTile.getX() - 10, knightTile.getY() - 20));
                    portals.put("e", new Tile(knightTile.getX() + 23, knightTile.getY() - 3));
                    portals.put("se", new Tile(knightTile.getX() + 14, knightTile.getY() - 19));
                }
                activate();
                walk(knightTile.getRandomizedTile(3));
                break;
            case WALKPORTAL:
                if (playerIdle && getNpcs().closest("Brawler") != null) {
                    attack(getNpcs().closest("Brawler"));
                    sleep(3333, 5555);
                }
                String closest = getClosestPortal();
                state = "Walking to closest portal " + closest;
                if (closest == null) return Calculations.random(1337, 2222);
                Tile portal = portals.get(closest);
                if (portal.distance(getLocalPlayer()) >= distance || (getWalking().getDestination() == null || getWalking().getDestination().distance(portal) > 5)) {
                    walk(portal);
                    sleep((int) Calculations.nextGaussianRandom(responsiveness*3, responsiveness));
                }
                break;
            case WALKCENTER:
                state = "Walking to center";
                walk(knightTile.getRandomizedTile(3));
                break;
            case WALKRANDOMPORTAL:
                state = "Walking to random portal";
                if (playerIdle && getNpcs().closest("Brawler") != null) {
                    attack(getNpcs().closest("Brawler"));
                    sleep(3333, 5555);
                }
                if (getPortalHealth(randomPortal) > 0) {
                    if (portals.get(randomPortal).distance(getLocalPlayer()) >= distance || (getWalking().getDestination() == null || getWalking().getDestination().distance(portals.get(randomPortal)) > 5)) {
                        walk(portals.get(randomPortal));
                        sleep((int) Calculations.nextGaussianRandom(responsiveness*3, responsiveness));
                    }
                } else {
                    randomPortal = portals.keySet().toArray()[Calculations.random(0, 4)].toString();
                }

                break;
            case FIGHT:
                state = "Fighting";
                activate();
                if (getDialogues().canContinue()) getDialogues().clickContinue(); // Level ups
                if (getWidgets().getChildWidget(548, 77).getText().equals("0")) { // We're dead
                    state = "Waiting to respawn";
                    sleep(responsiveness, 3*responsiveness);
                    break;
                }
                if (getLocalPlayer().getInteractingCharacter() == null || playerIdle) {
                    if (getLocalPlayer().getInteractingCharacter() != null && getNpcs().closest("Brawler") != null && getNpcs().closest("Brawler").distance(getLocalPlayer()) < 3) { // We're interacting by not attacking
                        attack(getNpcs().closest("Brawler"));
                    } else {
                        if (attack(getNpcs().closest("Spinner"))) {
                        } else if ((attackPortals || enoughDamage(damageThreshold)) && attack(getNpcs().closest(portalFilter))) {
                        } else if (attack(getNpcs().closest("Shifter", "Brawler", "Defiler", "Ravager", "Torcher"))) {
                        } else {
                            state = "No targets found";
                            attack(getNpcs().closest(portalFilter));
                        }
                    }
                    sleep(responsiveness*2, responsiveness*3);
                } else { // In combat
                    state = "In combat";
                    activate();
                    if (getLocalPlayer().getInteractingCharacter().getName().equals("Portal")) {
                        if (getNpcs().closest("Spinner") != null & getNpcs().closest("Spinner").distance(getLocalPlayer()) < 2)
                            attack(getNpcs().closest("Spinner"));
                    }
                    antiban();
                    sleep(responsiveness, 3*responsiveness);
                }
                break;
            case SLEEP:
                state = "Waiting";
                if (getWidgets().getChildWidget(407, 14) != null) {
                    int points = Integer.valueOf(getWidgets().getChildWidget(407, 14).getText().replace("Pest Points: ", ""));
                    if (pointsStart == -1) {
                        pointsStart = points;
                    } else {
                        pointsGained = points - pointsStart;
                    }
                }
                antiban();
                sleep((int)Calculations.nextGaussianRandom(responsiveness*3, responsiveness));
                break;
        }
        return responsiveness;
    }

    /**
     * Attacks a target NPC
     * @param target the NPC to attack
     * @return true if the attack was successful, false otherwise
     */
    private boolean attack(NPC target) {
        if (target == null || !target.exists() || target.distance(getLocalPlayer()) > distance || target.getHealth() == 0) return false;
        if (target.isOnScreen()) {
            state = "Attacking " + target.getName();
            int tolerance = Calculations.random(5, 10);
            try {
                do {
                    target.interact("Attack");
                    sleep(responsiveness/4, responsiveness/2);
                    tolerance--;
                } while (tolerance > 0 && getMouse().getCrosshairState() != CrosshairState.INTERACTED && target.isOnScreen());
            } catch (ArrayIndexOutOfBoundsException ignore) {}
            return sleepUntilInteracting(target, 1500);
        } else {
            walk(target.getTile().getRandomizedTile(2));
            sleep(666, 1337);
            return false;
        }
    }

    private boolean enoughDamage(int damage) {
        return Integer.valueOf(getWidgets().getChildWidget(408, 11).getText()) >= damage;
    }


    /**
     * Finds the closest tile to the target that's in the game viewport.
     * @return the tile
     */
    private Tile closestOnScreen(Tile target) {
        double scale = 0.9;
        Tile testTile = target;
        while (getClient().getViewport().tileToScreen(testTile).getX() == -1 && scale > 0) {
            testTile = new Tile((int)(getLocalPlayer().getX() + scale*(target.getX() - getLocalPlayer().getX())), (int)(getLocalPlayer().getY() + scale*(target.getY() - getLocalPlayer().getY())));
            scale -= 0.2;
        }
        return testTile;
    }

    private Tile almostToTile(Tile target) {
        double scale = 0.8;
        return closestOnMM(new Tile((int)(getLocalPlayer().getX() + scale*(target.getX() - getLocalPlayer().getX())), (int)(getLocalPlayer().getY() + scale*(target.getY() - getLocalPlayer().getY()))));
    }

    private Tile closestOnMM(Tile target) {
        double scale = 0.9;
        Tile testTile = target;
        while (testTile.distance(getLocalPlayer()) > 15 && scale > 0) {
            testTile = new Tile((int)(getLocalPlayer().getX() + scale*(target.getX() - getLocalPlayer().getX())), (int)(getLocalPlayer().getY() + scale*(target.getY() - getLocalPlayer().getY())));
            scale -= Calculations.random(0.1, 0.2);
        }
        return testTile;
    }

    private boolean sleepUntilInteracting(NPC target, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout) {
            if (getLocalPlayer().getInteractingCharacter() != null)
                return true;
            sleep(200, 300);
        }
        return false;
    }

    /**
     * Sleeps until player is within distance of target
     * @param distance distance
     * @param timeout timeout in ms
     * @return true if done, false if timed-out
     */
    private boolean sleepUntilDistance(double distance, long timeout) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout) {
            if (getWalking().getDestinationDistance() <= distance)
                return true;
            sleep(200, 300);
        }
        return false;
    }

    private void walk(Tile target) {
        Tile next = closestOnMM(target);
        if (next.distance(getLocalPlayer()) < walkingAccuracy) {
            sleep(666, 1337);
            return;
        }

        int NE_x = Math.max(getLocalPlayer().getX(), next.getX());
        int NE_y = Math.max(getLocalPlayer().getY(), next.getY());
        int SW_x = Math.min(getLocalPlayer().getX(), next.getX());
        int SW_y = Math.min(getLocalPlayer().getY(), next.getY());
        Area doorCheck = new Area(new Tile(NE_x, NE_y), new Tile(SW_x, SW_y));
        List<GameObject> gates = getGameObjects().all(gateFilter);
        for (GameObject gate : gates) {
            if (doorCheck.contains(gate)) {
                openGate(gate, 6789);
                sleep(responsiveness, 2*responsiveness);
                break;
            }
        }

        if (next.distance(getLocalPlayer()) > 18) return;
        try {
            getWalking().walk(next);
        } catch (NullPointerException ignore) { return; }
        sleep((int) Calculations.nextGaussianRandom(666, 111));
        if (getCamera().getPitch() < 300 && Calculations.random(0, 50) < 25*getClient().seededRandom()) {
            getCamera().rotateToPitch(Calculations.random(380, 500));
        }
        if (getWalking().getDestination() != null && getWalking().getDestination().distance(target) < walkingAccuracy)
            sleepUntilDistance(walkingAccuracy, Calculations.random(4444, 6666));
        else
            sleepUntilDistance(walkingInterval, Calculations.random(4444, 11111));
    }

    /* Activate quick prayers and special */
    private void activate() {
        if (useQuickPrayer && !getWidgets().getChildWidget(548, 87).getText().equals("0")) {
            if (Calculations.random(0, 1000) < Calculations.random(0, likelihood))
                getPrayer().toggleQuickPrayer(true);
            likelihood = Math.max(Calculations.random(100, 200), likelihood - (int)(100*getClient().seededRandom()));
        }
    }

    /* Reset per-game variables */
    private void reset() {
        squireTile = null;
        knightTile = null;
        portals = new HashMap<>();
        wOpen = swOpen = seOpen = eOpen = false;
        likelihood = (int)(700*getClient().seededRandom());
    }

    /* Filter to find the Squire inside a PC game */
    private Filter<NPC> squireFilter = npc -> npc.getName().equals("Squire") && npc.hasAction("Leave");

    private Filter<NPC> portalFilter = npc -> npc.getName().equals("Portal") && npc.isInCombat();

    private Filter<GameObject> gateFilter = obj -> obj.getName().equals("Gate") && obj.hasAction("Open") && !obj.hasAction("Repair") && obj.getID() != 14245 && obj.getID() != 14247;

    private boolean openGate(GameObject gate, long timeout) {
        state = "Opening gate";
        long start = System.currentTimeMillis();
        Tile gateTile = gate.getTile();
        while (System.currentTimeMillis() - start < timeout && gate != null && gate.distance(gateTile) < 2 && gate.hasAction("Open")) {
            if (gate.isOnScreen() || gate.distance(getLocalPlayer()) < distance) {
                if (gate.interact("Open"))
                    sleep(responsiveness, responsiveness*2);
            } else {
                getWalking().walk(almostToTile(gate.getTile()));
                sleep(222, 666);
                sleepUntilDistance(1, 3000);
            }
            gate = getGameObjects().closest(gateFilter);
        }
        return false;
    }

    /* Returns the HP of a portal */
    private int getPortalHealth(String portal) {
        int parentWidget = 408;
        int base = 13;
        if (portal.toLowerCase().equals("w")) {
            return Integer.valueOf(getWidgets().getChildWidget(parentWidget, base).getText());
        } else if (portal.toLowerCase().equals("e")) {
            return Integer.valueOf(getWidgets().getChildWidget(parentWidget, base + 1).getText());
        } else if (portal.toLowerCase().equals("se")) {
            return Integer.valueOf(getWidgets().getChildWidget(parentWidget, base + 2).getText());
        } else if (portal.toLowerCase().equals("sw")) {
            return Integer.valueOf(getWidgets().getChildWidget(parentWidget, base + 3).getText());
        }
        return -1;
    }

    /* Finds the closest, open, alive portal. */
    private String getClosestPortal() {
        double[] distances = {1337, 1337, 1337, 1337};
        if (/*(wOpen || !attackPortals) && */getPortalHealth("w") > portalLiveHealth)
            distances[0] = portals.get("w").distance(getLocalPlayer());
        if (getPortalHealth("e") > portalLiveHealth)
            distances[1] = portals.get("e").distance(getLocalPlayer());
        if (getPortalHealth("se") > portalLiveHealth)
            distances[2] = portals.get("se").distance(getLocalPlayer());
        if (getPortalHealth("sw") > portalLiveHealth)
            distances[3] = portals.get("sw").distance(getLocalPlayer());

        // Get the min distance portal
        double minDistance = distances[0];
        int minIndex = 0;
        for (int i = 1; i < 4; i++) {
            if (distances[i] < minDistance) {
                minDistance = distances[i];
                minIndex = i;
            }
        }
        switch (minIndex) {
            case 0:
                return "w";
            case 1:
                return "e";
            case 2:
                return "se";
            case 3:
                return "sw";
        }
        return null;
    }

    private boolean nearPortal() {
        NPC portal = getNpcs().closest("Portal");
        return portal != null && portal.distance(getLocalPlayer()) <= distance;
    }

    private Filter<NPC> nearMobsFilter = npc -> {
        String[] mobs = {"Shifter", "Brawler", "Defiler", "Ravager", "Torcher", "Spinner"};
        return npc.getHealth() != 0 && npc.distance(getLocalPlayer()) < distance && npc.isOnScreen() && Arrays.asList(mobs).contains(npc.getName());
    };

    private boolean nearMobs() {
        try {
            return getNpcs().all(nearMobsFilter).size() >= Math.max(1, Calculations.nextGaussianRandom(2, 1));
        } catch (ArrayIndexOutOfBoundsException ignore) {
            return false;
        }
    }

    private Filter<Player> playerFilter = Entity::isOnScreen;

    private void antiban() {
        int random = (int)(Calculations.random(getSkills().getRealLevel(Skill.HITPOINTS) + getSkills().getRealLevel(Skill.ATTACK)
                + getSkills().getRealLevel(Skill.STRENGTH) + getSkills().getRealLevel(Skill.DEFENCE)
                + getSkills().getRealLevel(Skill.RANGED) + getSkills().getRealLevel(Skill.MAGIC) + responsiveness
                + Integer.valueOf(getWidgets().getChildWidget(548, 77).getText()) // current health
                + pointsGained) * (getLocalPlayer().getLevel()/10*getClient().seededRandom()));
        int a1 = Math.max(5, Calculations.random(getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        int a2 = a1 + Math.max(10, Calculations.random(getWalking().getRunEnergy() + getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        int a3 = a2 + Math.max(5, Calculations.random(getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        int a4 = a3 + Math.max(5, Calculations.random(getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        int a5 = a4 + Math.max(5, Calculations.random(getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        int a6 = a5 + Math.max(5, Calculations.random(getSkills().getRealLevel(Skill.values()[Calculations.random(22)])));
        if (random < a1) {
            state = "Antiban - mouse off screen";
            getMouse().moveMouseOutsideScreen();
        } else if (random < a2) {
            state = "Antiban - move randomly";
            getMouse().move(new Point(Calculations.random(760), Calculations.random(499)));
        } else if (random < a3 && getNpcs().closest(nearMobsFilter) != null) {
            state = "Antiban - move to npc";
            getMouse().move(getNpcs().closest(nearMobsFilter));
        } else if (random < a4 && getPlayers().all(playerFilter).size() > 0) {
            state = "Antiban - move to player";
            getMouse().click(getPlayers().all(playerFilter).get(Calculations.random(getPlayers().all(playerFilter).size())), true);
        } else if (random < a5) {
            state = "Antiban - move camera";
            getCamera().rotateTo(Calculations.random(2000), Calculations.random(400));
        } else if (random < a6) {
            state = "Antiban - open tab";
            getTabs().openWithMouse(Tab.values()[Calculations.random(Tab.values().length)]);
        }
    }

    @Override
    public void onMessage(Message m) {
        String message = m.getMessage();
        if (!outpostArea.contains(getLocalPlayer())) {
            if (message.contains("The purple, western portal shield has dropped!")) {
                wOpen = true;
            } else if (message.contains("The red, south-western portal shield has dropped!")) {
                swOpen = true;
            } else if (message.contains("The yellow, south-eastern portal shield has dropped!")) {
                seOpen = true;
            } else if (message.contains("The blue, eastern portal shield has dropped!")) {
                eOpen = true;
            } else if (message.contains("Oh dear, you are dead!")) {
//                if (getNpcs().closest(squireFilter) != null) squireTile = null;
            } else if (message.contains("I can't reach that!")) {
                getWalking().walk(getLocalPlayer().getTile().getRandomizedTile(10));
                sleep(1337, 3333);
            }
        }
    }

    private static final Font font1 = new Font("Arial", 1, 12);

    @Override
    public void onPaint(Graphics2D g) {
        long runtime = (System.currentTimeMillis() - startTime)/1000;
        long xpGained = getSkillTracker().getGainedExperience(Skill.ATTACK) + getSkillTracker().getGainedExperience(Skill.STRENGTH) + getSkillTracker().getGainedExperience(Skill.DEFENCE) + getSkillTracker().getGainedExperience(Skill.RANGED) + getSkillTracker().getGainedExperience(Skill.MAGIC) + getSkillTracker().getGainedExperience(Skill.HITPOINTS);
        g.setColor(Color.white);

        g.setFont(font1);
        g.drawString("State: " + state, 8, 287);
        g.drawString("XP Gained (Hr): " + xpGained + " (" + (int)(xpGained*(3600/(double)runtime)) + ")", 8, 302);
        g.drawString("Points Gained (Hr): " + pointsGained + " (" + (int)(pointsGained*(3600/(double)runtime)) + ")", 8, 317);
        g.drawString("Time running: " + String.format("%02d:%02d:%02d", runtime / 3600, (runtime % 3600) / 60, runtime % 60), 8, 332);
    }
}