


import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;

import jig.misc.rd.AirCurrentGenerator;
import jig.misc.rd.Tile;
import jig.misc.rd.ai.InsectView;
import jig.misc.rd.tiles.FanTower;


/**
 * The state vector is used to encode the environment's state; thereby allowing
 * an agent to learn a function that maps states to actions.
 * 
 * The state vector encodes relevant map attributes near a tower.
 * 
 * @author Scott Wallace
 *
 */
public class StateVector {
	
	/**
	 * The radius of the neighborhood.  If radius = 1, only cells immediately
	 * adjacent to the tower are taken into account.  A larger radius means
	 * more cells are taken into account, but also increases the state space.
	 */
	static final int RADIUS = 1;
	
	/**
	 * Member variables with the 'ns' prefix are NOT actually part of the state
	 * representation. Rather they are used for internal bookkeeping and string
	 * layout
	 */
	private int nsTowerWidth, nsTowerHeight;
	
	
	/**
	 * the cellContentsCode encodes the 'state' of nearby cells. 
	 * @see CellContents.getContentsCode
	 * @see getMapContentsCode
	 * 
	 */
	private int[] cellContentsCode;
	
	/**
	 * the state is also contingent on the type of Tower 
	 * at the center of this neighborhood.
	 */
	private Class<? extends AirCurrentGenerator> towerType;
	
	/** The hashcode for the state is precomputed and stored here */
	private int hashCode;

	private static StateVector emptyState;
	
	private StateVector() {}
	
	public static StateVector emptyStateVector() {
		if (emptyState == null) {
			emptyState = new StateVector();
			emptyState.cellContentsCode = new int[0];
			emptyState.hashCode = 0;
			emptyState.towerType = FanTower.class;
		}
		return emptyState;
		
	}
	public static StateVector buildForTower(AirCurrentGenerator acg, LearningAgentSensorSystem sensors) {
		
		StateVector s = new StateVector();
		s.nsTowerWidth = acg.getGridWidth();
		s.nsTowerHeight = acg.getGridHeight();
		
		s.towerType = acg.getClass();
		
		//
		// The cell contents code is an array that stores values associated
		// with 'nearby' cells. The array contains all cells within RADIUS cells
		// (in either the x or y direction) from the tower.  Thus with RADIUS 1
		// the array contains all cells directly adjacent (including diagonally)
		// to the tower.
		//
		// The array's layout starts with index 0
		// in the upper left (northwest) corner of the neighborhood located
		// (-RADIUS, -RADIUS) cells from the upper left corner of the tower.
		// Indexing continues first from left to right, then from top to bottom
		// the cells in the center, occupied by the tower itself are skipped.
		s.cellContentsCode = new int[4*RADIUS*RADIUS + 2*RADIUS*acg.getGridHeight() + 2*RADIUS*acg.getGridWidth()];

		// precompute the hash code as we go
		s.hashCode = s.nsTowerWidth + s.nsTowerHeight + s.towerType.hashCode();
		
		
		// here we actually get the content codes and update the hashcode
		int acg_y = acg.getGridY();
		int acg_x = acg.getGridX();
		int i = 0;
		int code;
		// TOP Part (North of Tower)
		for (int y = acg_y - RADIUS; y < acg_y; y++) {
			for(int x = acg_x - RADIUS, xe = acg_x+RADIUS+acg.getGridWidth(); x < xe; x++) {
				code = sensors.getMapContentsCode(x,y);
				//System.out.println("Code for " + x + "," + y + "is " + code);
				s.cellContentsCode[i++] = code;
				s.hashCode += code;
				
			}
		}		
		// LEFT & RIGHT Parts (East and West of Tower)
		for (int y = acg_y, ye = acg_y + acg.getGridHeight(); y < ye; y++) {
			for(int x = acg_x - RADIUS; x < acg_x; x++) {
				code = sensors.getMapContentsCode(x,y);
				s.cellContentsCode[i++] = code;
				s.hashCode += code;
			}
			for(int x = acg_x + acg.getGridWidth(), xe = acg_x + acg.getGridWidth() + RADIUS; x < xe; x++) {
				code = sensors.getMapContentsCode(x,y);
				s.cellContentsCode[i++] = code;
				s.hashCode += code;
			}

		}
		// BOTTOM Part (South of Tower)
		for (int y = acg_y + acg.getGridHeight(), ye = acg_y + acg.getGridHeight() + RADIUS; y < ye; y++) {
			for(int x = acg_x - RADIUS, xe = acg_x+RADIUS+acg.getGridWidth(); x < xe; x++) {
				code = sensors.getMapContentsCode(x,y);
				s.cellContentsCode[i++] = code;
				s.hashCode += code;
			}
		}
		
		return s;
		
	}
	@Override
	public int hashCode() { return hashCode; }

	/**
	 * States are equal all of their members 
	 * (excluding members with the 'ns' prefix) are equal
	 */
	@Override
	public boolean equals(Object o) {
		if (! (o instanceof StateVector) ) { return false; }
		StateVector sv = (StateVector)o;
		
		// hash code checking offers an early failure test
		if (sv.hashCode != hashCode) return false;
		
		if (!sv.towerType.equals(towerType)) return false;
		if (sv.cellContentsCode.length != cellContentsCode.length) return false;
		for (int i = 0, e = cellContentsCode.length; i < e; i++) {
			if (sv.cellContentsCode[i] != cellContentsCode[i]) return false;
		}
		return true;
	}
	/**
	 * Yields a multi-line string representation of the state vector. This 
	 * can help with debugging
	 * 
	 */
	public String representation() {
		if (this == emptyState) return "Code: 0 [Empty State]";
		
		StringBuffer sb = new StringBuffer(80);
		sb.append("Hash Code:");
		sb.append(hashCode);
		sb.append(" Tower: ");
		sb.append(towerType.toString());
		sb.append("\nCells:\n");
		int i = 0;
		// TOP
		for (int y = 0; y < RADIUS; y++) {
			for (int x = 0, xe = 2*RADIUS+nsTowerWidth; x < xe; x++) {
				sb.append(String.format("%4d ", cellContentsCode[i++]));
			}
			sb.append('\n');
		}
		// LEFT & RIGHT
		for (int y = 0; y < nsTowerHeight; y++) {
			for (int x = 0; x < RADIUS; x++) {
				sb.append(String.format("%4d ", cellContentsCode[i++]));
			}
			for (int x = 0; x < nsTowerWidth; x++) {
				sb.append("xxxx ");
			}
			for (int x = 0; x < RADIUS; x++) {
				sb.append(String.format("%4d ", cellContentsCode[i++]));
			}
			sb.append('\n');
		}
		
		// BOTTOM
		for (int y = 0; y < RADIUS; y++) {
			for (int x = 0, xe = 2*RADIUS+nsTowerWidth; x < xe; x++) {
				sb.append(String.format("%4d ", cellContentsCode[i++]));
			}
			sb.append('\n');
		}
		
		return sb.toString();
	}
	
}

/**
 * State is typically computed based on the contents of cells 
 * within a small region.  Each cell, thus has its own individual
 * state which is encapsulated by CellContents objects and the
 * values returned by their getContentsCode() method.
 * 
 * @author Scott Wallace
 *
 */
class CellContents {
	LinkedList<InsectView> insects;
	HashMap<AirCurrentGenerator,Point2D.Double> aircurrents; 
	
	public CellContents() {
		insects = new LinkedList<InsectView>();
		aircurrents = new HashMap<AirCurrentGenerator,Point2D.Double>();
	}
	
	/**
	 * Called by the Agent's Sensory Interface when the tile corresponding
	 * to this particular cell location is set.
	 * 
	 * @param t
	 */
	public void setTile(Tile t) {
		// tile type is not important in my version of state, so I'll just ignore
		// this value
	}
	
	/**
	 * Called by the Agent's Sensory Interface when an insect has entered
	 * this cell.
	 *  
	 *  
	 * @param i the agent's view of an insect in this cell
	 */
	public void addInsect(InsectView i) {
		insects.add(i);
	}

	/**
	 * Called by the Agent's Sensory Interface when an insect has leaves
	 * this cell.
	 *  
	 * @param i the agent's view of an insect in this cell
	 */
	public void removeInsect(InsectView i) {
		insects.remove(i);
	}
	
	/**
	 * Called by the Agent's Sensory Interface when an air current generator 
	 * (e.g., a vacuum) changes the current applied to this cell (because
	 * the vacuum changed its power or direction).
	 * 
	 * NOTE: while the probability of an insect is directly related to the
	 * air currents in the cell it occupies, however for simple maps where
	 * only one ACG can add an air current to any particular cell, it is 
	 * unnecessary to use this information to calculate the state.
	 * 
	 */
	public void setAirCurrent(AirCurrentGenerator acg, double xmag, double ymag) {
		if (xmag == 0.0 && ymag == 0.0) {
			aircurrents.remove(acg);
			return;
		} 
		
		Point2D.Double current = aircurrents.get(acg);
		if (current == null) {
			// add one for this ACG
			current = new Point2D.Double(xmag, ymag);
			aircurrents.put(acg, current);
		} else {
			// destructively change the ACG's hashed value
			current.x = xmag;
			current.y = ymag;
		}
	}
	
	/**
	 * Get the 'state' of a cell that is outside of the playable area. This should
	 * either be set to something special, or the same state as an empty cell.
	 * 
	 */
	public static int getOutOfBoundsContentsCode() {
		return 0;
	}
	/**
	 *  NOTE TO STUDENTS:  This is probably the only method you'll want/need
	 *  to modify in this class.
	 * 
	 *  returns a integer code representing the contents of this cell.
	 *  For the purposes of the state vector, cell's with the SAME contents
	 *  code are IDENTICAL. Thus, it is important to think though an appropriate
	 *  representation for this code.
	 *  
	 *  This method simply returns a code based on the number and type of the
	 *  insects in this cell.
	 */
	public int getContentsCode() {
		int code = 0;
		String sn;
		for (InsectView iv : insects) {
			sn = iv.shortName();
			if (sn.equals("scarabug")) code += 1;
			else if (sn.equals("scarlite")) code += 10;
			else if (sn.equals("sqworm")) code += 100;
			else {
				System.err.println("Unknown insect type: " + sn);
				code += 7;
			}
		}
		return code;
	}
}


