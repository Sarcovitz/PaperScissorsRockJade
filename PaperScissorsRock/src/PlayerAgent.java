package paperScissorsRock;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerAgent extends Agent
{
	private List<Figures> enemyData = new ArrayList<>();
	private List<Figures> startData = new ArrayList<>();
	protected void setup()
	{
		initializeStartData();

		enemyData.add(Figures.PAPER);
		enemyData.add(Figures.SCISSORS);
		enemyData.add(Figures.ROCK);

		//book selling service registration at DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());

		ServiceDescription sd = new ServiceDescription();
		sd.setType("game-play");
		sd.setName("JADE-game-playing");
		dfd.addServices(sd);

		try { DFService.register(this, dfd); }
		catch (FIPAException fe) { fe.printStackTrace(); }

		addBehaviour(new StartGameServer());
		addBehaviour(new ResultServer());
	}

	protected void takeDown()
	{
		try { DFService.deregister(this); }
		catch (FIPAException fe) { fe.printStackTrace(); }

		System.out.println("Player agent " + getAID().getName() + " terminated.");
	}

  //invoked from GUI, when a new book is added to the catalogue
  public void updateEnemyData(Figures figure) {
		addBehaviour(new OneShotBehaviour()
		{
		  public void action()
		  {
			enemyData.add(figure);
			System.out.println(getAID().getLocalName() + ": " + figure + " put into the enemy data.");
		  }
		} );
  }
  
	private class StartGameServer extends CyclicBehaviour {
	  public void action() {
	    //proposals only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null)
		{
			ACLMessage reply = msg.createReply();
			reply.setPerformative(ACLMessage.AGREE);
			Figures figure = getRandomFigure();
			reply.setContent(String.valueOf(figure));
			reply.setOntology(String.valueOf(GetChance(figure)));
			myAgent.send(reply);
	    }
	    else block();
	  }
	}

	
	private class ResultServer extends CyclicBehaviour {
	  public void action() {
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null) enemyData.add(getOposite(parseFigures(msg.getContent())));
	    else block();
	  }
	}

	private void initializeStartData()
	{
		for(int i =0; i< 10; i++)
		{
			int elem = ThreadLocalRandom.current().nextInt(3);
			switch (elem)
			{
				case 0: startData.add(Figures.PAPER);
				case 1: startData.add(Figures.SCISSORS);
				case 2: startData.add(Figures.ROCK);
			}
		}
	}

	private Figures getRandomFigure()
	{
		if(enemyData.size() == 3) return startData.get(ThreadLocalRandom.current().nextInt(startData.size()));
		else return enemyData.get(ThreadLocalRandom.current().nextInt(enemyData.size()));
	}

	private double GetChance(Figures figure)
	{
		if(enemyData.size() == 3) return (double)1/3;
		else
		{
			long quantity = enemyData.stream().filter(figure::equals).count();
			return (double)quantity/enemyData.size();
		}
	}

	private Figures getOposite(Figures figure)
	{
		return switch (figure)
		{
			case ROCK -> Figures.PAPER;
			case PAPER -> Figures.SCISSORS;
			default -> Figures.ROCK;
		};
	}

	private Figures parseFigures(String figure)
	{
		return switch (figure)
				{
					case "ROCK" -> Figures.ROCK;
					case "SCISSORS" -> Figures.SCISSORS;
					case "PAPER" -> Figures.PAPER;
					default -> Figures.PAPER;
				};
	}

}
