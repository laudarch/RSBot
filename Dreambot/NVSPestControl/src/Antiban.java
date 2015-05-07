import org.dreambot.api.methods.Calculations;
import org.dreambot.api.script.AbstractScript;

public class Antiban extends AbstractScript implements Runnable {

    @Override
    public int onLoop() {
        log("Antiban loop");
        return 1337;
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {

    }
}
