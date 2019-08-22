package au.edu.wehi.clove;


public class ComplexEvent extends Event{

	private Event[] eventsInvolvedInComplexEvent;
	private GenomicCoordinate insertionPoint;


	public ComplexEvent(GenomicCoordinate c1, GenomicCoordinate c2,
			EVENT_TYPE type, Event[] involvedEvents, Boolean addition, GenomicNode hostingNode) {
		super(c1, c2, type);
		this.eventsInvolvedInComplexEvent = involvedEvents;
		super.setNode(hostingNode, true);
		super.setNode(hostingNode, false);
//		String[] ids = new String[involvedEvents.length];
//		for (int i=0; i<ids.length;i++){ ids[i] = involvedEvents[i].getId();}
//		String newId = String.join("+", ids) ;
//		super.setId(newId);
		for(Event e: involvedEvents){
			super.addCaller(e.getCalledBy());
			super.increaseCalls(e.getCalledTimes());
		}

		this.setCoord(c1);
		this.setAlt(getAltVCF(type));

		String id = "";
		Double qual = 0.0;

		for(Event e: involvedEvents){
			id = id + e.getId() + (addition?"+":"-");
			qual += Double.parseDouble(e.getQual());
		}

		id = id.substring(0, id.length() - 1);
		qual = qual/involvedEvents.length;

		this.setId(id);
		this.setRef(involvedEvents[0].getRef());
		this.setFilter(involvedEvents[0].getFilter());
		this.setQual(String.format( "%.2f", qual ));
	}


	public ComplexEvent(GenomicCoordinate c1, GenomicCoordinate c2,
			EVENT_TYPE type, Event[] involvedEvents, Boolean addition , GenomicNode hostingNode, GenomicCoordinate insertionPoint) {
		this(c1,c2,type,involvedEvents, addition, hostingNode);
		this.insertionPoint = insertionPoint;

//		String[] ids = new String[involvedEvents.length];
//		for (int i=0; i<ids.length;i++){ ids[i] = involvedEvents[i].getId();}
//		String newId = String.join("+", ids) ;
//		super.setId(newId);
	}

	public Event[] getEventsInvolvedInComplexEvent(){
		return this.eventsInvolvedInComplexEvent;
	}
	
	public GenomicCoordinate getInsertionPoint() {
		return this.insertionPoint;
	}
	
	@Override
	public String toString(){
		if(this.getType() == EVENT_TYPE.COMPLEX_TRANSLOCATION || this.getType() == EVENT_TYPE.COMPLEX_DUPLICATION 
				|| this.getType() == EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_DUPLICATION || this.getType() == EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_TRANSLOCATION
				|| this.getType() == EVENT_TYPE.COMPLEX_INVERTED_DUPLICATION || this.getType() == EVENT_TYPE.COMPLEX_INVERTED_TRANSLOCATION
				|| this.getType() == EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_INVERTED_DUPLICATION || this.getType() == EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_INVERTED_TRANSLOCATION){
			return this.getInsertionPoint()+" "+super.toString();
		} else {
			return super.toString();
		}
	}
	
}
