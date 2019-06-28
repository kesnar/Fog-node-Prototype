package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;

public class Gateway implements MqttCallback {
	
	MqttClient client;
	
	static String BrokerIp;
	
	public static void main(String[] args) {
		BrokerIp = args[0];
		try {
		BufferedReader buffer = new BufferedReader(new FileReader(args[1]));
		
		StringBuilder msg = new StringBuilder();
		String line = buffer.readLine();

		while (line != null) {
			msg.append(line);
			msg.append("\n");
			line = buffer.readLine();
		}
		
		buffer.close();
		
		new Gateway().connect(msg.toString());
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void connect(String msgContent) {
		try {
			client = new MqttClient("tcp://"+BrokerIp+":1883", "gateway");
			client.connect();
			client.setCallback(this);
			client.subscribe("fromBroker/results");
			
			MqttMessage message = new MqttMessage();
			message.setPayload(msgContent.getBytes());

			MqttTopic pubTopic = client.getTopic("toBroker/toExec");
			pubTopic.publish(message);
			
			//client.publish("toBroker/toExec", message);
			
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
		if (topic.equals("fromBroker/results")) {
			System.out.println(message);
		}
	}
	
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
	
	}

}