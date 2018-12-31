package mdsd.controller;

import mdsd.model.Area;
import mdsd.model.Environment;
import mdsd.model.Mission;
import project.AbstractRobotSimulator;
import project.Point;
import simbad.sim.RangeSensorBelt;
import simbad.sim.RobotFactory;

import javax.vecmath.Point2f;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Robot extends AbstractRobotSimulator implements IControllableRover {
    private final int id;
    private static int idCount = 0;
    private Mission mission;
    private Point2f[] path;
    private ArrayList<Observer> observers;
    private Point destination;
    private List<Area> currentRooms;
    private Environment environment;
    private boolean stopped;
    private AtomicBoolean waitingForEnter;
    private int behavior;  // 0:Missions, 1:TravelinSalesRover, 2:LawnMower
    public static int BEHAVIOUR_MISSION = 0;
    public static int BEHAVIOUR_TRAVELLING_SALES_ROVER = 1;
    public static int BEHAVIOUR_LAWN_MOWER = 2;
    private RangeSensorBelt sonars;

    public Robot(Point position, String name, Environment environment, int behavior) {
        super(position, name);
        this.destination = position;
        this.environment = environment;
        currentRooms = new ArrayList<>();
        stopped = false;
        synchronized (this) {
            this.id = idCount++;
        }
        this.behavior = behavior;
        this.sonars = RobotFactory.addBumperBeltSensor(super.getAgent(), 24);

        waitingForEnter = new AtomicBoolean(false);
    }

    public Robot(Point2f position, String name, Environment environment, int behavior) {
        this(new project.Point(position.getX(), position.getY()), name, environment, behavior);
    }

    @Override
    public String toString() {
        return super.getName();
    }

    @Override
    public void setMission(Mission mission) {
        this.mission = mission;
        if (mission != null) {
            setDestination(mission.getNextPoint());
        }
    }

    @Override
    public Mission getMission() {
        return mission;
    }

    @Override
    public Point getPosition() {
        return super.getPosition();
    }

    public void update() {
        if (behavior == BEHAVIOUR_MISSION || behavior == BEHAVIOUR_TRAVELLING_SALES_ROVER) { // Mission and TravelingSalesRover
            if (mission != null && this.isAtPosition(destination)) {
                Point2f newPoint = mission.getNextPoint();
                if (newPoint != null) {
                    setDestination(newPoint);
                    start();
                }
            }
        } else if (behavior == BEHAVIOUR_LAWN_MOWER) {  // LawnMower implementation
            final double centerX = getPosition().getX();
            final double centerY = getPosition().getZ();
            final double point2x = destination.getX();
            final double point2y = destination.getZ();

            final double backX = -point2x;
            final double backY = -point2y;

            destination.setX(backX);
            destination.setZ(backY);
            setDestination(destination); // Back away for 0.5s

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            while (stopped) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            destination.setX(point2x); // Set old destination
            destination.setZ(point2y);
            setDestination(destination);

            final double rotateWith = 0.3 + Math.random() * Math.PI / 2;

            double newX = getPosition().getX() + (point2x - centerX) * Math.cos(rotateWith) - (point2y - centerY) * Math.sin(rotateWith);
            double newY = centerY + (point2x - centerX) * Math.sin(rotateWith) + (point2y - centerY) * Math.cos(rotateWith);

            if ((newX < 500 && newX > 0) || (newX > -500 && newX < 0) ||
                    (newY < 500 && newY > 0) || (newY > -500 && newY < 0)) {
                newX += 500;
                newY += 500;
            }

            destination.setX(newX);
            destination.setZ(newY);
            setDestination(destination);
        }
    }

    @Override
    public Point2f getJavaPosition() {
        Point point = super.getPosition();
        return new Point2f((float) point.getX(), (float) point.getZ());
    }

    @Override
    public Status getStatus() {
        return new Status(this);
    }

    /**
     * Starts a rover if the rover is not waiting to enter an area
     */
    @Override
    public void start() {
        stopped = false;
        if (!waitingForEnter.get()) {
            setDestination(destination);
        }
    }

    /**
     * Stops the rover until start() is called
     */
    @Override
    public void stop() {
        stopped = true;
        setDestination(getPosition());
    }

    @Override
    public String[] getFaults() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isFaulty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    @Override
    public void reportEvent(Object event) {
        // TODO
    }

    public void setDestination(Point2f dest) {
        destination = new project.Point(dest.getX(), dest.getY());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Robot) {
            Robot r2 = (Robot) obj;
            return this.id == r2.id;
        }
        return false;
    }

    @Override
    public List<Area> getRooms() {
        return currentRooms;
    }

    @Override
    public void run() {
        update();
        new Thread(() -> {
            while (true) {
                if (behavior == BEHAVIOUR_MISSION || behavior == BEHAVIOUR_TRAVELLING_SALES_ROVER) {
                    update();
                    Point2f roverPos = getJavaPosition();
                    List<Area> lastRooms = new ArrayList<>(currentRooms);
                    boolean newRoom = false;
                    List<Area> newAreas = new ArrayList<>();

                    for (Area area : environment.getAreas()) { // Check all areas if a new has been entered or left
                        if (area.contains(roverPos)) {
                            if (!lastRooms.contains(area)) {
                                waitingForEnter.set(true);
                                // Entered a new room
                                currentRooms.add(area);
                                area.enter(this);
                                for (Area area2 : environment.getAreas()) {
                                    if (!area2.equals(area)) { // Leave all old areas
                                        area2.leave(this);
                                    }
                                }
                                newRoom = true;
                                newAreas.add(area);
                            }
                        } else {
                            if (lastRooms.contains(area)) {
                                // Left a room
                                currentRooms.remove(area);
                                area.leave(this);
                            }
                        }
                    }
                    if (newRoom) { // If entered a new room
                        setDestination(getPosition()); // Stop without setting the stopped boolean
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        while (!newAreas.isEmpty()) { // Wait for all the new areas to be empty of rovers
                            newAreas.removeIf(area -> area.canEnter(this));
                            if (!newAreas.isEmpty()) {
                                try {
                                    Thread.sleep(200);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        waitingForEnter.set(false);
                        while (stopped) { // If the stop button was pressed in the GUI, wait until start is pressed
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        start();
                    } else {
                        waitingForEnter.set(false);
                    }
                } else if (behavior == BEHAVIOUR_LAWN_MOWER) {
                    while (stopped) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (sonars.getFrontQuadrantHits() > 0 || sonars.getLeftQuadrantHits() > 0 ||
                            sonars.getRightQuadrantHits() > 0 || this.isAtPosition(destination)) {
                        update(); // Only update if hit or at destination
                    }
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public class Status {
        public final int id;
        public final String name;
        public final Mission mission;
        public final Point2f destination;
        public final boolean stopped;
        public final Point2f position;

        private Status(Robot robot) {
            this.id = robot.id;
            this.mission = robot.mission;
            if (robot.destination != null) {
                this.destination = new Point2f((float) robot.destination.getX(), (float) robot.destination.getZ());
            } else {
                this.destination = null;
            }
            this.stopped = robot.stopped;
            this.position = robot.getJavaPosition();
            this.name = robot.toString();
        }
    }

}
