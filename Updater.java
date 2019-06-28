package client;

public class Updater extends Thread {

	Broker parent;
	
	public Updater (Broker p){
		parent = p;
	}
	
	@Override
	public void run() {
		parent.callForStats();
	}

}