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
		addBehaviour(new PurchaseOrdersServer());
		addBehaviour(new ClearLockerService());
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
			reply.setContent(String.valueOf(getRandomFigure()));
			myAgent.send(reply);
	    }
	    else block();
	  }
	}

	
	private class PurchaseOrdersServer extends CyclicBehaviour {
	  public void action() {
	    //purchase order as proposal acceptance only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null) {
	      String title = msg.getContent();
	      ACLMessage reply = msg.createReply();
	      Integer price = (Integer) getPrice(title, msg.getSender().getLocalName());//catalogue.remove(title);
	      if (price != null)
		  {
	        reply.setPerformative(ACLMessage.INFORM);
	        System.out.println(getAID().getLocalName() + ": " + title + " sold to " + msg.getSender().getLocalName());
	      }
	      else {
	        //title not found in the catalogue, sold to another agent in the meantime (after proposal submission)
	        reply.setPerformative(ACLMessage.FAILURE);
	        reply.setContent("not-available");
	      }
	      myAgent.send(reply);
	    }
	    else {
		  block();
		}
	  }
	}

	Integer getPrice(String title, String sender)
	{ lockerItem temp;
		if(catalogue.containsKey(title)) return catalogue.remove(title);
		else
		{
			for (var x : locker )
			{
				if(x.sender.equals(sender)  && x.title.equals(title))
				{
					 temp = x;
					 locker.remove(x);
					 return x.price;
				}
			}
			return null;
		}
	}

	private class ClearLockerService extends CyclicBehaviour {
	  public void action(){
		  List<lockerItem> temp;
		  temp = new ArrayList<>();
		  for(var x : locker)
		  {
			  if((LocalTime.now().toSecondOfDay() - x.time) == 20 ) {
				  catalogue.put(x.title, x.price);
				  temp.add(x);
			  }
		  }

		  for(var t : temp)
		  {
			  locker.remove(t);
		  }
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
	private Figures getOposite(Figures figure)
	{
		return switch (figure)
		{
			case ROCK -> Figures.PAPER;
			case PAPER -> Figures.SCISSORS;
			default -> Figures.ROCK;
		};
	}

}