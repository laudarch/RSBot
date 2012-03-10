
import com.rarebot.event.listeners.PaintListener;
import com.rarebot.script.Script;
import com.rarebot.script.ScriptManifest;
import com.rarebot.script.methods.Skills;
import com.rarebot.script.util.Filter;
import com.rarebot.script.wrappers.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import javax.imageio.ImageIO;
import javax.swing.*;

@ScriptManifest(authors = {"Chad [Xeroday]"}, version = 1.0, keywords = ("combat, armoured, armored, zombies"), description = "Kills Armoured Zombies.", name = "XDZombies", website = "http://node13.info/xeroday/pages/xdzombies.php")
public class XDZombies extends Script implements PaintListener {

    public final static String URL = XDZombies.class.getAnnotation(ScriptManifest.class).website().toString();
    private String state = "";
    private RSPlayer me;
    private RSTile location;
    private State currentState;
    private final RenderingHints antialiasing = new RenderingHints(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    private Image getImage(String url) {
        try {
            return ImageIO.read(new URL(url));
        } catch (IOException e) {
            return null;
        }
    }
    private final Image cursor1 = getImage("http://node13.info/xeroday/stats/XDZombies/img.ws?entity=HA3Hx");
    private final Image cursor2 = getImage("http://node13.info/xeroday/stats/XDZombies/img.ws?entity=s5Ep8");
    private final Color color1 = new Color(0, 204, 204);
    private final Font font1 = new Font("Trebuchet MS", 0, 25);
    private final Image img1 = getImage("http://node13.info/xeroday/stats/XDZombies/img.ws?entity=h62Pc");
    private final Image img2 = getImage("http://node13.info/xeroday/stats/XDZombies/img.ws?entity=Gsj2w");
    private long startTime = 0;
    private long hours = 0;
    private long minutes = 0;
    private long seconds = 0;
    private long runTime = 0;
    private int currentXP;
    private int startXP;
    private int gainedXP;
    private int xpPerHour;
    private boolean need2bank = false;
    private final static int doorID = 15536;
    private RSObject door;
    private final static RSTile doorTile = new RSTile(3203, 3494);
    private final static RSTile bankTile = new RSTile(3189, 3436);
    private final static RSTile[] toStairs = {new RSTile(3189, 3436), new RSTile(3186, 3433), new RSTile(3190, 3429), new RSTile(3196, 3429), new RSTile(3202, 3429), new RSTile(3208, 3431), new RSTile(3212, 3437), new RSTile(3212, 3444), new RSTile(3212, 3450), new RSTile(3212, 3456), new RSTile(3212, 3462), new RSTile(3212, 3469), new RSTile(3209, 3475), new RSTile(3206, 3482), new RSTile(3206, 3487), new RSTile(3204, 3492), new RSTile(3203, 3493)};
    private RSTilePath BankToStairs;
    private final static RSTile[] toBank = {new RSTile(3210, 3425), new RSTile(3205, 3428), new RSTile(3198, 3428), new RSTile(3193, 3428), new RSTile(3187, 3430), new RSTile(3186, 3434), new RSTile(3189, 3435)};
    private RSTilePath BankPath;
    private RSArea stairsArea = new RSArea(3200, 3495, 3206, 3500);
    private final static RSTile stairsTile = new RSTile(3203, 3498);
    private final static int hartwinID = 13485;
    private RSNPC hartwin;
    private final static String zombie = "Armoured zombie";
    private final static int altarID = 65371;
    private RSObject altar;
    private static RSObject entrance;
    private RSTile trapdoor = new RSTile(99999, 99999);
    private final static int ladderID = 39191;
    private RSObject ladder;
    private RSArea zombiesArea = new RSArea(3239, 9989, 3245, 10010);
    private static final int sharkID = 385;
    private static final int pouchID = 12029;
    private RSNPC interact;
    public int[] atkpot;
    public int[] strpot;
    public boolean pset = false;
    public int qtypots = 0;
    public int qtysharks;
    public boolean uses = false;
    public int qtybunyips;
    public int qtyspots;
    private int[] loot = new int[100];
    public boolean getloot = false;
    public static final int[] combatpotion = {9745, 9743, 9741, 9739};
    public static final int[] atksuperpotion = {149, 147, 145, 2436};
    public static final int[] strsuperpotion = {161, 159, 157, 2440};
    public static final int[] atkextremepotion = {15311, 15310, 15309, 15308};
    public static final int[] strextremepotion = {15315, 15314, 15313, 15312};
    public static final int[] overloadpotion = {15335, 15334, 15333, 15332};
    private static final int sumID[] = {12146, 12144, 12142, 12140};
    public int mousespeed = 6;
    public int antibanfreq = -5;
    private Filter<RSNPC> Filter = new Filter<RSNPC>() {

        @Override
        public boolean accept(RSNPC npc) {
            if (npc != null && (npc.getName().contains(zombie)) && !npc.isInCombat() && npc.getInteracting() == null && npc.getHPPercent() > 0 && players.getMyPlayer().getAnimation() < 0 && zombiesArea.contains(npc.getLocation())) {
                return true;
            } else {
                return false;
            }
        }
    };
    private Filter<RSNPC> interacting = new Filter<RSNPC>() {

        @Override
        public boolean accept(RSNPC npc) {
            if (npc != null && npc.getName().contains(zombie)) {
                if (npc.getInteracting() != null) {
                    if (npc.getInteracting().equals(me)) {
                        return true;
                    }
                }
                return false;
            } else {
                return false;


            }
        }
    };

    private static enum State {

        SLEEP, FIGHT, RECHARGE, GOTO, BANK, GOBANK
    }

    @Override
    public boolean onStart() {
        try {
            URL url = new URL("http://www.node13.info/xeroday/stats/XDZombies/loot.ws");
            URLConnection yc = url.openConnection();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()))) {
                String inputLine;
                int i = 0;
                while ((inputLine = in.readLine()) != null) {
                    loot[i] = Integer.parseInt(inputLine);
                    i++;
                }
            }
        } catch (IOException | NumberFormatException e) {
            return false;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    XDZombiesGUI gui = new XDZombiesGUI();
                    gui.setVisible(true);
                }
            });
        } catch (Throwable ignore) {
        }
        while (antibanfreq < 0) {
            sleep(250);
        }
        BankToStairs = walking.newTilePath(toStairs);
        BankPath = walking.newTilePath(toBank);
        startXP = skills.getCurrentExp(Skills.ATTACK) + skills.getCurrentExp(Skills.CONSTITUTION) + skills.getCurrentExp(Skills.DEFENSE) + skills.getCurrentExp(Skills.STRENGTH);
        mouse.setSpeed(mousespeed);
        if (!inventory.containsOneOf(atkpot)) {
            need2bank = true;
        }
        if (!inventory.containsOneOf(strpot)) {
            need2bank = true;
        }
        startTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public int loop() {
        me = players.getMyPlayer();
        entrance = objects.getNearest("Trapdoor");
        if (entrance != null) {
            trapdoor = entrance.getLocation();
        }
        currentState = getState();
        switch (currentState) {
            case FIGHT:
                if (zombiesArea.contains(location)) {
                    interact = npcs.getNearest(interacting);
                    if (interact != null) {
                        check();
                        if (!combat.isAttacking(interact)) {
                            if (interact.isOnScreen()) {
                                state = "Attacking interacting";
                                AttackonPoint(interact.getPoint());
                                sleep(3000);
                                break;
                            }
                        }
                        sleep(1337);
                        break;
                    } else {
                        check();
                        final RSNPC free = npcs.getNearest(Filter);
                        if (free != null) {
                            if (free.isOnScreen()) {
                                if (!combat.isAttacking(free) && !free.isInteractingWithLocalPlayer()) {
                                    state = "Attacking free";
                                    AttackonPoint(free.getPoint());
                                }
                                break;
                            } else {
                                if (!me.isInCombat() && me.getAnimation() < 0) {
                                    walking.walkTo(free.getLocation());
                                    sleep(600, 1000);
                                }
                                break;
                            }
                        }
                    }
                    break;
                } else if (calc.distanceTo(trapdoor) < 25) {
                    if (calc.tileOnScreen(trapdoor)) {
                        if (entrance.interact("Open")) {
                            sleep(1000, 2000);
                            check();
                        }
                        break;
                    } else {
                        walking.walkTileMM(trapdoor);
                    }
                }
                break;
            case RECHARGE:
                if (zombiesArea.contains(location)) {
                    ladder = objects.getNearest(ladderID);
                    if (ladder != null) {
                        if (ladder.isOnScreen()) {
                            if (ladder.interact("Climb-up")) {
                                sleep(2777, 3522);
                                break;
                            }
                            break;
                        } else {
                            walking.walkTileMM(ladder.getLocation());
                            sleep(888);
                            break;
                        }
                    }
                } else if (calc.distanceTo(trapdoor) < 25) {
                    altar = objects.getNearest(altarID);
                    if (altar != null) {
                        if (altar.isOnScreen()) {
                            if (altar.interact("Pray")) {
                                sleep(300);
                                break;
                            }
                            break;
                        } else {
                            walking.walkTileMM(altar.getLocation());
                            sleep(888, 1337);
                            break;
                        }
                    } else {
                        sleep(1337, 3333);
                        break;
                    }
                }
                break;
            case GOTO:
                if (stairsArea.contains(location)) {
                    state = "In tower";
                    hartwin = npcs.getNearest(hartwinID);
                    if (hartwin == null) {
                        if (calc.tileOnScreen(stairsTile)) {
                            long time = System.currentTimeMillis();
                            while ((System.currentTimeMillis() - time) < 15000 && hartwin == null) {
                                final RSObject stairs = objects.getTopAt(stairsTile);
                                if (stairs != null) {
                                    state = "Climbing stairs";
                                    stairs.interact("Climb-up");
                                    sleep(1337, 2333);
                                    hartwin = npcs.getNearest(hartwinID);
                                }
                            }
                            break;
                        } else {
                            walking.walkTo(stairsTile);
                            sleep(1337);
                            break;
                        }
                    } else {
                        if (hartwin != null) {
                            if (hartwin.isOnScreen()) {
                                state = "Talking to Hartwin";
                                if (hartwin.interact("Talk-to Hart")) {
                                    long time = System.currentTimeMillis();
                                    while ((System.currentTimeMillis() - time) < 10000) {
                                        if (interfaces.getComponent(1191, 20).isValid()) {
                                            interfaces.getComponent(1191, 20).doClick();
                                            sleep(777, 1337);
                                        }
                                        if (interfaces.getComponent(1184, 20).isValid()) {
                                            interfaces.getComponent(1184, 20).doClick();
                                            sleep(777, 1337);
                                        }
                                        if (interfaces.getComponent(1184, 20).isValid()) {
                                            interfaces.getComponent(1184, 20).doClick();
                                            sleep(777, 1337);
                                        }
                                        if (interfaces.getComponent(1188, 3).isValid()) {
                                            interfaces.getComponent(1188, 3).doClick();
                                            sleep(5000);
                                            break;
                                        }
                                    }
                                }
                                break;
                            } else {
                                walking.walkTileMM(hartwin.getLocation());
                                break;
                            }
                        }
                    }
                } else {
                    state = "Walking to zombies";
                    hartwin = npcs.getNearest(hartwinID);
                    if (hartwin != null) {
                        if (hartwin.isOnScreen()) {
                            state = "Talking to Hartwin";
                            if (hartwin.interact("Talk-to Hart")) {
                                long time = System.currentTimeMillis();
                                while ((System.currentTimeMillis() - time) < 10000) {
                                    if (interfaces.getComponent(1191, 20).isValid()) {
                                        interfaces.getComponent(1191, 20).doClick();
                                        sleep(777, 1337);
                                    }
                                    if (interfaces.getComponent(1184, 20).isValid()) {
                                        interfaces.getComponent(1184, 20).doClick();
                                        sleep(777, 1337);
                                    }
                                    if (interfaces.getComponent(1188, 3).isValid()) {
                                        interfaces.getComponent(1188, 3).doClick();
                                        sleep(5000);
                                        break;
                                    }
                                }
                            }
                            break;
                        } else {
                            walking.walkTileMM(hartwin.getLocation());
                            break;
                        }
                    }
                    long time = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - time) < 30000 && calc.distanceTo(stairsTile) > 2) {
                        BankToStairs.traverse();
                        door = objects.getNearest(doorID);
                        if (door != null) {
                            if (calc.distanceBetween(door.getLocation(), doorTile) < 3) {
                                while (me.isMoving()) {
                                    sleep(300);
                                }
                                state = "Opening door";
                                mouse.click(door.getModel().getCentralPoint(), true);
                                sleep(2000);
                                walking.walkTileMM(new RSTile(3203, 3496));
                                sleep(1339, 1999);
                            }
                        }
                        final RSObject odoor = objects.getNearest(15535);
                        if (odoor != null) {
                            if (calc.distanceBetween(odoor.getLocation(), doorTile) < 3) {
                                walking.walkTileMM(new RSTile(3203, 3496));
                                sleep(1339, 1999);
                            }
                        }
                        sleep(1333, 2222);
                    }
                    break;
                }
                break;
            case GOBANK:
                if (calc.distanceTo(bankTile) < 30) {
                    long time = System.currentTimeMillis();
                    while ((System.currentTimeMillis() - time) < 30000 && calc.distanceTo(bankTile) > 3) {
                        BankPath.traverse();
                        sleep(1333, 2222);
                    }
                    break;
                } else {
                    if (inventory.getItem("Varrock teleport").doClick(true)) {
                        sleep(5000);
                        break;
                    }
                }
                break;
            case BANK:
                if (!interfaces.getComponent(762, 62).isValid()) {
                    final RSObject booth = objects.getNearest(782);
                    if (booth != null) {
                        if (booth.isOnScreen()) {
                            state = "Opening bank";
                            booth.interact("Bank");
                            sleep(1337);
                            break;
                        } else {
                            walking.walkTileMM(booth.getLocation());
                            sleep(1000);
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    bank.depositAllExcept(8007);
                    sleep(900, 1200);
                    withdraw();
                    break;
                }
        }

        return 300;
    }

    private State getState() {
        if (me != null && game.isLoggedIn()) {
            location = me.getLocation();
            if (!need2bank) {
                if (zombiesArea.contains(location)) {
                    if (XDgetPrayerLeft() > 100) {
                        state = "Fighting";
                        return State.FIGHT;
                    } else {
                        state = "Recharging prayer";
                        return State.RECHARGE;
                    }
                } else if (calc.distanceTo(trapdoor) < 25) {
                    if (XDgetPrayerLeft() < 430) {
                        state = "Recharging prayer";
                        return State.RECHARGE;
                    } else {
                        state = "Fighting";
                        return State.FIGHT;
                    }
                } else {
                    state = "Going to zombies";
                    return State.GOTO;
                }
            } else {
                if (calc.distanceTo(bankTile) < 3) {
                    state = "Banking";
                    return State.BANK;
                } else {
                    state = "Going to bank";
                    return State.GOBANK;
                }
            }
        }
        return State.SLEEP;
    }

    private void check() {
        if (!XDisQuickPrayerOn() && zombiesArea.contains(location)) {
            state = "Turning on prayer";
            interfaces.getComponent(749, 0).doClick(true);
            sleep(1337, 1669);
        }
        if (walking.getEnergy() > 40 && !walking.isRunEnabled()) {
            walking.setRun(true);
            sleep(1333);
        }
        if (inventory.contains(229)) {
            state = "Dropping vial";
            inventory.getItem(229).interact("Drop");
            sleep(1337, 1669);
        }
        if (players.getMyPlayer().getHPPercent() < 60) {
            state = "Eating shark";
            if (inventory.getItem(sharkID) != null) {
                inventory.getItem(sharkID).interact("Eat");
                sleep(1337, 1669);
            }
        }
        if (skills.getCurrentLevel(Skills.ATTACK) - skills.getRealLevel(Skills.ATTACK) < (skills.getCurrentLevel(Skills.ATTACK) * .06)) {
            if (inventory.getItem(atkpot) != null) {
                state = "Drinking potion";
                inventory.getItem(atkpot).doClick(true);
                sleep(1337, 1669);
            }
        }
        if (skills.getCurrentLevel(Skills.STRENGTH) - skills.getRealLevel(Skills.STRENGTH) < (skills.getCurrentLevel(Skills.STRENGTH) * .06)) {
            if (inventory.getItem(strpot) != null) {
                state = "Drinking potion";
                inventory.getItem(strpot).doClick(true);
                sleep(1337, 1669);
            }
        }
        if (uses) {
            if (!XDisFamiliarSummoned()) {
                if (inventory.contains(pouchID)) {
                    if (Integer.parseInt(interfaces.getComponent(747, 7).getText()) < 7) {
                        if (inventory.getItem(sumID) != null) {
                            state = "Drinking summoning potion";
                            inventory.getItem(sumID).doClick(true);
                            sleep(1337, 1669);
                            summon();
                        }
                    } else {
                        summon();
                    }
                }
            }
        }
        if (getloot) {
            loot();
        }
        checkbank();
    }

    private void checkbank() {
        if (!inventory.contains(sharkID)) {
            if (me.getHPPercent() < 50) {
                need2bank = true;
            }
        }
        if (!inventory.containsOneOf(atkpot)) {
            need2bank = true;
        }
        if (!inventory.containsOneOf(strpot)) {
            need2bank = true;
        }
    }

    private void loot() {
        final RSGroundItem target = groundItems.getNearest(loot);
        if (target != null) {
            RSTile loc = target.getLocation();
            if (loc != null) {
                if (calc.distanceTo(loc) < 5) {
                    state = "Looting";
                    if (!inventory.isFull()) {
                        if (target.isOnScreen()) {
                            target.interact("Take " + target.getItem().getName());
                            sleep(1000);
                            if (getMyPlayer().isMoving()) {
                                sleepUntilPlayerIsNearTile(loc, 1, 1000);
                            }
                        } else {
                            walking.walkTo(loc);
                        }
                    } else {
                        if (inventory.contains(sharkID)) {
                            if (inventory.getCount(sharkID) > 1) {
                                inventory.getItem(sharkID).interact("Eat");
                                sleep(1337, 1669);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean sleepUntilPlayerIsNearTile(RSTile tile, int distance, long timeout) {
        long time = System.currentTimeMillis();
        while ((System.currentTimeMillis() - time) < timeout) {
            RSPlayer myPlayer = players.getMyPlayer();
            if (myPlayer != null) {
                if (calc.distanceTo(tile) <= distance) {
                    return true;
                }
            }
        }
        return false;
    }

    private void summon() {
        if (inventory.contains(pouchID)) {
            state = "Summoning bunyip";
            inventory.getItem(pouchID).doClick(false);
            sleep(400, 700);
            inventory.getItem(pouchID).interact("Summon");
            sleep(1000, 1337);
            check();
        }
    }

    private void withdraw() {
        long timeout = System.currentTimeMillis();
        while (!inventory.contains(sharkID) && System.currentTimeMillis() - timeout < 45000) {
            try {
                if (pset) {
                    if (!inventory.contains(atkpot[3]) || inventory.getCount(atkpot[3]) != qtypots) {
                        if (inventory.contains(atkpot[3])) {
                            bank.deposit(atkpot[3], 0);
                            sleep(1200);
                        }
                        bank.withdraw(atkpot[3], qtypots - inventory.getCount(atkpot[3]));
                        sleep(1300, 1800);
                        if (qtypots - inventory.getCount(atkpot[3]) != 0) {
                            if (inventory.contains(atkpot[3])) {
                                bank.deposit(atkpot[3], 0);
                            }
                            bank.withdraw(atkpot[3], qtypots - inventory.getCount(atkpot[3]));
                            sleep(1300, 1800);
                        }
                    }
                    if (!inventory.contains(strpot[3]) || inventory.getCount(strpot[3]) != qtypots) {
                        if (inventory.contains(strpot[3])) {
                            bank.deposit(strpot[3], 0);
                            sleep(1200);
                        }
                        bank.withdraw(strpot[3], qtypots - inventory.getCount(strpot[3]));
                        sleep(1300, 1800);
                        if (qtypots - inventory.getCount(strpot[3]) != 0) {
                            if (inventory.contains(strpot[3])) {
                                bank.deposit(strpot[3], 0);
                            }
                            bank.withdraw(strpot[3], qtypots - inventory.getCount(strpot[3]));
                            sleep(1300, 1800);
                        }
                    }
                } else {
                    if (!inventory.contains(strpot[3]) || inventory.getCount(strpot[3]) != qtypots) {
                        if (inventory.contains(strpot[3])) {
                            bank.deposit(strpot[3], 0);
                            sleep(1200);
                        }
                        bank.withdraw(strpot[3], qtypots - inventory.getCount(strpot[3]));
                        sleep(1300, 1800);
                        if (qtypots - inventory.getCount(strpot[3]) != 0) {
                            if (inventory.contains(strpot[3])) {
                                bank.deposit(strpot[3], 0);
                            }
                            bank.withdraw(strpot[3], qtypots - inventory.getCount(strpot[3]));
                            sleep(1300, 1800);
                        }
                    }
                }
                if (uses) {
                    if (!inventory.contains(pouchID) || inventory.getCount(pouchID) != qtybunyips) {
                        if (inventory.contains(pouchID)) {
                            bank.deposit(pouchID, 0);
                            sleep(1200);
                        }
                        bank.withdraw(pouchID, qtybunyips - inventory.getCount(pouchID));
                        sleep(1300, 1800);
                        if (qtybunyips - inventory.getCount(pouchID) != 0) {
                            if (inventory.contains(pouchID)) {
                                bank.deposit(pouchID, 0);
                            }
                            bank.withdraw(pouchID, qtybunyips - inventory.getCount(pouchID));
                            sleep(1300, 1800);
                        }
                    }
                    if (!inventory.contains(sumID[3]) || inventory.getCount(sumID[3]) != qtyspots) {
                        if (inventory.contains(sumID[3])) {
                            bank.deposit(sumID[3], 0);
                            sleep(1200);
                        }
                        bank.withdraw(sumID[3], qtyspots - inventory.getCount(sumID[3]));
                        sleep(1300, 1800);
                        if (qtyspots - inventory.getCount(sumID[3]) != 0) {
                            if (inventory.contains(sumID[3])) {
                                bank.deposit(sumID[3], 0);
                            }
                            bank.withdraw(sumID[3], qtyspots - inventory.getCount(strpot[3]));
                            sleep(1300, 1800);
                        }
                    }
                }
                if (qtysharks != 0) {
                    if (!inventory.contains(sharkID) || inventory.getCount(sharkID) != qtysharks) {
                        if (inventory.contains(sharkID)) {
                            bank.deposit(sharkID, 0);
                            sleep(1200);
                        }
                        bank.withdraw(sharkID, qtysharks - inventory.getCount(sharkID));
                        sleep(1300, 1800);
                        if (qtysharks - inventory.getCount(sharkID) != 0) {
                            if (inventory.contains(sharkID)) {
                                bank.deposit(sharkID, 0);
                            }
                            bank.withdraw(sharkID, qtysharks - inventory.getCount(sharkID));
                            sleep(1300, 1800);
                        }
                    }
                }
            } catch (IllegalArgumentException | NullPointerException il) {
            }
        }
        need2bank = false;
    }

    private boolean XDisQuickPrayerOn() {
        return interfaces.getComponent(749, 0).getTextureID() == 782;
    }

    private int XDgetPrayerLeft() {
        return Integer.parseInt(interfaces.getComponent(749, 6).getText());
    }

    private boolean XDisFamiliarSummoned() {
        return interfaces.getComponent(747, 0).getTextureID() == 1802;
    }

    private boolean AttackonPoint(Point point) { //credits to Whitebear
        if (!this.calc.pointOnScreen(point)) {
            return false;
        }
        try {
            boolean stop = false;
            for (int i = 0; i <= 50; i++) {
                this.mouse.move(point);
                Object[] menuItems = this.menu.getItems();
                for (Object menuItem : menuItems) {
                    if (menuItem.toString().contains("Attack")) {
                        stop = true;
                        break;
                    }
                }
                if (stop) {
                    break;
                }
            }
            return this.menu.doAction("Attack");
        } catch (Exception localException) {
        }
        return false;
    }

    private void antiban() {
        if (players.getMyPlayer() != null) {
            if (camera.getPitch() < 90) {
                camera.setPitch(true);
            }
            int rand = random(1, antibanfreq);
            if (rand < 3) {
                state = "Antiban";
                camera.turnTo(new RSTile(players.getMyPlayer().getLocation().getX() + random(-2, 2), players.getMyPlayer().getLocation().getY() + random(-2, 2)), 2);
            }
            if (rand == 7) {
                state = "Antiban";
                mouse.moveRandomly(antibanfreq * random(1, 3));
            }
            if (rand == 6) {
                state = "Antiban";
                mouse.moveOffScreen();
            }
            if (rand == 5) {
                state = "Antiban";
                mouse.moveSlightly();
            }
        }
    }

    @Override
    public void onRepaint(Graphics g1) {
        runTime = System.currentTimeMillis() - startTime;
        seconds = runTime / 1000;
        if (seconds >= 60) {
            minutes = seconds / 60;
            seconds -= (minutes * 60);
        }
        if (minutes >= 60) {
            hours = minutes / 60;
            minutes -= (hours * 60);
        }
        currentXP = skills.getCurrentExp(Skills.ATTACK) + skills.getCurrentExp(Skills.CONSTITUTION) + skills.getCurrentExp(Skills.DEFENSE) + skills.getCurrentExp(Skills.STRENGTH);
        gainedXP = currentXP - startXP;
        xpPerHour = (int) ((3600000.0 / (double) runTime) * gainedXP);
        Graphics2D g = (Graphics2D) g1;
        g.setRenderingHints(antialiasing);
        g.drawImage(img1, 7, 344, null);
        g.drawImage(img2, 14, 257, null);
        g.setFont(font1);
        g.setColor(color1);
        g.drawString("Runtime: " + hours + ":" + minutes + ":" + seconds, 8, 375);
        g.drawString("XP Gained: " + gainedXP + " (" + xpPerHour + " per hr)", 8, 405);
        g.drawString("State: " + state + ".", 8, 435);
        final Point p = mouse.getLocation();
        final Point c = mouse.getPressLocation();
        final long mpt = System.currentTimeMillis() - mouse.getPressTime();
        if (mouse.getPressTime() == -1 || mpt >= 666) {
            g.drawImage(cursor1, p.x, p.y, null);
        }
        if (mpt < 666) {
            g.drawImage(cursor2, p.x, p.y, null);
            g.drawImage(cursor1, c.x, c.y, null);


        }
    }

    public class XDZombiesGUI extends JFrame {

        public XDZombiesGUI() {
            initComponents();
        }

        private void startActionPerformed(ActionEvent e) {
            if (combats.isSelected()) {
                pset = false;
                atkpot = combatpotion;
                strpot = combatpotion;
            } else if (supers.isSelected()) {
                pset = true;
                atkpot = atksuperpotion;
                strpot = strsuperpotion;
            } else if (extremes.isSelected()) {
                pset = true;
                atkpot = atkextremepotion;
                strpot = strextremepotion;
            } else if (overloads.isSelected()) {
                pset = false;
                atkpot = overloadpotion;
                strpot = overloadpotion;
            }
            qtypots = Integer.parseInt(npots.getText());
            qtysharks = Integer.parseInt(nsharks.getText());
            if (checkBox1.isSelected()) {
                uses = true;
                qtybunyips = Integer.parseInt(nbunyips.getText());
                qtyspots = Integer.parseInt(nspots.getText());
            } else {
                uses = false;
            }
            if (loot.isSelected()) {
                getloot = true;
            } else {
                getloot = false;
            }
            mousespeed = Integer.parseInt(mouse.getText());
            antibanfreq = Integer.parseInt(antiban.getText());
            this.setVisible(false);
        }

        private void infoActionPerformed(ActionEvent e) throws URISyntaxException {
            try {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                java.net.URI address = new java.net.URI(URL);
                desktop.browse(address);
            } catch (IOException ex) {
            }
        }

        private void thisWindowClosed(WindowEvent e) {
            antibanfreq = 5;
            stopScript();
        }

        private void initComponents() {
            // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
            // Generated using JFormDesigner Evaluation license - Xeroday CS
            tabbedPane1 = new JTabbedPane();
            panel1 = new JPanel();
            combats = new JRadioButton();
            extremes = new JRadioButton();
            supers = new JRadioButton();
            overloads = new JRadioButton();
            npots = new JTextField();
            label1 = new JLabel();
            textPane1 = new JTextPane();
            panel2 = new JPanel();
            label2 = new JLabel();
            nsharks = new JTextField();
            textPane2 = new JTextPane();
            checkBox1 = new JCheckBox();
            nbunyips = new JTextField();
            label3 = new JLabel();
            label4 = new JLabel();
            nspots = new JTextField();
            panel3 = new JPanel();
            mouse = new JTextField();
            label5 = new JLabel();
            label6 = new JLabel();
            antiban = new JTextField();
            loot = new JCheckBox();
            start = new JButton();
            info = new JButton();

            //======== this ========
            setTitle("XDZombies");
            setResizable(false);
            addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosed(WindowEvent e) {
                    thisWindowClosed(e);
                }
            });
            Container contentPane = getContentPane();
            contentPane.setLayout(null);

            //======== tabbedPane1 ========
            {
                tabbedPane1.setBorder(null);

                //======== panel1 ========
                {

                    // JFormDesigner evaluation mark
                    panel1.setBorder(new javax.swing.border.CompoundBorder(
                            new javax.swing.border.TitledBorder(new javax.swing.border.EmptyBorder(0, 0, 0, 0),
                            "", javax.swing.border.TitledBorder.CENTER,
                            javax.swing.border.TitledBorder.BOTTOM, new java.awt.Font("Dialog", java.awt.Font.BOLD, 12),
                            java.awt.Color.red), panel1.getBorder()));
                    panel1.addPropertyChangeListener(new java.beans.PropertyChangeListener() {

                        public void propertyChange(java.beans.PropertyChangeEvent e) {
                            if ("border".equals(e.getPropertyName())) {
                                throw new RuntimeException();
                            }
                        }
                    });

                    panel1.setLayout(null);

                    //---- combats ----
                    combats.setText("Combat potion");
                    combats.setSelected(true);
                    panel1.add(combats);
                    combats.setBounds(new Rectangle(new Point(10, 10), combats.getPreferredSize()));

                    //---- extremes ----
                    extremes.setText("Extreme potion set");
                    panel1.add(extremes);
                    extremes.setBounds(10, 60, 125, 23);

                    //---- supers ----
                    supers.setText("Super potion set");
                    panel1.add(supers);
                    supers.setBounds(10, 35, 120, 23);

                    //---- overloads ----
                    overloads.setText("Overload potion");
                    panel1.add(overloads);
                    overloads.setBounds(10, 85, 125, 23);
                    panel1.add(npots);
                    npots.setBounds(70, 110, 100, 20);

                    //---- label1 ----
                    label1.setText("Quantity:");
                    panel1.add(label1);
                    label1.setBounds(15, 110, 165, 20);

                    //---- textPane1 ----
                    textPane1.setText("Sets include 2 potions (1 super potion set = 2 potions, 1 attack and 1 strength).\n\nFor example, a full inventory of super sets is 14.");
                    textPane1.setBackground(UIManager.getColor("Button.background"));
                    panel1.add(textPane1);
                    textPane1.setBounds(145, 15, 245, 85);

                    { // compute preferred size
                        Dimension preferredSize = new Dimension();
                        for (int i = 0; i < panel1.getComponentCount(); i++) {
                            Rectangle bounds = panel1.getComponent(i).getBounds();
                            preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                            preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                        }
                        Insets insets = panel1.getInsets();
                        preferredSize.width += insets.right;
                        preferredSize.height += insets.bottom;
                        panel1.setMinimumSize(preferredSize);
                        panel1.setPreferredSize(preferredSize);
                    }
                }
                tabbedPane1.addTab("Potions", panel1);


                //======== panel2 ========
                {
                    panel2.setLayout(null);

                    //---- label2 ----
                    label2.setText("Number of sharks:");
                    panel2.add(label2);
                    label2.setBounds(10, 10, 100, 20);

                    //---- nsharks ----
                    nsharks.setText("2");
                    panel2.add(nsharks);
                    nsharks.setBounds(105, 10, 20, 20);

                    //---- textPane2 ----
                    textPane2.setText("It is recommended to bring a few sharks, just in case.\n\nYou'll need less sharks if you use bunyips.");
                    textPane2.setBackground(UIManager.getColor("Button.background"));
                    panel2.add(textPane2);
                    textPane2.setBounds(130, 5, 260, 70);

                    //---- checkBox1 ----
                    checkBox1.setText("Use bunyip");
                    checkBox1.setSelected(true);
                    panel2.add(checkBox1);
                    checkBox1.setBounds(new Rectangle(new Point(10, 40), checkBox1.getPreferredSize()));

                    //---- nbunyips ----
                    nbunyips.setText("4");
                    panel2.add(nbunyips);
                    nbunyips.setBounds(85, 65, 20, 20);

                    //---- label3 ----
                    label3.setText("# of pouches:");
                    panel2.add(label3);
                    label3.setBounds(10, 65, 165, 20);

                    //---- label4 ----
                    label4.setText("Number of summoning pots:");
                    panel2.add(label4);
                    label4.setBounds(10, 90, 140, 20);

                    //---- nspots ----
                    nspots.setText("1");
                    panel2.add(nspots);
                    nspots.setBounds(150, 90, 20, 20);

                    { // compute preferred size
                        Dimension preferredSize = new Dimension();
                        for (int i = 0; i < panel2.getComponentCount(); i++) {
                            Rectangle bounds = panel2.getComponent(i).getBounds();
                            preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                            preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                        }
                        Insets insets = panel2.getInsets();
                        preferredSize.width += insets.right;
                        preferredSize.height += insets.bottom;
                        panel2.setMinimumSize(preferredSize);
                        panel2.setPreferredSize(preferredSize);
                    }
                }
                tabbedPane1.addTab("Food & Summoning", panel2);


                //======== panel3 ========
                {
                    panel3.setLayout(null);

                    //---- mouse ----
                    mouse.setText("7");
                    panel3.add(mouse);
                    mouse.setBounds(190, 15, 45, 20);

                    //---- label5 ----
                    label5.setText("Mouse speed (Lower is faster)");
                    panel3.add(label5);
                    label5.setBounds(10, 15, 150, 20);

                    //---- label6 ----
                    label6.setText("Antiban frequency (higher is less)");
                    panel3.add(label6);
                    label6.setBounds(10, 40, 170, 20);

                    //---- antiban ----
                    antiban.setText("20");
                    panel3.add(antiban);
                    antiban.setBounds(190, 40, 45, 20);

                    //---- loot ----
                    loot.setText("Pick up loot? (Will slow XP rates)");
                    panel3.add(loot);
                    loot.setBounds(5, 65, 230, loot.getPreferredSize().height);

                    { // compute preferred size
                        Dimension preferredSize = new Dimension();
                        for (int i = 0; i < panel3.getComponentCount(); i++) {
                            Rectangle bounds = panel3.getComponent(i).getBounds();
                            preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                            preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                        }
                        Insets insets = panel3.getInsets();
                        preferredSize.width += insets.right;
                        preferredSize.height += insets.bottom;
                        panel3.setMinimumSize(preferredSize);
                        panel3.setPreferredSize(preferredSize);
                    }
                }
                tabbedPane1.addTab("Misc", panel3);

            }
            contentPane.add(tabbedPane1);
            tabbedPane1.setBounds(0, 0, 400, 160);

            //---- start ----
            start.setText("Start");
            start.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    startActionPerformed(e);
                }
            });
            contentPane.add(start);
            start.setBounds(250, 165, 145, 25);

            //---- info ----
            info.setText("Guide/Changelog/Forum Thread");
            info.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        infoActionPerformed(e);
                    } catch (URISyntaxException ex) {
                    }
                }
            });
            contentPane.add(info);
            info.setBounds(0, 165, 245, 25);

            { // compute preferred size
                Dimension preferredSize = new Dimension();
                for (int i = 0; i < contentPane.getComponentCount(); i++) {
                    Rectangle bounds = contentPane.getComponent(i).getBounds();
                    preferredSize.width = Math.max(bounds.x + bounds.width, preferredSize.width);
                    preferredSize.height = Math.max(bounds.y + bounds.height, preferredSize.height);
                }
                Insets insets = contentPane.getInsets();
                preferredSize.width += insets.right;
                preferredSize.height += insets.bottom;
                contentPane.setMinimumSize(preferredSize);
                contentPane.setPreferredSize(preferredSize);
            }
            pack();
            setLocationRelativeTo(getOwner());

            //---- buttonGroup1 ----
            ButtonGroup buttonGroup1 = new ButtonGroup();
            buttonGroup1.add(combats);
            buttonGroup1.add(extremes);
            buttonGroup1.add(supers);
            buttonGroup1.add(overloads);
            // JFormDesigner - End of component initialization  //GEN-END:initComponents
        }
        // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
        // Generated using JFormDesigner Evaluation license - Xeroday CS
        private JTabbedPane tabbedPane1;
        private JPanel panel1;
        private JRadioButton combats;
        private JRadioButton extremes;
        private JRadioButton supers;
        private JRadioButton overloads;
        private JTextField npots;
        private JLabel label1;
        private JTextPane textPane1;
        private JPanel panel2;
        private JLabel label2;
        private JTextField nsharks;
        private JTextPane textPane2;
        private JCheckBox checkBox1;
        private JTextField nbunyips;
        private JLabel label3;
        private JLabel label4;
        private JTextField nspots;
        private JPanel panel3;
        private JTextField mouse;
        private JLabel label5;
        private JLabel label6;
        private JTextField antiban;
        private JCheckBox loot;
        private JButton start;
        private JButton info;
        // JFormDesigner - End of variables declaration  //GEN-END:variables
    }
}