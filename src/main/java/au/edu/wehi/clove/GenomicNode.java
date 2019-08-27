package au.edu.wehi.clove;

import java.util.ArrayList;
import java.util.HashSet;




public class GenomicNode implements Comparable<GenomicNode>{
	private static int global_node_id_counter = 0;
	public static int global_event_merge_counter = 0;

	private GenomicCoordinate start, end;
	private ArrayList<Event> events;
	private Integer id;
	
	public GenomicNode(GenomicCoordinate coord){
		this.start = coord;
		this.end  = coord;
		this.id = global_node_id_counter++;
	}
	
	public GenomicNode(GenomicCoordinate coord, Event e){
		this.start = coord;
		this.end  = coord;
		this.events = new ArrayList<Event>();
		events.add(e);
		this.id = global_node_id_counter++;
	}

	public GenomicCoordinate getStart() {
		return start;
	}

	public GenomicCoordinate getEnd() {
		return end;
	}

	public ArrayList<Event> getEvents() {
		return events;
	}

	public void addEvent(Event e) {
		this.events.add(e);
	}

	@Override
	public int compareTo(GenomicNode other) {
		//this compare method never spits out equal, so that the adding to TreeSets 
		//would not ignore them (unless it's actually identical).
		int compare = this.start.compareTo(other.start);
		if(compare == 0)
			return this.id.compareTo(other.id);
		else
			return compare;
	}
	
	/*
	 * Assimilates the members and end coordinate of other node
	 * Assumes that the other node is downstream of this (on same chr)
	 * Also assumes that other node has only one event
	 */

	//TODO: Why would we only merge nodes with event size 1 ?
	//Lets try 2 lateron
	public void mergeWithNode(GenomicNode other){

		if(!this.start.onSameChromosome(other.start) || this.end.compareTo(other.start) >0 && other.getEvents().size()>1){
			//System.err.println("Event size : " + other.getEvents().size());
			//System.err.println("Assumptions violated in mergeWithNode!");
			return;
		}
		//change end coordinate of node interval
		this.end = other.end;
		//add event if necessary
		if(other.getEvents().size() == 0)
			return;

		for(Event e: other.getEvents() ) {
			//Event e = other.getEvents().get(0);
			if (!this.getEvents().contains(e)) {
				this.addEvent(e);
			}
			//adjust pointers to new node where applicable
			if (e.getC1() == other.start) {
				e.setNode(this, true);
			}
			if (e.getC2() == other.start) {
				e.setNode(this, false);
			}
			if (e.getC2() == other.end) {
				e.setNode(this, false);
			}
		}
	}

	public void checkForRedundantEvents(int maxDistanceForNodes){
		Event e1=null, e2=null;
		HashSet<Event> redundantEvents = new HashSet<Event>();

		//System.out.println("Start of this node is at: " + this.getStart().toString());

		try {
			for (int i = 0; i < this.getEvents().size(); i++) {
				e1 = this.getEvents().get(i);
				if (redundantEvents.contains(e1)) continue;
				for (int j = i + 1; j < events.size(); j++) {
					e2 = events.get(j);

					if (redundantEvents.contains(e2)) continue;

					if ( e2.otherNodes(this).size() < 2 &&
							e1.otherNodes(this).get(0).getStart().distanceTo(e2.otherNodes(this).get(0).getStart()) < maxDistanceForNodes
							&& e1.sameTypes(e2) ) {
						//System.out.println("Redundant events identified: "+e1+" "+e2);
						e1.setId(e1.getId() + "-" + e2.getId());
						e1.addCaller(e2.getCalledBy());
						e1.increaseCalls(e2.getCalledTimes());

						Double qual1, qual2;
						try{
							qual1 = Double.parseDouble(e1.getQual());
						} catch (NumberFormatException ex){
							qual1 = 0.0;
						}

						try{
							qual2 = Double.parseDouble(e2.getQual());
						} catch (NumberFormatException ex){
							qual2 = 0.0;
						}
						e1.setQual(String.format( "%.2f", Double.max(qual1,qual2)));
						redundantEvents.add(e2);
						global_event_merge_counter++;
					}
				}
			}
			for (Event e : redundantEvents) {
				e.otherNodes(this).get(0).getEvents().remove(e);
			}
			this.events.removeAll(redundantEvents);

		} catch (Exception e){
			System.out.println(e1.toString() + " + " + e2.toString() + " Message: " + e.getMessage() );
			System.exit(2);
		}
	}
	
	public Event existsDeletionEventTo(GenomicNode other){
		for(Event e: this.events){
			if(e.otherNodes(this).get(0) == other && e.getType()==EVENT_TYPE.DEL)
				return e;
		}
		return null;
	}
}
