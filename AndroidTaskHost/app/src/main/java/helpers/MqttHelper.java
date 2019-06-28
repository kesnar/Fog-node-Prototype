package helpers;

import android.content.Context;
import android.location.Location;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import com.kereji.fog.taskhost.MainActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.liquidplayer.javascript.JSContext;
import org.liquidplayer.javascript.JSValue;
import org.w3c.dom.Text;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import android.content.IntentFilter;
import android.content.Intent;
import android.os.BatteryManager;
import java.net.InetAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;


public class MqttHelper implements MqttCallbackExtended {
    public MqttAndroidClient mqttAndroidClient;

    String serverUri = "192.168.1.10";

    String clientId = "ExampleAndroidClientOfKesnar";
    ArrayList<String> subList = new ArrayList<String>();

    static int zone = 1;
    static int direction = 1;

    float brokerLat;
    float brokerLng;
    MainActivity parent;

    static Location currentLocation = new Location("host");

    static Context appContext;
    public MqttHelper(Context context, String brokerIP, String name, ArrayList<String> sList, MainActivity p) {

        //1 Get brokerIP, name and subscription list from MainActivity
        parent = p;
        appContext = context;

        serverUri = brokerIP ;
        clientId = name;
        subList.clear();
        subList.addAll(sList);

        mqttAndroidClient = new MqttAndroidClient(context, "tcp://" + serverUri +":1883", clientId);
        mqttAndroidClient.setCallback(this);
        /*mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.w("mqtt", s);
            }

            @Override
            public void connectionLost(Throwable throwable) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                Log.w("Mqtt message: ", mqttMessage.toString() + " from " + topic);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });*/
        connect();
    }

    public void setCallback(MqttCallbackExtended callback) {
        mqttAndroidClient.setCallback(callback);
    }

    private void connect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);


        try {

            if (mqttAndroidClient.isConnected()){
                mqttAndroidClient.disconnect();
            }

            //2 Attempt to connect to MQTT Broker
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.w("Mqtt", "Connected to: " + "tcp://" + serverUri +":1883");

                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                    for (String i : subList) {
                        subscribeToTopic(i);
                    }

                    //3 Send first message to broker
                    MqttMessage message = new MqttMessage();
                    message.setPayload(("online~4~128974848~1~-1~1~0").getBytes());

                    try {
                        mqttAndroidClient.publish("stats/" + clientId, message);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }


                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Failed to connect to: " + "tcp://" + serverUri +":1883" + exception.toString());
                }
            });


        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    //4 method to call when we want to subscribe to a topic
    private void subscribeToTopic(final String subTopic) {
        try {
            mqttAndroidClient.subscribe(subTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.w("Mqtt", "Subscribed! to " + subTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.w("Mqtt", "Subscribed fail! to " + subTopic);
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }


    @Override
    public void connectComplete(boolean b, String s) {
        Log.w("mqtt", s);
        parent.setText(s);
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        Log.w("Mqtt message: ", mqttMessage.toString() + " from " + topic);
        parent.setText(mqttMessage.toString());

        if (topic.equals("stats/broker")) {
            //5 Get broker location and create geofences
            Log.w("Mqtt message: ", "Broker stats received");
            String[] subl = (mqttMessage.toString()).split("~");
            brokerLat = Float.parseFloat(subl[0]);
            brokerLng = Float.parseFloat(subl[1]);
            parent.setGeoFence(brokerLat, brokerLng);

            Location brokerLocation = new Location("broker");
            setZone((int) currentLocation.distanceTo(brokerLocation) /50);
            //Set at the worst place until it moves in geofence area (in case distance from broker > 50 * 10)
            setDirection(-1);

            Log.w("Location", "distance is " + currentLocation.distanceTo(brokerLocation));


        }
        if (topic.equals("fromBroker/" + clientId)) {
            //6 if message is a task to execute
            System.out.println("host now publishing");

            //7 Note start time of execution
            long startTime = System.currentTimeMillis();

            String jsonMsg = new String(mqttMessage.getPayload());

            //8 Parse JSON file
            JSONObject obj = new JSONObject();
            JSONParser parser = new JSONParser();
            obj = (JSONObject) parser.parse(jsonMsg);

            //9 Get id
            String id = (String) obj.get("id");
            Log.w("JSONparse", id);


            //10 get Javascript code
            JSONObject execution_script = new JSONObject();
            execution_script = (JSONObject) obj.get("execution_script");
            JSONArray code = new JSONArray();
            code = (JSONArray) execution_script.get("codeJs");

            //System.out.println(codeArray.get(0));

            //System.out.println("1st phase!~~~");
            //long startTime = System.currentTimeMillis();

            StringBuilder msg = new StringBuilder();
            Iterator<String> it = code.iterator();
            while (it.hasNext()) {
                msg.append(it.next());
                msg.append("\n");
            }
            msg.append("data = " + jsonMsg);
            msg.append("a = escr(data);");
            Log.w("JSONparse", msg.toString());

            //11 run javascript code and get return value
            JSContext context = new JSContext();
            //context.evaluateScript("data = "+jsonMsg);
            context.evaluateScript(msg.toString());
            JSValue newAValue = context.property("a");
            Log.w("JS", "a is "+ newAValue.toString());

            String ret = newAValue.toString();
            MqttMessage answer = new MqttMessage();
            answer.setRetained(false);
            String tmp = new String();
            tmp = clientId + "~" + id + "~" + ret.toString();
            System.out.println(tmp);
            answer.setPayload(tmp.getBytes());

            //12 Publish self-name, id of the task executed and its result
            try {
                Log.w("MQTTpub", "forwarding results");
                mqttAndroidClient.publish("toBroker/taskResults", answer);
            } catch (MqttException e) {
                e.printStackTrace();
            }

            long endTime = System.currentTimeMillis();

            System.out.println("~~~~~~~~~");
            System.out.println(endTime - startTime);
            System.out.println("~~~~~~~~~");

            //client.publish("toBroker/taskResults", answer);


        }

        if (topic.equals("stats/call")) {
            //13 if the message is a call for stats get stats and publish to personal topic
            Log.w("Mqtt message: ", "called for stats");

            String tmp = getStat();
            Log.w("Mqtt message: ", "cpu + ram + latency + battery: " +tmp);

            parent.setText("cpu + ram + battery + latency: " +tmp);

            MqttMessage message = new MqttMessage();
            message.setPayload(tmp.getBytes());

            try {
                mqttAndroidClient.publish("stats/" +clientId, message);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

   /* private void jsTest(String x) {
        Log.w("JS", "I am in");
        JSContext context = new JSContext();
        context.evaluateScript("a = "+x);
        JSValue newAValue = context.property("a");
        Log.w("JS", "x equals " + df.format(newAValue.toNumber())); // 10.0
        String script =
                "function factorial(x) { var f = 1; for(; x > 1; x--) f *= x; return f; }\n" +
                        "var fact_a = factorial(a);\n";
        context.evaluateScript(script);
        JSValue fact_a = context.property("fact_a");
        Log.w("JS", "the factorial of x is "+df.format(fact_a.toNumber()));
    }*/

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

    public void disconnect() throws MqttException {
        if (mqttAndroidClient.isConnected()){
            mqttAndroidClient.disconnect();
        }
    }


    private String getStat() {
		int cpu = Runtime.getRuntime().availableProcessors();
		long ram = Runtime.getRuntime().totalMemory();

		int battery = getBatteryPercentage();

		String latency = getLatency();

		return "online~" + cpu + "~" + ram + "~" + zone + "~" + direction + "~" + latency + "~" + battery ;

		//System.out.println("online~"+cpu+"~"+ram+"~"+zone+"~"+direction+"~"+latency+"~"+battery);
		//return "online~"+cpu+"~"+ram+"~"+zone+"~"+direction+"~"+latency+"~"+battery;
    }

    private int getBatteryPercentage() {

        //14 We create an Intent to query for the battey status
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = appContext.registerReceiver(null, iFilter);

        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        //int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

        //float batteryPct = level / (float) scale;

        return level;
    }

    private String getLatency() {

        //15 We run ping program to find latency
        String ret = new String();
        Log.w("ping: ", serverUri);
        try {
            Process p = Runtime.getRuntime().exec("ping -c 1 192.168.1.4");
            p.waitFor();



            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = in.readLine();

			while (line != null) {
                Log.w("ping: ", line);
				if (line.contains("time=")) {
				    ret = line;
				    break;
                }
				line = in.readLine();
			}
			in.close();

            Log.w("ping: ", ret);

			ret = ret.substring(ret.lastIndexOf("=")+1, ret.lastIndexOf("m")-1);



        }
        catch (Exception e) {
            //
        }
        return ret;

    }

    public static void setLocation(Location l) {
        currentLocation = l;
    }

    public static void setDirection(int x) {
        direction = x;
    }

    public static void setZone(int x) {
        zone = x;
    }
}