package client;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.lang.Math;


public class Stater extends Thread {
	String topic;
	MqttMessage message; 
	
	double[] avg = new double[7];
	double[] sgm = new double[7];
	//double[] squ = new double[6];
	//double[] sum = new double[6];
	int n;
	
	public Stater(String t, MqttMessage m) {
		topic = t;
		message = m;
	}

	private double calcAverageOnline(int i, double x) {
		return ((n-1) * avg[i] + x)/n;
	}
	
	private double calcSigmaOnline(int i, double x) {
		double sigma = ((n-2) * sgm[i]*sgm[i] + (n * (avg[i] - x)*(avg[i] - x))/(n-1))/(n-1);  
		if (Double.isNaN(Math.sqrt(sigma))) {
			return 0;
		}
		else {
			return Math.sqrt(sigma);
		}
	}
	
	private double calcAverageOffline(int i, double x) {
		return (n * avg[i] - x) / (n - 1);
	}
	
	private double calcSigmaOffline(int i, double x) {
		double sigma = ((n-1)*sgm[i]*sgm[i] - (n*(avg[i]-x)*(avg[i]-x))/(n-2)) / (n-2);
		if (Double.isNaN(Math.sqrt(sigma))) {
			return 0;
		}
		else {
			return Math.sqrt(sigma);
		}
	}
	
	private void hostOnline(String hostName, Attributes stats) {
		if (Broker.getStatsMap().isEmpty()) {
			
			Broker.putStatsMap(hostName, stats);
			
			avg[0] = stats.cpu;
			avg[1] = stats.ram;
			avg[2] = stats.location;
			avg[3] = stats.latency;
			avg[4] = stats.battery;
			avg[5] = stats.noTasks;
			avg[6] = stats.trust;
			
			Broker.setAverage(avg);
			
			sgm[0] = 0;
			sgm[1] = 0;
			sgm[2] = 0;
			sgm[3] = 0;
			sgm[4] = 0;
			sgm[5] = 0;
			sgm[6] = 0;
			
/*			sgm[0] = 0;
			sgm[0] = 0;
			sgm[0] = 0;			*/
			
			Broker.setSigma(sgm);
			
			n=1;
			Broker.setN(n);
		}
		else {
			Broker.putStatsMap(hostName, stats);
			
			n = Broker.getN();
			n = n + 1;
			Broker.setN(n);
									
			avg = Broker.getAverage();
					
			avg[0] = calcAverageOnline(0, stats.cpu);
			avg[1] = calcAverageOnline(1, stats.ram);
			avg[2] = calcAverageOnline(2, stats.location);
			avg[3] = calcAverageOnline(3, stats.latency);
			avg[4] = calcAverageOnline(4, stats.battery);
			avg[5] = calcAverageOnline(5, stats.noTasks);
			avg[6] = calcAverageOnline(5, stats.trust);
			
					
			Broker.setAverage(avg);
					
			sgm = Broker.getSigma();

			sgm[0] = calcSigmaOnline(0, stats.cpu);
			sgm[1] = calcSigmaOnline(1, stats.ram);
			sgm[2] = calcSigmaOnline(2, stats.location);
			sgm[3] = calcSigmaOnline(3, stats.latency);
			sgm[4] = calcSigmaOnline(4, stats.battery);
			sgm[5] = calcSigmaOnline(5, stats.noTasks);
			sgm[6] = calcSigmaOnline(5, stats.trust);
					
			Broker.setSigma(sgm);	
		}
	
	}
	
	private void hostOffline(String hostName) {
		Attributes toDel = Broker.getStats(hostName);
		
		n = Broker.getN();
		
		sgm = Broker.getSigma();
		
		avg = Broker.getAverage();
		
		sgm[0] = calcSigmaOffline(0, toDel.cpu);
		sgm[1] = calcSigmaOffline(1, toDel.ram);
		sgm[2] = calcSigmaOffline(2, toDel.location);
		sgm[3] = calcSigmaOffline(3, toDel.latency);
		sgm[4] = calcSigmaOffline(4, toDel.battery);
		sgm[5] = calcSigmaOffline(5, toDel.noTasks);
		sgm[6] = calcSigmaOffline(6, toDel.trust);
		
		Broker.setSigma(sgm);
					
		avg[0] = calcAverageOffline(0, toDel.cpu);
		avg[1] = calcAverageOffline(1, toDel.ram);
		avg[2] = calcAverageOffline(2, toDel.location);
		avg[3] = calcAverageOffline(3, toDel.latency);
		avg[4] = calcAverageOffline(4, toDel.battery);
		avg[5] = calcAverageOffline(5, toDel.noTasks);
		avg[6] = calcAverageOffline(6, toDel.trust);
		
		Broker.setAverage(avg);
		

		n = n - 1;
		Broker.setN(n);
		
		Broker.removeStats(hostName);		
	}
	

	
	@Override
	public void run() {
		synchronized (Broker.lock) {
			//Stater.yield();
			String hostName = topic.substring(6);
			String payload = message.toString();
			String[] subl =payload.split("-");
			
			Attributes stats;
						
			if (subl[0].equals("online")) {
				stats = new Attributes(Integer.parseInt(subl[1]), Long.parseLong(subl[2]), Integer.parseInt(subl[3]), Integer.parseInt(subl[4]), Long.parseLong(subl[5]), Integer.parseInt(subl[6]), Broker.getNoTasks(hostName), Broker.getTrust(hostName));
				System.out.println(hostName + ":" + stats.cpu + " " + stats.ram + " " + stats.zone + " " + stats.direction + " " + stats.latency + " " + stats.battery + " " + stats.noTasks + " " + stats.trust);
			
				if (Broker.containedStatsMap(hostName)) {
					hostOffline(hostName);
					hostOnline(hostName, stats);
				}
				else {
					hostOnline(hostName, stats);
				}
			}
			else if (subl[0].equals("offline") && Broker.containedStatsMap(hostName)) {
				hostOffline(hostName);
			}
			
			
			
			
			
				
/*			if (subl[0].equals("online")) {
				

				
				stats = new Ubl(Double.parseDouble(subl[1]), Double.parseDouble(subl[2]), Double.parseDouble(subl[3]));
				
				System.out.println(hostName + ":" + stats.u + " " + stats.b + " " + stats.l);

				if (Broker.getStatsMap().isEmpty()) {
					
					Broker.putStatsMap(hostName, stats);
					
					avg[0] = stats.u;
					avg[1] = stats.b;
					avg[2] = stats.l;
					
					Broker.setAverage(avg);
					
					sgm[0] = 0;
					sgm[0] = 0;
					sgm[0] = 0;
					
					Broker.setSigma(sgm);
					
					n=1;
					Broker.setN(n);
				}
				else if (Broker.containedStatsMap(hostName)){
				//TODO better maybe
					toDel = Broker.getStats(hostName);
					
					n = Broker.getN();
					
					if (n == 1) {
						avg[0] = stats.u;
						avg[1] = stats.b;
						avg[2] = stats.l;
						
						Broker.setAverage(avg);
						
						sgm[0] = 0;
						sgm[0] = 0;
						sgm[0] = 0;
						
						Broker.setSigma(sgm);
						
						Broker.putStatsMap(hostName, stats);
					}
					else {
					
						sgm = Broker.getSigma();
						
						avg = Broker.getAverage();
						
						sgm[0] = calcSigmaOffline(0, toDel.u);
						sgm[1] = calcSigmaOffline(1, toDel.b);
						sgm[2] = calcSigmaOffline(2, toDel.l);
						
						Broker.setSigma(sgm);
									
						avg[0] = calcAverageOffline(0, toDel.u);
						avg[1] = calcAverageOffline(1, toDel.b);
						avg[2] = calcAverageOffline(2, toDel.l);
						
						Broker.setAverage(avg);
						
	
						n = n - 1;
						Broker.setN(n);
						
						Broker.removeStats(hostName);
						
						Broker.putStatsMap(hostName, stats);
						
						n = Broker.getN();
						n = n + 1;
						Broker.setN(n);
												
						avg = Broker.getAverage();
								
						avg[0] = calcAverageOnline(0, stats.u);
						avg[1] = calcAverageOnline(1, stats.b);
						avg[2] = calcAverageOnline(2, stats.l);
								
						Broker.setAverage(avg);
								
						sgm = Broker.getSigma();
		
						sgm[0] = calcSigmaOnline(0, stats.u);
						sgm[1] = calcSigmaOnline(1, stats.b);
						sgm[2] = calcSigmaOnline(2, stats.l);
								
						Broker.setSigma(sgm);
					}
					
				} 
				else {
					
					Broker.putStatsMap(hostName, stats);
							
					n = Broker.getN();
					n = n + 1;
					Broker.setN(n);
											
					avg = Broker.getAverage();
							
					avg[0] = calcAverageOnline(0, stats.u);
					avg[1] = calcAverageOnline(1, stats.b);
					avg[2] = calcAverageOnline(2, stats.l);
							
					Broker.setAverage(avg);
							
					sgm = Broker.getSigma();
	
					sgm[0] = calcSigmaOnline(0, stats.u);
					sgm[1] = calcSigmaOnline(1, stats.b);
					sgm[2] = calcSigmaOnline(2, stats.l);
							
					Broker.setSigma(sgm);
				}

				
			}
			else if (subl[0].equals("offline") && Broker.containedStatsMap(hostName)) {
				
				toDel = Broker.getStats(hostName);
				
				n = Broker.getN();
				
				sgm = Broker.getSigma();
				
				avg = Broker.getAverage();
				
				sgm[0] = calcSigmaOffline(0, toDel.u);
				sgm[1] = calcSigmaOffline(1, toDel.b);
				sgm[2] = calcSigmaOffline(2, toDel.l);
				
				Broker.setSigma(sgm);
							
				avg[0] = calcAverageOffline(0, toDel.u);
				avg[1] = calcAverageOffline(1, toDel.b);
				avg[2] = calcAverageOffline(2, toDel.l);
				
				Broker.setAverage(avg);
				

				n = n - 1;
				Broker.setN(n);
				
				Broker.removeStats(hostName);
				
			
			}
			*/	
		}
	}
}
