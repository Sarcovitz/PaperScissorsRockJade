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
			  //search only if the purchase task was ordered
			  if (!targetBookTitle.equals(""))
			  {
				  System.out.println(getAID().getLocalName() + ": I'm looking for " + targetBookTitle);
				  //update a list of known sellers (DF)
				  DFAgentDescription template = new DFAgentDescription();
				  ServiceDescription sd = new ServiceDescription();
				  sd.setType("book-selling");
				  template.addServices(sd);
				  try
				  {
					  DFAgentDescription[] result = DFService.search(myAgent, template);
					  System.out.println(getAID().getLocalName() + ": the following sellers have been found");
					  playerAgents = new AID[result.length];
					  for (int i = 0; i < result.length; ++i)
					  {
						  playerAgents[i] = result[i].getName();
						  System.out.println(playerAgents[i].getLocalName());
					  }
				  }
				  catch (FIPAException fe) { fe.printStackTrace(); }

				  myAgent.addBehaviour(new RequestPerformer());
			  }
		  }
	  });
  }

	//invoked from GUI, when purchase was ordered
	public void lookForTitle(final String title, int budget)
	{
		addBehaviour(new OneShotBehaviour()
		{
			public void action()
			{
				targetBookTitle = title;
				System.out.println(getAID().getLocalName() + ": purchase order for " + targetBookTitle + " accepted. Budget: " +budget);
			}
		});
	}

	protected void takeDown()
	{
		System.out.println("Game agent " + getAID().getLocalName() + " terminated.");
	}
  
	private class RequestPerformer extends Behaviour {
	  private AID bestSeller;
	  private int bestPrice;
	  private int repliesCnt = 0;
	  private MessageTemplate mt;
	  private int step = 0;

	  private LocalTime t0;
	
	  public void action()
	  {
		  ACLMessage turnStartRequest = new ACLMessage(ACLMessage.REQUEST);
		  for (AID player : playerAgents) turnStartRequest.addReceiver(player);
		  turnStartRequest.setContent("start");
		  turnStartRequest.setConversationId("game-start");
		  turnStartRequest.setReplyWith("turnStartRequest"+System.currentTimeMillis()); //unique value
		  myAgent.send(turnStartRequest);
		  mt = MessageTemplate.and(MessageTemplate.MatchConversationId("game-start"),
				  MessageTemplate.MatchInReplyTo(turnStartRequest.getReplyWith()));
		  t0 = LocalTime.now(); //think if it's necessary

		  ACLMessage reply = myAgent.receive(mt); ///licznik do dwóch z użyciem blocka



		  switch (step) {
	    case 0:
	      //call for proposal (CFP) to found sellers
	      ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
	      for (int i = 0; i < playerAgents.length; ++i) {
	        cfp.addReceiver(playerAgents[i]);
	      } 
	      cfp.setContent(targetBookTitle);
	      cfp.setConversationId("book-trade");
	      cfp.setReplyWith("cfp"+System.currentTimeMillis()); //unique value
	      myAgent.send(cfp);
	      mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
	                               MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
		  t0 = LocalTime.now();
	      step = 1;
	      break;
	    case 1:
	      //collect proposal
	      ACLMessage reply = myAgent.receive(mt);
			if((LocalTime.now().toSecondOfDay()  - t0.toSecondOfDay()) > 2)
			{
				System.out.println("There was no response from sellers!");
				step = 2;
				break;
			}
	      if (reply != null)
		  {
			  System.out.println(reply.getPerformative());
			  if(reply.getPerformative() == ACLMessage.REFUSE)
			  {
				  System.out.println("title not found error: " + reply.getContent());
				  break;
			  }

	        if (reply.getPerformative() == ACLMessage.PROPOSE)
			{
	          //proposal received
	          int price = Integer.parseInt(reply.getContent());
	          if (bestSeller == null || price < bestPrice )
			  {
	            //the best proposal as for now
	            bestPrice = price;
				if(bestPrice > budget) break;
	            bestSeller = reply.getSender();
	          }
	        }
			  repliesCnt++;
			  if (repliesCnt >= playerAgents.length)
			{
	          //all proposals have been received
	          step = 2; 
	        }
	      }
	      else
		  {
	        block(1000);
	      }
	      break;
	    case 2:
	      //best proposal consumption - purchase
	      ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
          order.addReceiver(bestSeller);
	      order.setContent(targetBookTitle);
	      order.setConversationId("book-trade");
	      order.setReplyWith("order"+System.currentTimeMillis());
	      myAgent.send(order);
	      mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
	                               MessageTemplate.MatchInReplyTo(order.getReplyWith()));
	      step = 3;
	      break;
	    case 3:      
	      //seller confirms the transaction
	      reply = myAgent.receive(mt);
	      if (reply != null)
		  {
	        if (reply.getPerformative() == ACLMessage.INFORM)
			{
	          //purchase succeeded
				budget -= bestPrice;
	          System.out.println(getAID().getLocalName() + ": " + targetBookTitle + " purchased for " + bestPrice + " from " + reply.getSender().getLocalName() + " Money left: " +budget);
		  System.out.println(getAID().getLocalName() + ": waiting for the next purchase order.");
		  targetBookTitle = "";
	          //myAgent.doDelete();
	        }
	        else
			{
	          System.out.println(getAID().getLocalName() + ": purchase has failed. " + targetBookTitle + " was sold in the meantime.");
	        }
	        step = 4;	//this state ends the purchase process
	      }
	      else
		  {
	        block();
	      }
	      break;
	    }        
	  }
	
	  public boolean done() {
	  	if (step == 2 && bestSeller == null) {
	  		System.out.println(getAID().getLocalName() + ": " + targetBookTitle + " is not on sale.");
	  	}
	    //process terminates here if purchase has failed (title not on sale) or book was successfully bought 
	    return ((step == 2 && bestSeller == null) || step == 4);
	  }
	}

}
