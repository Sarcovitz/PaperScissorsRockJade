package paperScissorsRock;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.sql.SQLOutput;
import java.time.LocalTime;

public class GameAgent extends Agent {
  private AID[] playerAgents;
  
	protected void setup() {
		int interval = 10000;
		Object[] args = getArguments();
		if (args != null && args.length > 0) interval = Integer.parseInt(args[0].toString());
	  addBehaviour(new TickerBehaviour(this, interval)
	  {
		  protected void onTick()
		  {
			  System.out.println(getAID().getLocalName() + ": I'm Starting game");
			  //update a list of known sellers (DF)
			  DFAgentDescription template = new DFAgentDescription();
			  ServiceDescription sd = new ServiceDescription();
			  sd.setType("game-play");
			  template.addServices(sd);
			  try
			  {
				  DFAgentDescription[] result = DFService.search(myAgent, template);
//				  System.out.println(getAID().getLocalName() + ": the following players have been found:");
				  playerAgents = new AID[result.length];
				  for (int i = 0; i < result.length; ++i)
				  {
					  playerAgents[i] = result[i].getName();
//					  System.out.println(playerAgents[i].getLocalName());
				  }
				  System.out.println();
			  }
			  catch (FIPAException fe) { fe.printStackTrace(); }

			  myAgent.addBehaviour(new RequestPerformer());
		  }
	  });
  }

	protected void takeDown()
	{
		System.out.println("Game agent " + getAID().getLocalName() + " terminated.");
	}
  
	private class RequestPerformer extends Behaviour {
	  private AID winner;
	  private Figures p1result, p2result;
	  private int repliesCnt = 0;
	  private MessageTemplate mt;
	  private int step = 0;

	  private LocalTime t0;
	
	  public void action()
	  {
		  switch (step) {
	    case 0:
			ACLMessage turnStartRequest = new ACLMessage(ACLMessage.REQUEST);
			for (AID player : playerAgents) turnStartRequest.addReceiver(player);
			turnStartRequest.setContent("start");
			turnStartRequest.setConversationId("game-start");
			turnStartRequest.setReplyWith("turnStartRequest"+System.currentTimeMillis()); //unique value
			myAgent.send(turnStartRequest);
			mt = MessageTemplate.and(MessageTemplate.MatchConversationId("game-start"),
					MessageTemplate.MatchInReplyTo(turnStartRequest.getReplyWith()));
			t0 = LocalTime.now(); //think if it's necessary
	      step = 1;
	      break;
	    case 1:
	      	//collect proposal
	      	ACLMessage reply = myAgent.receive(mt);

			if((LocalTime.now().toSecondOfDay()  - t0.toSecondOfDay()) > 2)
			{
				System.out.println("There was no response from players");
				step = 2;
				break;
			}

	      if (reply != null)
		  {
	        if (reply.getPerformative() == ACLMessage.AGREE)
			{
	          //proposal received
				System.out.println( reply.getSender().getLocalName()+" "+ reply.getContent());
				if(reply.getSender().getLocalName().equals("p1"))
					p1result = parseFigures(reply.getContent());
				else
					p2result = parseFigures(reply.getContent());
	        }
			  repliesCnt++;
			  if (repliesCnt >= playerAgents.length) step = 2;
	      }
	      else block(1000);
	      break;
	    case 2:
			String result = getWinner(p1result, p2result);
			System.out.println("Winner: " + result);
			System.out.println();

			ACLMessage p1mess = new ACLMessage(ACLMessage.INFORM);

			for (AID playerAgent : playerAgents) {
				if(playerAgent.getLocalName().equals("p1")) winner = playerAgent;
			}
			p1mess.addReceiver(winner);
			p1mess.setContent(String.valueOf(p2result));
			p1mess.setConversationId("results");
			p1mess.setReplyWith("result"+System.currentTimeMillis());

			ACLMessage p2mess = new ACLMessage(ACLMessage.INFORM);

			for (AID playerAgent : playerAgents) {
				if(playerAgent.getLocalName().equals("p2")) winner = playerAgent;
			}
			p2mess.addReceiver(winner);
			p2mess.setContent(String.valueOf(p1result));
			p2mess.setConversationId("results");
			p2mess.setReplyWith("result"+System.currentTimeMillis());

			myAgent.send(p1mess);
			myAgent.send(p2mess);

	      step = 4;
	      break;
	    }        
	  }
	
	  public boolean done() {
	    return (step == 4);
	  }
	}

	private Figures parseFigures(String figure)
	{
		return switch (figure)
				{
					case "ROCK" -> Figures.ROCK;
					case "SCISSORS" -> Figures.SCISSORS;
					case "PAPER" -> Figures.PAPER;
					default -> Figures.ROCK;
				};
	}

	private String getWinner(Figures f1, Figures f2){
		if(f1 == f2) return "draw";
		if(f1 == Figures.PAPER && f2 == Figures.ROCK) return "p1";
		if(f2 == Figures.PAPER && f1 == Figures.ROCK) return "p2";
		if(f1 == Figures.ROCK && f2 == Figures.SCISSORS) return "p1";
		if(f2 == Figures.ROCK && f1 == Figures.SCISSORS) return "p2";
		if(f1 == Figures.SCISSORS && f2 == Figures.PAPER) return "p1";
		if(f2 == Figures.SCISSORS && f1 == Figures.PAPER) return "p2";

		return "error";
	}

}
