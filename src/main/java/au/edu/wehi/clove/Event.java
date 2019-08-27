package au.edu.wehi.clove;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


enum EVENT_TYPE {
	// simple event types
	INS, INV1, INV2, DEL, TAN, INVTX1, INVTX2, ITX1, ITX2, BE1, BE2, XXX,
	// inversion
	COMPLEX_INVERSION,
	// duplication events
	COMPLEX_INVERTED_DUPLICATION, COMPLEX_DUPLICATION,
	COMPLEX_INTERCHROMOSOMAL_DUPLICATION, COMPLEX_INTERCHROMOSOMAL_INVERTED_DUPLICATION,
	// translocation events
	COMPLEX_TRANSLOCATION, COMPLEX_INVERTED_TRANSLOCATION, COMPLEX_INTERCHROMOSOMAL_TRANSLOCATION,
	COMPLEX_INTERCHROMOSOMAL_INVERTED_TRANSLOCATION,
	// insertion events
	COMPLEX_BIG_INSERTION,
	//knockout events
	COMPLEX_REPLACED_DELETION, COMPLEX_INVERTED_REPLACED_DELETION,
	// vector integrations
	VECTOR_PARTS}

public class Event {

	private GenomicCoordinate c1, c2;
	private EVENT_TYPE type;
	private ArrayList<GenomicNode> myNodes;
	private String additionalInformation;
	private GenomicCoordinate coord;
	private String id;
	private String ref;
	private String alt;
	private String qual;
	private String filter;
	private String info;
	private HashSet<Clove.SV_ALGORITHM> calledBy;
	private int calledTimes;
		
	public Event(GenomicCoordinate c1, GenomicCoordinate c2, EVENT_TYPE type){

		if(c1.compareTo(c2) <= 0){
			this.c1 = c1;
			this.c2 = c2;
		} else {
			this.c1 = c2;
			this.c2 = c1;
		}

		this.type = type;
		myNodes = new ArrayList<GenomicNode>();
		this.info="";
		this.calledBy = new HashSet<Clove.SV_ALGORITHM>();
		this.calledTimes = 0;
	}
	
	public Event(GenomicCoordinate c1, GenomicCoordinate c2, EVENT_TYPE type, String additionalInformation){
		this(c1,c2,type);
		this.additionalInformation = additionalInformation;
	}

	/*Create event with VCF Info*/
	public Event(GenomicCoordinate c1, GenomicCoordinate c2, EVENT_TYPE type, String id, String ref, String alt, String qual, String filter, String info, HashSet<Clove.SV_ALGORITHM> calledBy, int calledTimes){
		if(c1.compareTo(c2) < 0){
			this.c1 = c1;
			this.c2 = c2;
		} else {
			this.c1 = c2;
			this.c2 = c1;
		}
		this.coord = c1;
		this.type = type;
		myNodes = new ArrayList<GenomicNode>();
		this.id=id;
		this.ref=ref;
		this.alt=alt;
		this.qual=qual;
		this.filter=filter;
		this.info=info;
		this.calledBy = calledBy;
		this.calledTimes = calledTimes;
	}
	
	/*
	 * Static function to handle the particularities of Socrates output, and convert it into a general
	 * purpose Event.
	 */
	public static Event createNewEventFromSocratesOutput(String output){
		String line = output.replace("\t\t", "\tX\t");
		StringTokenizer t = new StringTokenizer(line);
		String chr1 = t.nextToken(":");
		int p1 = Integer.parseInt(t.nextToken(":\t"));
		String o1 = t.nextToken("\t");
		t.nextToken("\t");
		String chr2 = t.nextToken("\t:");
		int p2 = Integer.parseInt(t.nextToken("\t:"));
		String o2 = t.nextToken("\t");
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifySocratesBreakpoint(c1, o1, c2, o2);
		
		//look for additional information at the end of the call
		int i = 0;
		while(i<19 && t.hasMoreTokens()){
			i++;
			t.nextToken();
		}
		String additionalComments = (t.hasMoreTokens()? t.nextToken() : "");
		if(additionalComments.startsWith("Inserted sequence")){
			String insert = additionalComments.substring("Inserted sequence: ".length());
			return new Event(c1, c2, type, insert);
		}
		
		return new Event(c1, c2, type);
	}
	/*
	 * Static function to handle the particularities of Socrates output, and convert it into a general
	 * purpose Event.
	 */
	public static Event createNewEventFromSocratesOutputLatest(String output, int count){
		String line = output.replace("\t\t", "\tX\t");
		StringTokenizer t = new StringTokenizer(line);
		String chr1 = t.nextToken(":");
		int p1 = Integer.parseInt(t.nextToken(":\t"));
		String o1 = t.nextToken("\t");
		t.nextToken("\t");
		String chr2 = t.nextToken("\t:");
		int p2 = Integer.parseInt(t.nextToken("\t:"));
		String o2 = t.nextToken("\t");
		
		String id="SOC"+Integer.toString(count);
		String ref=".";
		String qual=".";
		String filter="PASS";
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifySocratesBreakpoint(c1, o1, c2, o2);
		
		//look for additional information at the end of the call
		int i = 0;
		while(i<19 && t.hasMoreTokens()){
			i++;
			t.nextToken();
		}
		String additionalComments = (t.hasMoreTokens()? t.nextToken() : "");
		if(additionalComments.startsWith("Inserted sequence")){
			String insert = additionalComments.substring("Inserted sequence: ".length());
			return new Event(c1, c2, type, insert);
		}
		
		String alt= getAltVCF(type);
		String info="SVTYPE="+alt.substring(1, 4)+";CHR2="+chr2+";END="+p2;
				
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.SOCRATES);}}, 1);
	}
	/*
	 * Function to classify a line of Socrates output into a genomic event type.
	 * The distinctions between INV1/2 etc are arbitrary, and have to be consistent across all the inputs.
	 */
	private static EVENT_TYPE classifySocratesBreakpoint(GenomicCoordinate c1, String o1, GenomicCoordinate c2, String o2) {

		if(c1.compareTo(c2)>0){
			GenomicCoordinate tmp;
			String tmp_o;
			tmp = new GenomicCoordinate(c1.getChr(), c1.getPos());
			c1.setChr(c2.getChr());
			c1.setPos(c2.getPos());
			c2.setChr(tmp.getChr());
			c2.setPos(tmp.getPos());
			tmp_o = o1;
			o1 = o2;
			o2 = tmp_o;
		}

		if (o2.equals("")) {
			if (o1.equals("+")) {
				return EVENT_TYPE.BE1;
			} else {
				return EVENT_TYPE.BE2;
			}
		} else if (o1.equals(o2)) {
			if (c1.onSameChromosome(c2)) {
				if (o1.equals("+"))
					return EVENT_TYPE.INV1;
				else
					return EVENT_TYPE.INV2;
			} else {
				if (o1.equals("+"))
					return EVENT_TYPE.INVTX1;
				else
					return EVENT_TYPE.INVTX2;
			}
		} else {
			if (c1.onSameChromosome(c2)) {
				if (o1.equals("+") && c1.compareTo(c2) < 0 || o1.equals("-") && c1.compareTo(c2) >= 0) {
					return EVENT_TYPE.DEL;
				} else {
					return EVENT_TYPE.TAN;
				}
			} else {
				if (o1.equals("+") && c1.compareTo(c2) < 0 || o1.equals("-") && c1.compareTo(c2) >= 0) {
					return EVENT_TYPE.ITX1;
				} else {
					return EVENT_TYPE.ITX2;
				}
			}
		}
	}

	/*
	 * Static function to handle the particularities of Delly output, and convert it into a general
	 * purpose Event.
	 */
	public static Event createNewEventFromDellyOutput(String output){
		StringTokenizer t = new StringTokenizer(output, "\t:");
		String chr1 = t.nextToken();
		String chr2 = chr1;
		int p1 = Integer.parseInt(t.nextToken());
		int p2 = Integer.parseInt(t.nextToken());
		t.nextToken();
		t.nextToken();
		t.nextToken();
		String typeT;
		String tempT = t.nextToken(); 
		typeT = tempT.substring(1,tempT.indexOf("_"));
		if (typeT.equals("Inversion")){
			typeT = tempT.substring(1,(tempT.indexOf("_")+2));
		}
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifyDellyBreakpoint(c1, c2, typeT);
		
		//System.out.println(chr1 +"\t"+ p1 +"\t"+ p2 +"\t" + type +"\t"+ typeT);
		
		return new Event(c1, c2, type);
	}

	/*
	 * Function to classify a line of Delly output into a genomic event type.
	 * The distinctions between INV1/2 etc are arbitrary, and have to be consistent across all the inputs.
	 * c1 and c2 are always the same chromosome
	 */
	private static EVENT_TYPE classifyDellyBreakpoint(GenomicCoordinate c1, GenomicCoordinate c2, String t){
		if(t.equals("Inversion_0")){
			return EVENT_TYPE.INV1;
		} else if (t.equals("Inversion_1")){
			return EVENT_TYPE.INV2;
		} else if (t.equals("Deletion")){
			return EVENT_TYPE.DEL;
		} else if (t.equals("Duplication")){
			return EVENT_TYPE.TAN;
		} else {
			return EVENT_TYPE.XXX;
		}
	}
	
	
	/*
	 * Static function to handle the particularities of Delly output, and convert it into a general
	 * purpose Event.
	 */
	public static Event createNewEventFromDellyOutputLatest(String output){
		String[] bits = output.split("\t");
		String chr1 = bits[0];
		int p1 = Integer.parseInt(bits[1]);
		String[] moreBits = bits[7].split(";");
		String chr2 = moreBits[5].replace("CHR2=", "");
		int p2 = Integer.parseInt(moreBits[6].replace("END=", ""));
		String o = moreBits[7].replace("CT=", "");
		String o1 = (Integer.parseInt(o.split("to")[0]) == 3? "+" : "-");
		String o2 = (Integer.parseInt(o.split("to")[1]) == 3? "+" : "-");
		
		String id=bits[2];
		String ref=bits[3];
		String alt=bits[4];
		String qual=bits[5];
		String filter=bits[6];
		String info=bits[7];
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifySocratesBreakpoint(c1, o1, c2, o2);
		
		//System.out.println(chr1 +"\t"+ p1 +"\t"+ p2 +"\t" + type +"\t"+ typeT);
		
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.DELLY);}}, 1);
		//return new Event(c1, c2, type);
		
	}
	public static Event createNewEventFromDelly2Output(String output){
		String[] bits = output.split("\t");
		String chr1 = bits[0];
		int p1 = Integer.parseInt(bits[1]);
		String[] moreBits = bits[7].split(";");
		String chr2 = moreBits[3].replace("CHR2=", "");
		int p2 = Integer.parseInt(moreBits[4].replace("END=", ""));
		String o = moreBits[7].replace("CT=", "");
		String o1 = (Integer.parseInt(o.split("to")[0]) == 3? "+" : "-");
		String o2 = (Integer.parseInt(o.split("to")[1]) == 3? "+" : "-");
		
		String id=bits[2];
		String ref=bits[3];
		String alt=bits[4];
		String qual=bits[5];
		String filter=bits[6];
		String info=bits[7];
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifySocratesBreakpoint(c1, o1, c2, o2);
		
		//System.out.println(chr1 +"\t"+ p1 +"\t"+ p2 +"\t" + type +"\t"+ typeT);
		
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.DELLY2);}}, 1);
		//return new Event(c1, c2, type);
		
	}
	/*
	 * Function to classify a line of BedPE into a genomic event type.
	 * The distinctions between INV1/2 etc are arbitrary, and have to be consistent across all the inputs.
	 * c1 and c2 are always the same chromosome
	 */
	private static EVENT_TYPE classifyDellyBreakpointLatest(GenomicCoordinate c1, GenomicCoordinate c2){
		return null;
	}
	
	/*
	 * Static function to handle the particularities of MetaSV output, and convert it into a general
	 * purpose Event.
	 */
	public static Event createNewEventFromMetaSVOutput(String output){
		String[] bits = output.split("\t");
		String chr1 = bits[0];
		int p1 = Integer.parseInt(bits[1]);
		String[] moreBits = bits[7].split(";");
		String chr2 = chr1;
		int p2 = 0;
		String o1 = null;
		String o2 = null;
		for(String s: moreBits){
			if(s.startsWith("CHR2"))
				chr2 = s.replace("CHR2=", "");
			else if(s.startsWith("END="))
				p2 = Integer.parseInt(s.replace("END=", ""));
			else if(s.startsWith("SVLEN="))
				p2 = p1 + Integer.parseInt(s.replace("SVLEN=", ""));
			else if(s.startsWith("BD_ORI1")){
				String o = s.replace("BD_ORI1=", "");
				int fwd = Integer.parseInt(o.split("[+-]")[0]);
				int rev = Integer.parseInt(o.split("[+-]")[1]);
				o1 = (fwd>rev ? "+" : "-");}
			
			else if(s.startsWith("BD_ORI2")){
				String o = s.replace("BD_ORI2=", "");
				int fwd = Integer.parseInt(o.split("[+-]")[0]);
				int rev = Integer.parseInt(o.split("[+-]")[1]);
				o2 = (fwd>rev ? "+" : "-");
			}		
		}
		String id=bits[2];
		String ref=bits[3];
		String alt=bits[4];
		String qual=bits[5];
		String filter=bits[6];
		String info=bits[7];
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifyMetaSVBreakpoint(alt, chr1, chr2, o1, o2);
		
		if(type == EVENT_TYPE.COMPLEX_INVERSION){
			Event e =  new ComplexEvent(c1, c2, type, new Event[] {}, true,null);
			e.setAlt("<CIV>");
			e.setCoord(c1);
			return e;
		}
		
		//System.out.println(chr1 +"\t"+ p1 +"\t"+ p2 +"\t" + type +"\t"+ typeT);
		
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.METASV);}}, 1);
		//return new Event(c1, c2, type);
		
	}
	
	private static EVENT_TYPE classifyMetaSVBreakpoint(String t, String c1, String c2, String o1, String o2){
		if(t.equals("<DEL>")){
			return EVENT_TYPE.DEL;
		} else if (t.equals("<INS>")){
			return EVENT_TYPE.INS;
		} else if (t.equals("<INV>")){
			return EVENT_TYPE.COMPLEX_INVERSION;
		} else if(t.equals("<ITX>")){
			if(o1.equals("+"))
				if(o2.equals("+"))
					return EVENT_TYPE.INV1;
				else
					return EVENT_TYPE.DEL;
			else if(o2.equals("+"))
				return EVENT_TYPE.TAN;
			else
				return EVENT_TYPE.INV2;
		} else if (t.equals("<CTX>")) {
			if(o1.equals(o2)) {
				if(o1.equals("+"))
					return EVENT_TYPE.INVTX1;
				else
					return EVENT_TYPE.INVTX2;
			} else if(o1.equals("+") &&  c1.compareTo(c2) < 0 || o1.equals("-") && c1.compareTo(c2) >= 0){
				return EVENT_TYPE.ITX1;
			} else {
				return EVENT_TYPE.ITX2;
			}
		} else if(t.equals("<DUP>")){
			return EVENT_TYPE.TAN;
		}
		else {
			return EVENT_TYPE.XXX;
		}
	}
	
	public static Event createNewEventFromBEDPE (String output){
		String[] bits = output.split("\t");
		String chr1 = bits[0];
		int p1 = Integer.parseInt(bits[1]);
		String chr2 = bits[3];
		int p2 = Integer.parseInt(bits[4]);
		String o1 = bits[8];
		String o2 = bits[9];
		String qual=bits[7];
		String id=bits[6];
		String ref="";
		String alt="";
		String info= (bits.length>10? bits[10]+";": "");
		String filter = "PASS";
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		EVENT_TYPE type = classifySocratesBreakpoint(c1, o1, c2, o2);
		info+="SVTYPE="+type+";CHR2="+chr2+";END="+p2;
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.BEDPE);}}, 1);
	}
	
	
	public static Event createNewEventFromCrestOutput(String output) {
		StringTokenizer t = new StringTokenizer(output, "\t");
		
		String chr1 = t.nextToken();
		int p1 = Integer.parseInt(t.nextToken());
		String o1 = t.nextToken();
		t.nextToken();
		String chr2 = t.nextToken();
		int p2 = Integer.parseInt(t.nextToken());
		String o2 = t.nextToken();
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		
		t.nextToken();
		EVENT_TYPE type = classifyCrestBreakpoint(t.nextToken(), chr1, chr2, o1, o2);
		
		if(type == EVENT_TYPE.COMPLEX_INVERSION){
			Event e =  new ComplexEvent(c1, c2, type, new Event[] {}, true, null);
			e.setAlt("<CIV>");
			e.setCoord(c1);
			return e;
		}
		return new Event(c1, c2, type);
	}
	
	public static Event createNewEventFromCrestOutputLatest(String output, int count) {
		StringTokenizer t = new StringTokenizer(output, "\t");
		
		String chr1 = t.nextToken();
		int p1 = Integer.parseInt(t.nextToken());
		String o1 = t.nextToken();
		t.nextToken();
		String chr2 = t.nextToken();
		int p2 = Integer.parseInt(t.nextToken());
		String o2 = t.nextToken();
		
		String id="CRT"+Integer.toString(count);
		String ref=".";
		String qual=".";
		String filter="PASS";
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		
		t.nextToken();
		EVENT_TYPE type = classifyCrestBreakpoint(t.nextToken(), chr1, chr2, o1, o2);
		
		if(type == EVENT_TYPE.COMPLEX_INVERSION){
			Event e =  new ComplexEvent(c1, c2, type, new Event[] {}, true, null);
			e.setAlt("<CIV>");
			e.setCoord(c1);
			return e;
		}
		
		String alt= getAltVCF(type);
		String info="SVTYPE="+alt.substring(1, 4)+";CHR2="+chr2+";END="+p2;
		
		return new Event(c1, c2, type, id, ref, alt, qual, filter, info, new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.CREST);}}, 1);
	}
	
	private static EVENT_TYPE classifyCrestBreakpoint(String t, String c1, String c2, String o1, String o2){
		if(t.equals("DEL")){
			return EVENT_TYPE.DEL;
		} else if (t.equals("INS")){
			return EVENT_TYPE.TAN;
		} else if (t.equals("INV")){
			return EVENT_TYPE.COMPLEX_INVERSION;
		} else if(t.equals("ITX")){
			if(o1.equals("+"))
				return EVENT_TYPE.INV1;
			else 
				return EVENT_TYPE.INV2;
		} else if (t.equals("CTX")) {
//			if(o1.equals(o2)) {
//				if(o1.equals("+"))
//					return EVENT_TYPE.INVTX1;
//				else
//					return EVENT_TYPE.INVTX2;
//			} else if(o1.equals("+") &&  c1.compareTo(c2) < 0 || o1.equals("-") && c1.compareTo(c2) >= 0){
//				return EVENT_TYPE.ITX1;
//			} else {
//				return EVENT_TYPE.ITX2;
//			}
			if(o1.equals(o2)) {
				if(o1.equals("+")) {
					if(c1.compareTo(c2) < 0)
						return EVENT_TYPE.ITX1;
					else
						return EVENT_TYPE.ITX2;
				}
				else
					return EVENT_TYPE.XXX;
//			} else if(o1.equals("+") &&  c1.compareTo(c2) < 0 || o1.equals("-") && c1.compareTo(c2) >= 0){
			} else if(c1.compareTo(c2) < 0 && o1.equals("-")){
				return EVENT_TYPE.INVTX2;
			} else if(c1.compareTo(c2) < 0 && o1.equals("+")){
				return EVENT_TYPE.INVTX1;
			}
			else if (c1.compareTo(c2) >= 0 && o1.equals("+")){
				return EVENT_TYPE.INVTX1;
			} else {
				return EVENT_TYPE.INVTX2;
			}
		} else {
			return EVENT_TYPE.XXX;
		}
	}


	public static Event createNewEventFromGustafOutput(String output) {
		StringTokenizer t = new StringTokenizer(output, "\t");
		
		String chr1 = t.nextToken();
		t.nextToken();
		EVENT_TYPE type = classifyGustafBreakpoint(t.nextToken());
		int p1 = Integer.parseInt(t.nextToken());
		int p2 = Integer.parseInt(t.nextToken());
		t.nextToken();
		String o = t.nextToken();
		if(type==EVENT_TYPE.INV1 && o.equals("-"))
			type = EVENT_TYPE.INV2;
		t.nextToken();
		String info = t.nextToken();
		StringTokenizer i = new StringTokenizer(info, "=;");
		i.nextToken();
		i.nextToken();
		String d = i.nextToken();
		String chr2;
		if(d.equals("endChr")){
			chr2 = i.nextToken();
			i.nextToken();
			p2 = Integer.parseInt(i.nextToken());
		} else if (d.equals("size")) {
			chr2 = chr1;
		} else {
			System.err.println("Confusion in the Gustaf camp!");
			chr2=null;
		}
		
		GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
		GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
		
		return new Event(c1, c2, type);
	}
	private static EVENT_TYPE classifyGustafBreakpoint(String t){
		if(t.equals("deletion")){
			return EVENT_TYPE.DEL;
		} else if (t.equals("duplication")){
			return EVENT_TYPE.TAN;
		} else if (t.equals("inversion")){
			return EVENT_TYPE.INV1;
		} else if(t.equals("ITX")){
			return EVENT_TYPE.INV1;
		} else if (t.equals("insertion")) {
			return EVENT_TYPE.INS;
		} else {
			return EVENT_TYPE.XXX;
		}
	}
	
	
    
    /*************************************/
    /* GRIDSS Output                    */
    /*****************************************************************************************************************/

    public static Event createNewEventFromGRIDSSOutput(String output) {

    	//System.out.println(output);

        Pattern pattern;
        Matcher matcher;

        String[] bits = output.split("\t");

//        System.out.println(output + "\n" + bits.length);
//        if(bits.length != 7){
//        	System.err.println("Corrupted VCF File");
//        	System.exit(1 );
//        	return null;
//		}

        String chr1 = bits[0], chr2 = "";
        String orientation1 = "", orientation2 = "";
        int p1 = Integer.parseInt(bits[1]), p2 = -1;
        String alt = bits[4];
        String[] result = Event.classifyAltGridssLumpy(alt);

		chr2 = result[0];
		orientation1 = result[2];
		orientation2 = result[3];

		if(!result[1].isEmpty()) {
			p2 = Integer.parseInt(result[1]);
		}

		String info="";

        GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
        GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
        EVENT_TYPE type = EVENT_TYPE.XXX;
        if (!orientation1.equals("")) {
//        	 System.out.println(c1.toString() + " " + orientation1 + " " + c2.toString());
             type = Event.classifySocratesBreakpoint(c1, orientation1, c2, orientation2);
//			 System.out.println(c1.toString() + " " + orientation1 + " " +  " " + c2.toString() + type);

			info="SVTYPE="+type+";CHR2="+chr2+";END="+p2;
		}

        pattern = Pattern.compile("SVTYPE=(.+?)");
        matcher = pattern.matcher(bits[7]);
        matcher.find();

        String id = bits[2];
        String ref = bits[3];
        String qual = bits[5];
        String filter = bits[6];
//        if (matcher.groupCount() != 0){
//        	System.out.println(matcher.toString());
//            info = matcher.group(1);
//        }

        return new Event(
                c1, c2, type, id, ref, alt, qual, filter, info,
                new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.GRIDSS);}}, 1
        );
    }

    
    private static String[] classifyAltGridssLumpy(String alt) {

        /* 0: chr2
         * 1: p2
         * 2: orientation 1
         * 3: orientation 2
         * */
        String[] result = new String[4];

        if(alt.contains("]")) {

            String[] item = alt.split("]");
            if (item.length == 3){
                // ALT: ]p]t --> len 3
                result[0] = item[1].split(":")[0];
                result[1] = item[1].split(":")[1];

                result[2] = "-";
                result[3] = "+";

            } else {
                // ALT: t]p] --> len 2
                result[0] = item[1].split(":")[0];
                result[1] = item[1].split(":")[1];

                result[2] = "+";
                result[3] = "+";
            }

        } else if(alt.contains("[")){

            String[] item = alt.split("\\[");
            if (item.length == 3) {
                // ALT: [p[t --> become 3
                result[0] = item[1].split(":")[0];
                result[1] = item[1].split(":")[1];

                result[2] = "-";
                result[3] = "-";

            } else {
                // ALT: t[p[ --> become 2
                result[0] = item[1].split(":")[0];
                result[1] = item[1].split(":")[1];

                result[2] = "+";
                result[3] = "-";
            }
        } else if(alt.contains(".")) {
			result[0] = "";
			result[1] = "";

			if(alt.startsWith(".")) {
				result[2] = "-";
			} else {
				result[2] = "+";
			}

			result[3] = "";

		} else {
			// leave empty
			System.err.println("Found unknown ALT in GRIDSS input.");
		}

        return result;
    }

    /*************************************/
    /* LUMPY Output                      */
    /*****************************************************************************************************************/
    public static Event createNewEventFromLUMPYOutput(String output) {

        Pattern pattern; Matcher matcher;
        String orientation1 = "", orientation2 = "";
        String[] bits = output.split("\t");
        String chr1 = bits[0], chr2 = "";
        int p1 = Integer.parseInt(bits[1]), p2 = -1;

        String id = bits[2];
        String ref = bits[3];
        String alt = bits[4];
        String qual = bits[5];
        String filter = bits[6];
        String info = bits[7];

        /* get strands */
        pattern = Pattern.compile("STRANDS=(.+?);");
        matcher = pattern.matcher(bits[7]);
        String strands = "";

        if (matcher.find()){
            strands = matcher.group(1);
        }

        if (alt.equals("<INV>")) {
            int posStrand = 0, negStrand = 0;
            
            /* Get END */
            pattern = Pattern.compile("END=(.+?);");
            matcher = pattern.matcher(bits[7]);

            chr2 = chr1;
            if (matcher.find()){
                p2 = Integer.parseInt(matcher.group(1));
            }
            
            GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
            GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
    		Event e =  new ComplexEvent(c1, c2, EVENT_TYPE.COMPLEX_INVERSION, new Event[] {}, true,null);
    		e.setAlt("<CIV>");
    		e.setCoord(c1);
    			return e;
    		

        } else if (alt.equals("<DEL>") || alt.equals("<DUP>")) {

            String orientation = "";
            /* get orientation */
            pattern = Pattern.compile("STRANDS=(.+?):");
            matcher = pattern.matcher(bits[7]);

            if (matcher.find()){
                orientation = matcher.group(1);
            }

            orientation1 = orientation.substring(0,1);
            orientation2 = orientation.substring(1,1);

            /* Get END */
            pattern = Pattern.compile("END=(.+?);");
            matcher = pattern.matcher(bits[7]);

            chr2 = chr1;
            if (matcher.find()){
                p2 = Integer.parseInt(matcher.group(1));
            }

        } else {
            String[] result = Event.classifyAltGridssLumpy(alt);
            chr2 = result[0];
            p2 = Integer.parseInt(result[1]);
            orientation1 = result[2];
            orientation2 = result[3];
        }




        GenomicCoordinate c1 = new GenomicCoordinate(chr1, p1);
        GenomicCoordinate c2 = new GenomicCoordinate(chr2, p2);
        EVENT_TYPE type = Event.classifySocratesBreakpoint(c1, orientation1, c2, orientation2);

        /*return new Event(c1, c2, type);*/
        return new Event(
                c1, c2, type, id, ref, alt, qual, filter, info,
                new HashSet<Clove.SV_ALGORITHM>() {{add(Clove.SV_ALGORITHM.LUMPY);}}, 1
        );
    }

	
	
	public GenomicCoordinate getC1() {
		return c1;
	}

	public GenomicCoordinate getC2() {
		return c2;
	}

	public EVENT_TYPE getType() {
		return type;
	}
	
	public void setNode(GenomicNode n, boolean firstCoordinate){
		if(firstCoordinate) {
			if (myNodes.size() > 0) {
				myNodes.set(0, n);
			} else {
				myNodes.add(n);
			}
		} else {
			if (myNodes.size() > 1 ) {
				myNodes.set(1, n);
			} else{
				if( myNodes.size() == 0 ) {
					myNodes.add(null);
				}
				myNodes.add(n);
			}

		}
	}

	public void setNodes( ArrayList<GenomicNode> nodes){
		myNodes = nodes;
	}


	public GenomicNode getNode(boolean firstCoordinate){
    	if(this.getNodes().size() > 1){
			if(firstCoordinate)
				return myNodes.get(0);
			else
				return myNodes.get(1);
		}
    	return null;
	}

	public ArrayList<GenomicNode> getNodes(){
    	return myNodes;
	}
	
	
	public static boolean sameNodeSets(Event e1, Event e2){
		if(e1.getNodes().containsAll(e2.getNodes()))
			return true;
		return false;		
	}
	
	@Override
	public String toString() {
		if(c1.onSameChromosome(c2)){
			return this.getId()+" "+c1.getChr()+":"+c1.getPos()+"-"+c2.getPos()+" "+type;
		} else {
			return this.getId()+" "+c1+"<->"+c2+" "+type;
		}
	}
	
	public ArrayList<GenomicNode> otherNodes(GenomicNode node){

		ArrayList<GenomicNode> others = (ArrayList<GenomicNode>) myNodes.clone();

    	if(others.size() > 1) {
			Integer ind = others.indexOf(node);

			if(!ind.equals(null)){
				others.remove(node);
				return others;
			}

			System.err.println("otherNode: query node is not associated with Event! \n" + myNodes.size());
			return null;
		}
		//System.out.println("otherNode: Event has only one node");
		return null;
	}
	
	public int size() {
		return c1.distanceTo(c2);
	}
	
	public void processAdditionalInformation(){
		if(this.additionalInformation!= null && this.additionalInformation.matches("[ACGT]+") && myNodes.get(0) == myNodes.get(1) ){
			this.type = EVENT_TYPE.INS;
		}
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getQual() {
		return qual;
	}

	public void setQual(String qual) {
		this.qual = qual;
	}

	public String getAlt() {
		return alt;
	}

	public void setAlt(String alt) {
		this.alt = alt;
	}

	public String getFilter() {
		return (filter==null? ".":filter);
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public String getRef() {
		return ref;
	}

	public void setRef(String ref) {
		this.ref = ref;
	}

	public String getInfo() {
		return info;
	}

	public void setInfo(String info) {
		this.info = info;
	}

	public GenomicCoordinate getCoord() {
		return coord;
	}

	public void setCoord(GenomicCoordinate newCoord) {
		coord = newCoord;
	}

	public static String getAltVCF(EVENT_TYPE type){
		if(type.equals(EVENT_TYPE.DEL)){
			return "<DEL>";
		} else if(type.equals(EVENT_TYPE.INS)){
			return "<INS>";
		} else if(type.equals(EVENT_TYPE.TAN)){
			return "<TAN>";
		} else if(type.equals(EVENT_TYPE.INV1)){
			return "<INV>";
		} else if(type.equals(EVENT_TYPE.INV2)){
			return "<INV>";
		} else if(type.equals(EVENT_TYPE.INVTX1)){
			return "<ITX>";
		} else if(type.equals(EVENT_TYPE.INVTX2)){
			return "<ITX>";
		} else if(type.equals(EVENT_TYPE.ITX1)){
			return "<ITX>";
		} else if(type.equals(EVENT_TYPE.ITX2)){
			return "<ITX>";
		} else if(type.equals(EVENT_TYPE.BE1)){
			return "<BE>";
		} else if(type.equals(EVENT_TYPE.BE2)){
			return "<BE>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_DUPLICATION)){
			return "<DUP>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INVERTED_TRANSLOCATION)){
			return "<CVT>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INVERTED_DUPLICATION)){
			return "<CVD>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_TRANSLOCATION)){
			return "<TRA>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INVERSION)){
			return "<CIV>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_INVERTED_TRANSLOCATION)){
			return "<IVT>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INTERCHROMOSOMAL_INVERTED_DUPLICATION)){
			return "<IVD>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_BIG_INSERTION)){
			return "<ISB>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_INVERTED_REPLACED_DELETION)){
			return "<RDE>";
		} else if(type.equals(EVENT_TYPE.COMPLEX_REPLACED_DELETION)) {
			return "<IRD>";
		} else if(type.equals(EVENT_TYPE.VECTOR_PARTS)) {
			return "<VEC>";
		} else {
			return "<XXX>";
		} 
	}
	
	public HashSet<Clove.SV_ALGORITHM> getCalledBy() {
		return calledBy;
	}
	public void addCaller(HashSet<Clove.SV_ALGORITHM> caller){
		this.calledBy.addAll(caller);
	}

	public int getCalledTimes() {
		return calledTimes;
	}
	public void increaseCalls(int inc){
		this.calledTimes += inc;
	}

	public String toVcf() {
		return this.getCoord().getChr()+"\t"+this.getCoord().getPos()+"\t"+this.getId()+"\t"
				+this.getRef()+"\t"+this.getAlt()+"\t"+this.getQual()+"\t"+this.getFilter()
				+"\t"+this.getInfo()+";SUPPORT="+this.calledBy.size()+","+this.calledTimes;
	}
	
	public void addFilter( String filter){
    	if(this.getFilter().equals("PASS")){
			this.setFilter(filter);
		} else {
    		this.setFilter(this.getFilter() + ";" + filter);
		}
	}

	public Boolean sameTypes(Event other){
    	if(this.getType().equals(other.getType())){
    		return true;
		}
//    	else if (( this.getType() == EVENT_TYPE.ITX2 && other.getType() == EVENT_TYPE.ITX1) || (
//				this.getType() == EVENT_TYPE.ITX1 && other.getType() == EVENT_TYPE.ITX2)) {
//    		return true;
//		}
    	return false;
	}
}
