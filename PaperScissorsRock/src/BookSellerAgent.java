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
import java.util.concurrent.TimeUnit;

public class BookSellerAgent extends Agent {
  private HashMap<String, Integer> catalogue;
  private List<lockerItem> locker;
  private BookSellerGui myGui;
  private HashMap<String,ArrayList<String>> map;

  public class lockerItem{
	  public String title;
	  public Integer price;
	  public Integer time;
	  public String sender;
  }

  protected void setup() {
    catalogue = new HashMap<>();
    locker = new ArrayList<>();
    myGui = new BookSellerGui(this);
    myGui.display();

    //book selling service registration at DF
    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("book-selling");
    sd.setName("JADE-book-trading");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
    
    addBehaviour(new OfferRequestsServer());

    addBehaviour(new PurchaseOrdersServer());
    addBehaviour(new ClearLockerService());
  }

  protected void takeDown() {
    //book selling service deregistration at DF
    try {
      DFService.deregister(this);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
  	myGui.dispose();
    System.out.println("Seller agent " + getAID().getName() + " terminated.");
  }

  //invoked from GUI, when a new book is added to the catalogue
  public void updateCatalogue(final String title, final int price) {
    addBehaviour(new OneShotBehaviour() {
      public void action() {
		catalogue.put(title, price);
		  System.out.println(catalogue);
		System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + price);
      }
    } );
  }
  
	private class OfferRequestsServer extends CyclicBehaviour {
	  public void action() {
	    //proposals only template
		MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
		ACLMessage msg = myAgent.receive(mt);
	    if (msg != null)
		{
	      String title = msg.getContent();
	      ACLMessage reply = msg.createReply();
	      Integer price = (Integer) catalogue.get(title);
			System.out.println(catalogue);
	      if (price != null)
		  {
	        //title found in the catalogue, respond with its price as a proposal
			  var item = new lockerItem();
			  item.price = price;
			  item.title = title;
			  item.time = LocalTime.now().toSecondOfDay();
			  item.sender = msg.getSender().getLocalName();

		  	locker.add(item);
		  	catalogue.remove(title);
	        reply.setPerformative(ACLMessage.PROPOSE);
	        reply.setContent(String.valueOf(price.intValue()));
	      }
	      else
		  {
			  //title not found in the catalogue
			  reply.setPerformative(ACLMessage.REFUSE);
			  reply.setContent(title + " not-available");
		  }

	      myAgent.send(reply);
	    }
	    else
		{
	      block();
	    }
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

}
