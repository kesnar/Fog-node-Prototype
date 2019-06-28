package client;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class Scorer extends Thread{

	private Hashtable<String, Attributes> statsMap = new Hashtable<String, Attributes>();
	private TreeSet<Coo> scoreSet = new TreeSet<Coo>();
	
	
	private double[] sigma = new double[7];
	private double[] average = new double[7];
	
	
	@Override
	public void run() {
		synchronized (Broker.lock) {
			//System.out.println("*******Scorer Running**************");
	
			statsMap = Broker.getStatsMap();
			sigma = Broker.getSigma();
			average = Broker.getAverage();
	
			Set<String> keys = statsMap.keySet();
			Iterator<String> itr = keys.iterator();
			
			String key;
			Attributes stats;	
			
			
			scoreSet.clear();
			
			while (itr.hasNext()) {
				Coo score = new Coo();
		    	key = itr.next();
		    			    	
		    	stats = statsMap.get(key);
		    	
		    	score.name = key;
		    	score.rank = ((stats.cpu - average[0]) / sigma[0]) + ((stats.ram - average[1]) / sigma[1]) + ((stats.location - average[2]) / sigma[2]) + ((stats.latency - average[3]) / sigma[3]) + ((stats.battery - average[4]) / sigma[4]) + ((stats.noTasks - average[5]) / sigma[5]) +  ((stats.trust - average[6]) / sigma[6]);
		    	
		    	scoreSet.add(score);
		    }
			
			
			/*for (Coo i : scoreSet) {
				System.out.println(i.name + "------" + i.rank);
			}*/
			
			Broker.setScoreSet(scoreSet);
			//System.out.println("~~~~~~~Scorer Stopping~~~~~~~~~~~~~~~~~");
		}
	}
}
