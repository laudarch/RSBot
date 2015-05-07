import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.filter.Filter;
import org.dreambot.api.methods.input.mouse.CrosshairState;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.widgets.message.Message;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@ScriptManifest(author = "NVS", name = "NVSPestControl", version = 0.1, description = "Plays Pest Control", category = Category.MINIGAME)
public class NVSPestControl extends AbstractScript {

    // Gangplank crossing information
    private Tile joinTile, boatTile;
    private final Tile joinNovice = new Tile(2657, 2639);
    private final Tile boatNovice = new Tile(2661, 2639);
    private final Tile joinIntermediate = new Tile(2644, 2644);
    private final Tile boatIntermediate = new Tile(2640, 2644);
    private final Tile joinVeteran = new Tile(2638, 2653);
    private final Tile boatVeteran = new Tile(2634, 2653);

    private final Area outpostArea = new Area(new Tile(2686, 2686), new Tile(2626, 2626));

    private boolean attackPortals = false;
    private int damageThreshold = 75; // At this damage, we attack portals even if attackPortals is disabled
    private boolean fightMiddle = false;
    private int distance = 7;
    private int portalLiveHealth = 1; // How much hp a portal needs to be considered alive

    // Per-game variables
    private Tile squireTile;
    private Tile knightTile;
    private HashMap <String, Tile> portals = new HashMap<>();
    private boolean wOpen, swOpen, seOpen, eOpen;
    private String randomPortal;
    private int likelyhood = 65;

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
        getMouse().getMouseSettings().setUseMiddleMouseInInteracts(false);
        getMouse().getMouseSettings().setSpeed(100, 999999);
        getClient().disableIdleMouse();

        joinTile = joinNovice;
        boatTile = boatNovice;

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
//        new Thread(new Antiban()).start();

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
                if (nearPortal() || (!getLocalPlayer().isMoving() && nearMobs()) || getLocalPlayer().isInCombat()) // We're near a live portal or mobs
                    return State.FIGHT;
                if (wOpen || swOpen || seOpen || eOpen) { // Portals have opened but we aren't close to one
                    return State.WALKPORTAL;
                } else { // No portals open
                    if (getLocalPlayer().getY() > squireTile.getY() || (fightMiddle && knightTile.distance(getLocalPlayer()) > 5)) { // Walk to center
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
                if (joinTile.distance(getLocalPlayer()) > 3) {
                    state = "Walking to gangplank";
                    walk(joinTile, Calculations.nextGaussianRandom(6, 1), 2, 30000);
                } else {
                    state = "Clicking gangplank";
                    GameObject gangplank = getGameObjects().closest("Gangplank");
                    if (gangplank != null && gangplank.isOnScreen()) {
                        gangplank.interact();
                        sleep(777, 1337);
                    }
                }
                break;
            case WALKSOUTH:
                state = "Starting game";
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
                randomPortal = portals.keySet().toArray()[Calculations.random(0, 4)].toString();
                walk(knightTile.getRandomizedTile(3), Calculations.nextGaussianRandom(6, 1), 2, 30000);
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
                    walk(portal, Calculations.nextGaussianRandom(6, 1), 3, 30000);
                    sleep((int) Calculations.nextGaussianRandom(1000, 333));
                }
                break;
            case WALKCENTER:
                state = "Walking to center";
                walk(knightTile.getRandomizedTile(3), Calculations.nextGaussianRandom(6, 1), 2, 30000);
                break;
            case WALKRANDOMPORTAL:
                state = "Walking to random portal";
                if (playerIdle && getNpcs().closest("Brawler") != null) {
                    attack(getNpcs().closest("Brawler"));
                    sleep(3333, 5555);
                }
                if (getPortalHealth(randomPortal) > 0) {
                    if (portals.get(randomPortal).distance(getLocalPlayer()) >= distance || (getWalking().getDestination() == null || getWalking().getDestination().distance(portals.get(randomPortal)) > 5)) {
                        walk(portals.get(randomPortal), Calculations.nextGaussianRandom(6, 1), 3, 30000);
                        sleep((int) Calculations.nextGaussianRandom(1000, 333));
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
                    sleep(1337, 2222);
                    break;
                }
                if (getLocalPlayer().getInteractingCharacter() == null || playerIdle) {
                    if (getLocalPlayer().getInteractingCharacter() != null) { // We're interacting by not attacking
                        if (!attack(getNpcs().closest("Brawler")))
                            attack(getNpcs().closest("Shifter", "Brawler", "Defiler", "Ravager", "Torcher"));
                    } else {
                        if (attack(getNpcs().closest("Spinner"))) {
                        } else if ((attackPortals || enoughDamage(damageThreshold)) && attack(getNpcs().closest(portalFilter))) {
                        } else if (attack(getNpcs().closest("Shifter", "Brawler", "Defiler", "Ravager", "Torcher"))) {
                        } else {
                            state = "No targets found";
                            attack(getNpcs().closest(portalFilter));
                        }
                    }
                    sleep(666, 1337);
                } else { // In combat
                    state = "In combat";
                    activate();
                    sleep(666, 1337);
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
                sleep((int)Calculations.nextGaussianRandom(1000, 333));
                break;
        }
        return 200;
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
            do {
                if (getClient().getViewport().isOnGameScreen(target.getCenterPoint())) {
                    getMouse().move(target.getCenterPoint());
                    sleep(10, 15);
                }
                if (getMouse().getEntitiesOnCursor().size() > 0 && getMouse().getEntitiesOnCursor().contains(target)) {
//                    if (getMouse().getEntitiesOnCursor().get(0).getName().equals(target.getName()))
//                        getMouse().click();
//                    else
                        target.interact("Attack");
                    sleep(30, 50);
                }
                tolerance--;
            } while (tolerance > 0 && getMouse().getCrosshairState() != CrosshairState.INTERACTED && target.isOnScreen());
            return sleepUntilInteracting(target, 1500);
        } else {
            walk(target.getTile().getRandomizedTile(2), Calculations.nextGaussianRandom(6, 1), 2, 30000);
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

    private void walk(Tile target, double nextClick, int accuracy, long timeout) {
        Tile next = closestOnMM(target);
        if (next.distance(getLocalPlayer()) < 2*accuracy) {
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
                log("Found a gate");
                openGate(gate, 6789);
                sleepUntilDistance(1, 1000);
                sleep(444, 888);
                break;
            }
        }

        if (next == null || next.distance(getLocalPlayer()) > 18) return;
        getWalking().walk(next);
        sleep((int) Calculations.nextGaussianRandom(666, 111));
        if (getCamera().getPitch() < 300 && Calculations.random(0, 50) < 40) {
            getCamera().rotateToPitch(Calculations.random(380, 500));
        }
        if (getWalking().getDestination() != null && getWalking().getDestination().distance(target) < 2*accuracy)
            sleepUntilDistance(2*accuracy, Calculations.random(4444, 6666));
        else
            sleepUntilDistance(nextClick, Calculations.random(4444, 11111));
    }

    /* Activate quick prayers and special */
    private void activate() {
        if (!getWidgets().getChildWidget(548, 87).getText().equals("0")) {
            if (Calculations.random(0, 100) < Calculations.random(0, likelyhood))
                getPrayer().toggleQuickPrayer(true);
            likelyhood = Math.max(Calculations.random(10, 20), likelyhood - 10);
        }
    }

    /* Reset per-game variables */
    private void reset() {
        squireTile = null;
        knightTile = null;
        portals = new HashMap<>();
        wOpen = swOpen = seOpen = eOpen = false;
        likelyhood = 70;
    }

    /* Filter to find the Squire inside a PC game */
    private Filter<NPC> squireFilter = npc -> npc.getName().equals("Squire") && npc.hasAction("Leave");

    private Filter<NPC> portalFilter = npc -> npc.getName().equals("Portal") && npc.isInCombat();

    private Filter<GameObject> gateFilter = obj -> obj.getName().equals("Gate") && obj.hasAction("Open") && obj.getID() != 14245 && obj.getID() != 14247;

    private boolean openGate(GameObject gate, long timeout) {
        state = "Opening gate";
        long start = System.currentTimeMillis();
        Tile gateTile = gate.getTile();
        while (System.currentTimeMillis() - start < timeout && gate != null && gate.distance(gateTile) < 2 && gate.hasAction("Open")) {
            if (gate.isOnScreen()) {
                getMouse().move(gate.getCenterPoint());
                sleep(20, 50);
                gate.interact("Open");
                sleep(300, 456);
            } else {
                getMouse().click(getClient().getViewport().tileToScreen(closestOnScreen(gate.getTile())));
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
        return portal != null && portal.distance(getLocalPlayer()) < distance;
    }

    private Filter<NPC> nearMobsFilter = npc -> {
        String[] mobs = {"Shifter", "Brawler", "Defiler", "Ravager", "Torcher", "Spinner"};
        return npc.getHealth() != 0 && npc.distance(getLocalPlayer()) < distance && npc.isOnScreen() && Arrays.asList(mobs).contains(npc.getName());
    };

    private boolean nearMobs() {
        return getNpcs().all(nearMobsFilter).size() >= Math.max(1, Calculations.nextGaussianRandom(2, 1));
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
        g.drawString("State: " + state, 200, 370);
        g.drawString("XP Gained (Hr): " + xpGained + " (" + (int)(xpGained*(3600/(double)runtime)) + ")", 200, 385);
        g.drawString("Points Gained (Hr): " + pointsGained + " (" + (int)(pointsGained*(3600/(double)runtime)) + ")", 200, 400);
        g.drawString("Time running: " + String.format("%02d:%02d:%02d", runtime / 3600, (runtime % 3600) / 60, runtime % 60), 200, 415);
    }
}