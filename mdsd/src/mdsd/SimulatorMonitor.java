package mdsd;

import mdsd.controller.Robot;
import project.AbstractSimulatorMonitor;
import simbad.sim.EnvironmentDescription;

import java.util.Set;

public class SimulatorMonitor extends AbstractSimulatorMonitor<Robot> {

    public SimulatorMonitor(Set<Robot> robots, EnvironmentDescription e) {
        super(robots, e);
    }

    @Override
    public void update(Robot arg0) {
    }

    private void start() {
        // TODO: Start system. To be implemented in a button.
    }

    private void stop() {
        // TODO: Stop system. To be implemented in a button.
    }

}