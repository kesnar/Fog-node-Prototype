package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.json.JSONArray;
import org.json.JSONObject;

public class Host implements MqttCallback {
	
	MqttClient client;
	String name;
	
	static String BrokerIp;

	int cpu;
	long ram;
	int zone;
	int direction;
	long latency; 
	int battery;
	
	public void setlocation(int z, int d){
		zone = z;
		direction = d;
		
		latency = zone*10;
	}
	
	public static void main(String[] args) {
		BrokerIp = args[0];
		new Host().connect();
	}
	
	public void connect() {
		try {
			
			Thread t1 = new Geolocator(this);
			t1.start();
			
			//get interface and mac
			InetAddress ip = InetAddress.getLocalHost();
			NetworkInterface interf = NetworkInterface.getByInetAddress(ip);
			byte[] mac =  interf.getHardwareAddress();
			
			//convert byte array to string (mac)
			StringBuilder sb = new StringBuilder(18);
		    for (byte b : mac) {
		        if (sb.length() > 0) {
		            sb.append(':');
		        }
		        sb.append(String.format("%02x", b));
		    }
			name = sb.toString();
			
			//System.out.println(name);
			
			MqttConnectOptions options = new MqttConnectOptions();
			options.setWill("stats/" + name, "offline".getBytes(), 0, true);
			
			client = new MqttClient("tcp://"+BrokerIp+":1883", name);
			client.connect(options);
			client.setCallback(this);
			client.subscribe("fromBroker/" + name);
			client.subscribe("stats/call");
			
			MqttMessage stats = new MqttMessage();
			stats.setPayload(getStat().getBytes());
			stats.setRetained(true);

			MqttTopic pubTopic = client.getTopic("stats/" + name);
			
			Thread.sleep(latency);
			pubTopic.publish(stats);
								
		} catch (Exception e) {
			e.printStackTrace();
	    }
	}
	
	private String getStat() {

		cpu = Runtime.getRuntime().availableProcessors();
		ram = Runtime.getRuntime().totalMemory();
		
		return "online-"+cpu+"-"+ram+"-"+zone+"-"+direction+"-"+latency+"-"+battery;
	}
	
	public void disconnect() {
		try {
			client.disconnect();
		} catch (MqttException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	

	@Override
	public void connectionLost(Throwable cause) {
		// TODO Auto-generated method stub
	
	}
	
	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		Thread.sleep(latency);
		
		System.out.println("host received");
		if (topic.equals("stats/call")) {
			
			MqttMessage stats = new MqttMessage();
			stats.setPayload(getStat().getBytes());
			stats.setRetained(true);

			MqttTopic pubTopic = client.getTopic("stats/" + name);
			
			Thread.sleep(latency);
			pubTopic.publish(stats);
		}
		
		else if (topic.equals("fromBroker/" + name)) {
			System.out.print("host now publishing");
			System.out.print("");
			
			String jsonMsg = new String(message.getPayload(), "US-ASCII");
			
			PrintWriter writer = new PrintWriter("event.json", "US-ASCII");
			writer.println(jsonMsg);
			writer.close();
			
			JSONObject obj = new JSONObject(jsonMsg);
			
			//System.out.println("reading json");
			
			
			// TODO maybe is wrong
			String id = (obj.getJSONObject("id")).toString();
			
			
			JSONArray codeArray = new JSONArray();
			codeArray = (obj.getJSONObject("execution_script")).getJSONArray("code");
			
			//System.out.println("execution_script: " + (obj.getJSONObject("execution_script")).getJSONArray("code"));
						
			
			
			writer = new PrintWriter("script.py", "US-ASCII");
		    for (int i = 0, size = codeArray.length(); i < size; i++) {
				String str = codeArray.getString(i);
				writer.println(str);
			}
			writer.close();

			ProcessBuilder pb = new ProcessBuilder("python","script.py");
			Process p = pb.start();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String ret = in.readLine();
			
			MqttMessage answer = new MqttMessage();
			String tmp = new String();
			tmp = name + "~" + id + "~" + ret;
			answer.setPayload(tmp.getBytes());
						
			MqttTopic pubTopic = client.getTopic("toBroker/taskResults");
			
			Thread.sleep(latency);
			pubTopic.publish(answer);
			
			//client.publish("toBroker/taskResults", answer);
		}
	}
	
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
	
	}

}