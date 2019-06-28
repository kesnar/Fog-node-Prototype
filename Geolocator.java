package client;

import java.util.Random;

public class Geolocator extends Thread {
	int zone; 
	int direction; //0 to inside, 1 to outside
	boolean inBounds = true;
	Host parent;
	
	Random rand = new Random();
	
	public Geolocator (Host p){
		parent = p;
	}
	
	@Override
	public void run() {
		zone = rand.nextInt(10);
		direction = rand.nextInt(2);
		if (direction == 0) {
			direction = -1;
		}
		
		parent.setlocation(zone, direction);
		
		while (inBounds) {
			try {
				Thread.sleep(rand.nextInt(10));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (direction == 1) {
				if (zone > 0) {
					zone--;
				}
				else{
					zone = 1;
					direction = -1;
				}
			} else {
				if (zone < 9) {
					zone++;
				}
				else{
					inBounds = false;
				}
			}
			parent.setlocation(zone, direction);
			
		}
		parent.disconnect();
		
	
	}

}