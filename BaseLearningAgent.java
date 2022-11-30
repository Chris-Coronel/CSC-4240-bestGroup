

import java.awt.Point;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import jig.engine.Timer;
import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.RobotDefense;
import jig.misc.rd.Tile;
import jig.misc.rd.ai.AgentSensoryInterface;
import jig.misc.rd.ai.InsectView;
import jig.misc.rd.ai.RobotDefenseAgent;
import jig.misc.rd.ai.WorldEffectorInterface;

/**
 * A Base Class for an agent that learns.  This class really just helps out.
 * 
 * 
 * @author Scott Wallace
 *
 */
public abstract class BaseLearningAgent implements RobotDefenseAgent {

	/** 
	 * A reference to the world. This is used to effect changes.
	 */
	WorldEffectorInterface world;

	/**
	 * A sensor system that is used to get information from the world. 
	 */
	LearningAgentSensorSystem sensors;

	/**
	 * We'll store the previous state here
	 */
	HashMap<AirCurrentGenerator, StateVector> lastState;
	HashMap<AirCurrentGenerator, StateVector> thisState;

	boolean initialized;

	/**
	 * We'll store some performance data in this log
	 */
	private FileWriter performanceLog;
	
	private Timer intervalTimer;
	
	private Timer elapsedTimer;
	
	/**
	 * A Timer helps us decide when to 
	 */

	public BaseLearningAgent() {
		initialized = false;
		thisState = new HashMap<AirCurrentGenerator, StateVector>();
		lastState = new HashMap<AirCurrentGenerator, StateVector>();
		sensors = new LearningAgentSensorSystem();
		
		// set the timer's alarm for once every five seconds
		intervalTimer = Timer.createTimer(5 * Timer.NANOS_PER_SECOND, true);
		elapsedTimer = Timer.createTimer();
		try {
			performanceLog = new FileWriter("performance.out");
			performanceLog.write("Time (ms)\tResources\tCaptured\tEscaped\n");
				
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}
	/**
	 * This method is called by the world when the agent is 'enabled' or hooked up.
	 * The world passes a reference to itself using this method so that agent can 
	 * later effect changes (perform actions).
	 * 
	 */
	public void enableEffectors(WorldEffectorInterface worldKnobs) {
		world = worldKnobs;
	}

	/**
	 * This method is called by the world when the agent is 'enabled' or hooked up.
	 * The world performs active/event based updating on the agent's sensory system
	 * as the game is played. Thus, the sensors become aware of events such as an
	 * insect being created or destroyed.  When the agent deliberates, it can use
	 * knowledge from its sensors to make decisions.
	 */
	public AgentSensoryInterface getSensorySystem() {
		return sensors;
	}

	/**
	 * This is a high level function for subclasses of the BaseLearningAgent.
	 * It is used to determine whether the local state (as represented by a state
	 * vector) has changed in the vicinity of a particular AirCurrentGenerator.
	 * 
	 * The method operates by getting the current state for the area around a
	 * particular AirCurrentGenerator and checking to see if it is different
	 * than the cached state, if so, its because the environment itself has changed.
	 * 
	 * 
	 * @param acg the air current generator (such as a vacuum) at the center of the neighborhood 
	 * @return <code>true</code> iff the state associated with this acg changed since the last call
	 */
	protected boolean stateChanged(AirCurrentGenerator acg) {
		StateVector wasState, state;

		wasState = thisState.get(acg);
		state = StateVector.buildForTower(acg, sensors);
		if (wasState == null) {
			lastState.put(acg, state);
			thisState.put(acg, state);
			return true;
		}
		if (!wasState.equals(state)) {
			lastState.put(acg, wasState);
			thisState.put(acg, state);
			return true;
		}
		return false;
	}

	/**
	 * At regular intervals log the agent's performance.
	 */
	public void updatePerformanceLog() {
		if (intervalTimer.alarmExpired()) {
			RobotDefense rd = RobotDefense.getGame();
			
			try {
				performanceLog.write(Long.toString(elapsedTimer.getTimeSinceReset()/Timer.NANOS_PER_MS));
				performanceLog.write("\t");
				performanceLog.write(Integer.toString(rd.pf.countCrystals()));
				performanceLog.write("\t");
				performanceLog.write(Integer.toString(rd.pf.capturedInsects()));
				performanceLog.write("\t");
				performanceLog.write(Integer.toString(rd.pf.escapedInsects()));
				performanceLog.write("\n");
				performanceLog.flush();
				
			}catch (IOException e) {
				System.err.println("Couldn't write to performance log..." + e.toString());
			}
			intervalTimer.reset();
		}
	}
}

/**
 * This is the BaseLearningAgent's sensor system. This class can be viewed as a
 * container for all the information the agent <b>could</b> use in its decision
 * making. Users who want to subclass the BaseLearningAgent will typically not
 * need to interact this class directly. Rather, they will want to modify the
 * StateVector class and CellContents class to determine what information the
 * agent uses to make its decisions.
 * 
 * 
 * @author Scott Wallace
 * 
 */
class LearningAgentSensorSystem implements AgentSensoryInterface {

	/**
	 * This map has a dual function:
	 * 1) the key set can be used to track the set of air current generators on the map
	 * 2) the values indicate how many insect each generator has captured
	 */
	HashMap<AirCurrentGenerator, Integer> generators;
	
	/**
	 * This keeps track of the important properties of individual cells which will
	 * later be used to create StateVector instances.
	 */
	private CellContents[][] map;
	
	/**
	 * This keeps track of the insect's current location on the map (in grid coordiantes).
	 */
	private HashMap<InsectView, Point> insectLocationMap;

	/**
	 * This method wraps CellContents.getContentsCode to get the 
	 * code associated with one cell on the overall map. Users of the
	 * BaseLearningAgent probably want to modify CellContents
	 * instead of this method.
	 * 
	 * 
	 * @param gx the x grid coordinate of the desired cell
	 * @param gy the y grid coordinate of the desired cell
	 * @return an inteter code representing the 'state' of the desired cell
	 * 
	 * @see CellContents#getContentsCode()
	 */
	public int getMapContentsCode(int gx, int gy) {
		try {
			int r = map[gx][gy].getContentsCode();
			return r;
		} catch (ArrayIndexOutOfBoundsException aob) {
			return CellContents.getOutOfBoundsContentsCode();
		}
	}

	/**
	 * This method is called by the environment to indcate an airCurrentGenerator changed its
	 * state (power or direction).
	 * 
	 * It will be called in two situations: 
	 *  1) when the agent itself successfully changes the ACG's state (here, it serves
	 *  as proprioception)
	 *  
	 *  2) when a human user steps in a directly interacts with the game and changes
	 *  the state of a tower.
	 *  
	 * The base learning agent doesn't care if a human steps in and changes the
	 * tower settings. In addition, it assumes (as is currently the case) that
	 * its actions always work as expected.  Thus, we don't need to do anything here.
	 */
	public void airCurrentGeneratorChanged(AirCurrentGenerator ac) {}

	/**
	 * This method is called by the environment when a tile is initialized
	 * and before the game begins
	 */
	public void initializeTile(Tile t, int gx, int gy) {
		if (t instanceof AirCurrentGenerator) {
			// a acg that occupies more than one tile will 
			// call this method more than one time -- the hashmap ensures
			// a single entry is added
			generators.put((AirCurrentGenerator) t, 0);

		}
		map[gx][gy].setTile(t);
	}

	/**
	 * This method is called by the environment when the size of the world 
	 * is determined (before the game begins)
	 */
	public void initializeWorld(int width, int height) {
		generators = new HashMap<AirCurrentGenerator, Integer>(20);
		map = new CellContents[width][height];
		insectLocationMap = new HashMap<InsectView, Point>();

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				map[x][y] = new CellContents();
			}
		}
	}

	/**
	 * This method is called by the environment when a new Insect is created.
	 * 
	 * @param i the agent's view (an InsectView instance) of a new insect
	 *      the InsectView can be used to uniquely identify an insect over the
	 *      course of time (i.e., there is a one to one mapping of Insect objects
	 *      and InsectView objects, and this mapping is persistent over the lifetime
	 *      of the Insect object).
	 * 
	 * @param x the x location of the new insect (in grid coordinates)
	 * @param y the y location of the new insect (in grid coordinates)
	 * 
	 */
	public void insectCreated(InsectView i, int x, int y) {
		map[x][y].addInsect(i);
		insectLocationMap.put(i, new Point(x, y));

	}

	/**
	 * This method is called by the environment when an Insect is captured.
	 * 
	 * @param i the agent's view (an InsectView instance) of the insect
	 * @param acg the AirCurrentGenerator that captured the insect
	 */
	public void insectCaptured(InsectView i, AirCurrentGenerator acg) {
		Integer caught = generators.get(acg);
		generators.put(acg, new Integer(caught.intValue() + 1));
		Point oldLoc = insectLocationMap.get(i);
		if (oldLoc == null) {
			System.err.println("WARNING: couldn't look up insect!" + i);
			return;
		}
		map[oldLoc.x][oldLoc.y].removeInsect(i);
		insectLocationMap.remove(i);
	}

	
	/**
	 * This method is called by the environment when an Insect reaches its goal.
	 * 
	 * @param i the agent's view (an InsectView instance) of the insect
	 */
	public void insectObtainedGoal(InsectView i) {
		Point oldLoc = insectLocationMap.get(i);
		if (oldLoc == null) {
			System.err.println("WARNING: couldn't look up insect!" + i);
			return;
		}
		map[oldLoc.x][oldLoc.y].removeInsect(i);
		insectLocationMap.remove(i);
	}

	/**
	 * This method is called by the environment when an Insect changes grid cells.
	 *
	 * @param i the agent's view (an InsectView instance) of the insect
	 * @param newGridX the insect's new x position in grid coordinates
	 * @param newGridY the insect's new y position in grid coordinates
	 */
	public void insectGridLocationChanged(InsectView i, int newGridX,
			int newGridY) {

		Point oldLocation = insectLocationMap.get(i);
		if (oldLocation == null) {
			System.err.println("WARNING: couldn't look up insect!" + i);
			return;
		}
		map[oldLocation.x][oldLocation.y].removeInsect(i);
		map[newGridX][newGridY].addInsect(i);
		// desctructively modify hash value
		oldLocation.x = newGridX;
		oldLocation.y = newGridY;

	}

	/**
	 * This method is called by the environment when an air current changes
	 * on a particular cell. We'll pass this off to the CellContent object.
	 * 
	 */
	public void updateAirCurrent(AirCurrentGenerator a, int x, int y,
			double xmag, double ymag) {
		map[x][y].setAirCurrent(a, xmag, ymag);

	}

}