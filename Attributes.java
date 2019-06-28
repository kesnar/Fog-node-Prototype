package client;

public class Attributes {
	
	public int cpu;
	public long ram;
	public int zone;
	public int direction;
	public int location;
	public long latency; 
	public int battery;
	public int noTasks;
	public int trust;
	
	public Attributes(int c, long r, int z, int d, long l, int b, int n, int t) {
		cpu = c;
		ram = r;
		zone = z;
		direction = d;
		location = z*d;
		latency = l; 
		battery = b;
		noTasks = n;
		trust = t;
	}
}
