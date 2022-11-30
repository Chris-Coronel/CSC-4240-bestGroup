



import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Random;

import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.Direction;
import jig.misc.rd.RobotDefense;


/**
 *  A simple agent that uses reinforcement learning to direct the vacuum
 *  The agent has the following critical limitations:
 *  
 *  	- it only considers a small set of possible actions
 *  	- it does not consider turning the vacuum off
 *  	- it only reconsiders an action when the 'local' state changes  
 *         in some cases this may take a (really) long time
 *      - it uses a very simplisitic action selection mechanism
 *      - actions are based only on the cells immediately adjacent to a tower
 *      - action values are not dependent (at all) on the resulting state 
 */
public class AlexIsTheGooseAgent extends BaseLearningAgent {

	/**
	 * A Map of states to actions
	 * 
	 *  States are encoded in the StateVector objects
	 *  Actions are associated with a utility value and stored in the QMap
	 */
	HashMap<StateVector,QMap> actions = new HashMap<StateVector,QMap>();

	/**
	 * The agent's sensor system tracks /how many/ insects a particular generator
	 * captures, but here I want to know /when/ an air current generator just
	 * captured an insect so I can reward the last action. We use this captureCount
	 * to see when a new capture happens.
	 */
	HashMap<AirCurrentGenerator, Integer> captureCount;

	/**
	 * Keep track of the agent's last action so we can reward it
	 */
	HashMap<AirCurrentGenerator, AgentAction> lastAction;
	
	/**
	 * This stores the possible actions that an agent many take in any
	 * particular state.
	 */

	int timer = 0;
	private static final AgentAction [] potentials;

	static {
		Direction [] dirs = Direction.values();
		potentials = new AgentAction[(dirs.length * 2) + 1];

		int i = 0;
		for(Direction d: dirs) {
			// creates a new directional action with the power set to full
			// power can range from 1 ... AirCurrentGenerator.POWER_SETTINGS
			potentials[i] = new AgentAction(AirCurrentGenerator.POWER_SETTINGS, d);
			i++;
			
			potentials[i] = new AgentAction(AirCurrentGenerator.POWER_SETTINGS/2, d);
			//ALTER THIS TO DO MULTIPLE SETTINGS.
			i++;
		}
		
		potentials[i] = new AgentAction(0, dirs[0]);

	}
	
	public AlexIsTheGooseAgent() {
		captureCount = new HashMap<AirCurrentGenerator,Integer>();
		lastAction = new HashMap<AirCurrentGenerator,AgentAction>();		
	}
	
	/**
	 * Step the agent by giving it a chance to manipulate the environment.
	 * 
	 * Here, the agent should look at information from its sensors and 
	 * decide what to do.
	 * 
	 * @param deltaMS the number of milliseconds since the last call to step
	 * 
	 */
	
	public void step(long deltaMS) {
		StateVector state;
		StateVector lsTemp;
		QMap qmap;
		int bugSpawn = 0;
		int currentBug = 0;
		Random r = new Random(); //built in random java util
		double rDecision = r.nextDouble(); //random double variable to be used for state change purposes 

		// This must be called each step so that the performance log is 
		// updated.
		updatePerformanceLog();
		
		for (AirCurrentGenerator acg : sensors.generators.keySet()) {
			if (!stateChanged(acg) && timer < 5){

				timer++;
				continue;
			}
			timer = 0;

			
			// Check the current state, and make sure member variables are
			// initialized for this particular state...
			state = thisState.get(acg);
			lsTemp = lastState.get(acg);

			if (actions.get(state) == null) {
				actions.put(state, new QMap(potentials));
			}
			if (captureCount.get(acg) == null) captureCount.put(acg, 0);
			
			//Previous state reading in 
			qmap = actions.get(lastState.get(acg));
			
			//reads if bugs were present in the state stored in qmap
			if (lastState.get(acg) != null){
				int[] temp;
				temp = lsTemp.getCellContents();
				for (int i : temp){ bugSpawn += i ;}

				temp = state.getCellContents();
				for (int i : temp) { currentBug += i ;} //current bug spawned is still within qmap
			}
			
			//state concurrency
			if (!stateChanged(acg)){
				//keep functioning until bugs are nomore (the programmer creed :) )
				if(currentBug != 0){
					continue;
				}
				//else updates state as next step is taken
				else{
					lsTemp = state;
					qmap = actions.get(state);
				}

			}

			// Check to see if an insect was just captured by comparing our
			// cached value of the insects captured by each ACG with the
			// most up-to-date value from the sensors
			boolean justCaptured;
			justCaptured = (captureCount.get(acg) < sensors.generators.get(acg));

			// if this ACG has been selected by the user, we'll do some verbose printing
			boolean verbose = (RobotDefense.getGame().getSelectedObject() == acg);

			// If we did something on the last 'turn', we need to reward it
			if (lastAction.get(acg) != null ) {

				// get the action map associated with the previous state
				qmap = actions.get(lastState.get(acg));

				if (justCaptured){
					int rewardVal = 10;

					//Power scaling reward modifier for each power leverl  used for capture (we only used 2 and 4)
					switch(lastAction.get(acg).getPower()){
						case 2: rewardVal = 16;
							break;

						case 4: rewardVal = 12; //did 11 just in case the reward mechanism has issues with the potential tie
							break;
						//added 1 and 3 functionality just in case

						case 1: rewardVal = 18;
							break;
						case 3: rewardVal = 14;
							break;
						default: break;
					}

					
					int i;
					for (i = 0; i < potentials.length; i++) {
						if (lastAction.get(acg) == potentials[i]) break;
					}
					if (i >= potentials.length) {
						System.err.println("ERROR: Tried to reward an action that doesn't exist in the QMap. (Ignoring reward)");
						return;
					}
					
					qmap.rewardAction(lastAction.get(acg), rewardVal);
		
					
					
					if(i >= potentials.length-2)
					{
						if(i != potentials.length-1)
						{
							int temp = i;
							i = temp - 2;
								
							qmap.rewardAction(potentials[i], rewardVal/2);
							i = temp - (potentials.length - 2) ;
							i = 0 + i;
								
							qmap.rewardAction(potentials[i], rewardVal/2);
						}
					}
					else if(i <= 1)
					{
						
						int temp = i;
						i = temp + 2;
							
						qmap.rewardAction(potentials[i], rewardVal/2);
		
						i = potentials.length - (2 - temp);
		
						qmap.rewardAction(potentials[i], rewardVal/2);
					}
					else
					{
						
						int temp = i;
						i = temp - 2;
						
						qmap.rewardAction(potentials[i], rewardVal/2);
						i = temp + 2;
						
						qmap.rewardAction(potentials[i], rewardVal/2);
		
		
					}

				}
					captureCount.put(acg,sensors.generators.get(acg));

					
				

				//Added reward incentives

				//incentive to turn off when there are no insects
				if((bugSpawn == 0) && (lastAction.get(acg).getPower() == 0)){
					qmap.rewardAction(lastAction.get(acg), 5.0);
				}
				
				//incentive to capture insects when insects have spawned
				if((bugSpawn > 0 ) && (!justCaptured)){
					for(int i = 0; i < potentials.length-1; i++)
						qmap.rewardAction(potentials[i], 1);
				}

				//insentive to stay on when insects are present
				if((bugSpawn > 0) && (lastAction.get(acg).getPower() == 0)){
					qmap.rewardAction(lastAction.get(acg), -5.0);
				}

				//POWER REGULATIONS!!!!
				/*
				 * decided to impliment in the capture reward block since that reward for each
				 * capture instance, was a headache otherwise
				 */

				if (lastAction.get(acg) != null)
					if (verbose) {
					System.out.println("Last State for " + acg.toString() );
					System.out.println(lastState.get(acg).representation());
					System.out.println("Updated Last Action: " + qmap.getQRepresentation());
				}
			} 

			// decide what to do now...
			// first, get the action map associated with the current state
			qmap = actions.get(state);

			if (verbose) {
				System.out.println("This State for Tower " + acg.toString() );
				System.out.println(thisState.get(acg).representation());
			}
			// find the 'right' thing to do, and do it.
			AgentAction bestAction = qmap.findBestAction(verbose,lastAction.get(acg));
			bestAction.doAction(acg);

			// finally, store our action so we can reward it later.
			lastAction.put(acg, bestAction);

		}
	}


	/**
	 * This inner class simply helps to associate actions with utility values
	 */
	static class QMap {
		static Random RN = new Random();

		private double[] utility; 		// current utility estimate
		private int[] attempts;			// number of times action has been tried
		private AgentAction[] actions;  // potential actions to consider

		public QMap(AgentAction[] potential_actions) {

			actions = potential_actions.clone();
			int len = actions.length;

			utility = new double[len];
			attempts = new int[len];
			for(int i = 0; i < len; i++) {
				utility[i] = 0.0;
				attempts[i] = 0;
			}
		}

		/**
		 * Finds the 'best' action for the agent to take.
		 * 
		 * @param verbose
		 * @return
		 */
		public AgentAction findBestAction(boolean verbose,AgentAction a) {
			int i,maxi,maxcount;
			maxi=0;
			maxcount = 1;
			if (verbose)
				System.out.print("Picking Best Actions: " + getQRepresentation());

			for (i = 1; i < utility.length; i++) {
				if (utility[i] > utility[maxi]) {
					maxi = i;
					maxcount = 1;
				}
				else if (utility[i] == utility[maxi]) {
					maxcount++;
				}
			}
			if (RN.nextDouble() > .2) {
				int whichMax = RN.nextInt(maxcount);

				if (verbose)
					System.out.println( " -- Doing Best! #" + whichMax);


				for (i = 0; i < utility.length; i++) {
				
					if (utility[i] == utility[maxi]) {
						if (actions[i] == a) return actions[i];
					}
				}
				for (i = 0; i < utility.length; i++) {
					if (utility[i] == utility[maxi]) {
						if (whichMax == 0) return actions[i];
						whichMax--;
					}
				}
				return actions[maxi];
			}
			else {
				int which = RN.nextInt(actions.length);
				if (verbose)
					System.out.println( " -- Doing Random (" + which + ")!!");

				return actions[which];
			}
		}

		/**
		 * Modifies an action value by associating a particular reward with it.
		 * 
		 * @param a the action performed 
		 * @param value the reward received
		 */
		public void rewardAction(AgentAction a, double value) {
			int i;
			for (i = 0; i < actions.length; i++) {
				if (a == actions[i]) break;
			}
			if (i >= actions.length) {
				System.err.println("ERROR: Tried to reward an action that doesn't exist in the QMap. (Ignoring reward)");
				return;
			}

			utility[i] = (utility[i] * attempts[i]) + value;
			attempts[i] = attempts[i] + 1;
			utility[i] = utility[i]/attempts[i];
		}
		/**
		 * Gets a string representation (for debugging).
		 * 
		 * @return a simple string representation of the action values
		 */
		public String getQRepresentation() {
			StringBuffer sb = new StringBuffer(80);

			for (int i = 0; i < utility.length; i++) {
				sb.append(String.format("%.2f  ", utility[i]));
			}
			return sb.toString();

		}

	}
}
