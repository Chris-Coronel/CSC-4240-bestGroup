import jig.misc.rd.ai.AgentFactory;
import jig.misc.rd.ai.RobotDefenseAgent;




public class AlexIsTheGooseFactory implements AgentFactory {

	public RobotDefenseAgent createAgent(String name, String agentResource) {
		return new AlexIsTheGooseAgent();
	}

}
