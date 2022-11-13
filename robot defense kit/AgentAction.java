

import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.Direction;

/**
 * This class encapsulates a single action that could be given to an 
 * AirCurrentGenerator...namely an adjustment of power and direction settings.
 *
 * The class is immutable.
 * 
 * @author Scott Wallace
 *
 */
public class AgentAction {
	private int power;
	private Direction facing;
	
	public AgentAction(int power, Direction facing) {
		this.power = power;
		this.facing = facing;
	}	
	public void doAction(AirCurrentGenerator acg) {
		acg.setFacingDirection(facing);
		acg.setPower(power);
	}
	public Direction getDirection() {return facing; }

	public int getPower() { return power;}
}

