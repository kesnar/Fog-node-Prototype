package client;

public class Coo implements Comparable<Coo> {
	
	String name;
	double rank;

	@Override
	public int compareTo(Coo o) {
		if (rank < o.rank){
			return 1;
		}
		else /*if (rank > o.rank)*/ {
			return -1;
		}
		/*else {
			return 0;
		}*/
	}

}
