package client;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
//import java.util.Timer;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.json.JSONObject;

public class Broker implements MqttCallback {
	
	MqttClient client;
	static Hashtable<String, Attributes> statsMap = new Hashtable<String, Attributes>();
	static TreeSet<Coo> scoreSet = new TreeSet<Coo>();
	static Hashtable<String, Hashtable<String, MqttMessage>> ledger = new Hashtable<String, Hashtable<String, MqttMessage>>();
	
	static double[] average = new double[7];
	static double[] sigma = new double[7];
	static int n = 0;
	
	static String BrokerIp;
	
	static final Object lock = new Object();
	
	public static synchronized boolean containedStatsMap(String name){
		return statsMap.containsKey(name);
	}
	
	public static synchronized void putStatsMap(String name, Attributes stats){
		statsMap.put(name, stats);
	}
	
	public static synchronized void setScoreSet(TreeSet<Coo> tree){
		scoreSet.clear();
		scoreSet.addAll(tree);
	}
		
	public static void setAverage(double[] avg){
		for (int i=0; i<7; i++) {
			average[i] = avg[i];
		}	
	}
	
	public static void setSigma(double[] sgm){
		for (int i=0; i<7; i++) {
			sigma[i] = sgm[i];
		}
	}
	
	public static void setN(int number){
		n = number;
	}
	
	public static synchronized int getNoTasks(String key) {
		if (ledger.contains(key)) {
			return ledger.get(key).size();
		}
		else {
			return 0;
		}
	}
	
	public static synchronized int getTrust(String key) {
		Random rand = new Random();
		return rand.nextInt(100);
	}
	
	public static synchronized Attributes getStats(String key){
		return statsMap.get(key);
	}
	
	public static synchronized void removeStats(String key) {
		statsMap.remove(key);
	}
	
	public static synchronized Hashtable<String, Attributes> getStatsMap(){
		return statsMap;
	}
	
	public static double[] getAverage(){
		return average;
	}
	
	public static double[] getSigma(){
		return sigma;
	}
	
	public static int getN(){
		return n;
	}
	
	public void callForStats() {
		MqttMessage msg = new MqttMessage();
		msg.setPayload("Call for stats".getBytes());
		
		try {
			client.publish("stats/call", msg);
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		BrokerIp = args[0];
		new Broker().connect();
	}
	
	public void connect() {
		try {
			client = new MqttClient("tcp://"+BrokerIp+":1883", "broker");
			client.connect();
			client.setCallback(this);
			client.subscribe("toBroker/toExec");
			client.subscribe("toBroker/#");
			client.subscribe("stats/#");
			client.subscribe("orfeas/sagapo");
			client.subscribe("orfeas/kaigo");
			
			
			ScheduledExecutorService updaterExecutor = Executors.newSingleThreadScheduledExecutor();
			updaterExecutor.scheduleAtFixedRate(new Updater(this), 0, 10, TimeUnit.SECONDS);
			
			
			ScheduledExecutorService scorerExecutor = Executors.newSingleThreadScheduledExecutor();
			scorerExecutor.scheduleAtFixedRate(new Scorer(), 5, 10, TimeUnit.SECONDS);
			
			
			//Timer timer = new Timer();
			//timer.schedule(new Scorer(), 0, 1000);
						
		} catch (MqttException e) {
			e.printStackTrace();
	    }
	}
	
	@Override
	public void connectionLost(Throwable cause) {
		// TODO Auto-generated method stub
	
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		System.out.println(message);		
		if (topic.equals("toBroker/toExec")) {
			System.out.println("Received from Gateway");
			
			
			//If you want to score before choosing host:
			/*	Thread t2 = new Scorer();
				t2.start();
				t2.join();
			*/
			
			
			
			String bestName = scoreSet.first().name;
			
			MqttTopic pubTopic = client.getTopic("fromBroker/" + bestName);
			pubTopic.publish(message);

			String jsonMsg = new String(message.getPayload(), "US-ASCII");
			JSONObject obj = new JSONObject(jsonMsg);
			String id = (obj.getJSONObject("id")).toString();
			
			Hashtable<String, MqttMessage> tmpTasks;
			
			
			if (ledger.get(bestName).isEmpty()) {
				tmpTasks = new Hashtable<String, MqttMessage>();
			}
			else {
				tmpTasks = ledger.get(bestName);
			}
			tmpTasks.put(id, message);
			
			ledger.put(bestName, tmpTasks);
	
			//client.publish("fromBroker/toTask", message);
			
			System.out.println("Forwarding to Host");
		}
		
		////topic.contains("toBroker") is an ok condition because it is certainly NOT "toBroker/toExec"
		else if (topic.contains("toBroker/taskResults")) {
			System.out.println("Received from Host");
			
			MqttTopic pubTopic = client.getTopic("fromBroker/results");
			pubTopic.publish(message);
			
			String payload = message.toString();
			String[] subl =payload.split("~");
			
			Hashtable<String, MqttMessage> tmpTasks;
			
			tmpTasks = ledger.get(subl[0]);
			tmpTasks.remove(subl[1]);
			if (tmpTasks.isEmpty()) {
				ledger.remove(subl[0]);
			}
			else {
				ledger.put(subl[0], tmpTasks);
			}
			
			//client.publish("fromBroker/results", message);
			
			System.out.println("Forwarding to Gateway");
		}
		
		else if (topic.contains("stats")) {
			System.out.println("Received stats");
			
			//MqttTopic pubTopic = client.getTopic("a/test");
			//pubTopic.publish(message);
			
			
			
			Thread t1 = new Stater(topic, message);
			//t1.setPriority(Thread.NORM_PRIORITY - 1); 
			t1.start();
			System.out.println("Started thread");
			t1.join();
			
			
			if ((message.toString()).startsWith("offline")) {
				
				Thread t2 = new Scorer(); //TI PAIZEI EDO???AAAAAA
				t2.start();
				t2.join();
				
				String hostName = topic.substring(6);
				
				Hashtable<String, MqttMessage> tmpTasks;
				tmpTasks = ledger.get(hostName);
				
				
				Set<String> keys = tmpTasks.keySet();
				Iterator<String> itr = keys.iterator();
				
				String key;			
				
				while (itr.hasNext()) {
					key = itr.next();
			    	
					
					String bestName = scoreSet.first().name;
					MqttMessage tmpMsg = new MqttMessage();
					tmpMsg = tmpTasks.get(key);
					
					MqttTopic pubTopic = client.getTopic("fromBroker/" + bestName);
					pubTopic.publish(tmpMsg);

					Hashtable<String, MqttMessage> tmpTasks2;
										
					if (ledger.get(bestName).isEmpty()) {
						tmpTasks2 = new Hashtable<String, MqttMessage>();
					}
					else {
						tmpTasks2 = ledger.get(bestName);
					}
					tmpTasks2.put(key, tmpMsg);
					
					ledger.put(bestName, tmpTasks2);
			    }
				ledger.get(hostName).clear();
				ledger.remove(hostName);
			
				
				//Old way with Timer.
/*				Timer timer = new Timer();
				timer.schedule(new Scorer(), 0);
				
				synchronized (Broker.lock) { 
					String hostName = topic.substring(6);
					
					Hashtable<String, MqttMessage> tmpTasks;
					tmpTasks = ledger.get(hostName);
					
					
					Set<String> keys = tmpTasks.keySet();
					Iterator<String> itr = keys.iterator();
					
					String key;			
					
					while (itr.hasNext()) {
						key = itr.next();
				    	
						
						String bestName = scoreSet.first().name;
						MqttMessage tmpMsg = new MqttMessage();
						tmpMsg = tmpTasks.get(key);
						
						MqttTopic pubTopic = client.getTopic("fromBroker/" + bestName);
						pubTopic.publish(tmpMsg);
	
						Hashtable<String, MqttMessage> tmpTasks2;
											
						if (ledger.get(bestName).isEmpty()) {
							tmpTasks2 = new Hashtable<String, MqttMessage>();
						}
						else {
							tmpTasks2 = ledger.get(bestName);
						}
						tmpTasks2.put(key, tmpMsg);
						
						ledger.put(bestName, tmpTasks2);
				    }
					ledger.get(hostName).clear();
					ledger.remove(hostName);
				}*/
			}
			
		}
		
		
		
		
		//Debug
		if (topic.equals("orfeas/sagapo")) {
			System.out.println("statsMap:");
			Set<String> keys = statsMap.keySet();
			Iterator<String> itr = keys.iterator();
			
			String key;
			Attributes stats;	

			while (itr.hasNext()) {
		    	key = itr.next();
		    	stats = statsMap.get(key);
		    	
		    	System.out.println(stats.cpu + " " + stats.ram + " " + stats.zone + stats.direction + " " + stats.latency + " " + stats.battery);
		    	
		    	
		    }
			
			System.out.println(n);
			
			System.out.println("average");
			for (int i=0; i<7; i++) {
				System.out.println(average[i]);
			}
			
			System.out.println("sigma");
			for (int i=0; i<7; i++) {
				System.out.println(sigma[i]);
			}
						
		}
		
		if (topic.equals("orfeas/kaigo")) {
			System.out.println("scoreSet");
				
			
			for (Coo i : scoreSet) {
				System.out.println(i.name + "------" + i.rank);
			}
		}
		

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
	
	}

}