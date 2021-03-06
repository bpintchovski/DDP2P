package hds;

import static java.lang.System.out;

import java.io.InputStream;
import java.util.ArrayList;

import util.Util;
import ASN1.ASN1DecoderFail;
import ASN1.ASNObj;
import ASN1.Decoder;
import ASN1.Encoder;
import config.DD;
import table.directory_forwarding_terms;
import util.P2PDDSQLException;
import config.Application;
import data.D_TermsInfo;

class DIR_Payment extends ASNObj{
	float amount;
	String method;
	String details;
	@Override
	public Encoder getEncoder() {
		Encoder enc = new Encoder().initSequence();
		enc.addToSequence(new Encoder(amount));
		enc.addToSequence(new Encoder(method, false));
		enc.addToSequence(new Encoder(details, false));
		return enc;
	}
	@Override
	public DIR_Payment decode(Decoder dec) throws ASN1DecoderFail {
		Decoder dr = dec.getContent();
		amount = dr.getFirstObject(true).getInteger().floatValue();
		method = dr.getFirstObject(true).getString();
		details = dr.getFirstObject(true).getString();
		return this;
	}
}
class DIR_Terms_Preaccepted extends ASNObj{
	public int version=0;
	public String topic;
	public DIR_Payment payment;
	public byte[] services_acceptable;
	public int ad=-1, plaintext=-1;
	
	@Override
	public String toString() {
		return  "version= "+version+
	            "\ntopic= "+topic+
	            "\npayment= "+payment+
	            "\nservice= "+services_acceptable+
	            "\nad= "+ad+
	            "\nplaintext= "+plaintext +"\n";
	}
			
	@Override
	public ASNObj instance() throws CloneNotSupportedException{return (ASNObj) new DIR_Terms_Preaccepted();}
	@Override
	public Encoder getEncoder() {
		Encoder enc = new Encoder().initSequence();
		if(version != 0) enc.addToSequence(new Encoder(version));
		if(topic != null) enc.addToSequence(new Encoder(topic).setASN1Type(DD.TAG_AP1));
		if(payment != null) enc.addToSequence(payment.getEncoder().setASN1Type(DD.TAG_AC2));
		if(services_acceptable != null) enc.addToSequence(new Encoder(services_acceptable).setASN1Type(DD.TAG_AP3));
		if(ad > 0) enc.addToSequence(new Encoder(ad).setASN1Type(DD.TAG_AP4));
		if(plaintext > 0) enc.addToSequence(new Encoder(plaintext).setASN1Type(DD.TAG_AP5));
		return enc.setASN1Type(getASN1Type());
	}
	@Override
	public DIR_Terms_Preaccepted decode(Decoder dec) throws ASN1DecoderFail {
		Decoder d = dec.getContent(); version = 0;
		if(d.getTypeByte()==Encoder.TAG_INTEGER) version = d.getFirstObject(true).getInteger().intValue();
		if(d.getTypeByte()==DD.TAG_AP1) topic = d.getFirstObject(true).getString();
		if(d.getTypeByte()==DD.TAG_AC2) payment = new DIR_Payment().decode(d.getFirstObject(true));
		if(d.getTypeByte()==DD.TAG_AP3) services_acceptable = d.getFirstObject(true).getBytesAnyType();
		if(d.getTypeByte()==DD.TAG_AP4) ad = d.getFirstObject(true).getInteger().intValue();
		if(d.getTypeByte()==DD.TAG_AP5) plaintext = d.getFirstObject(true).getInteger().intValue();
		return this;
	}
	public static byte getASN1Type() {
		return Encoder.TAG_SEQUENCE;
	}
}

public class DirectoryRequest extends ASNObj{
	private static final int V1 = 1;
	private static final int V2 = 2;
	private static final int V3 = 3;
	private static boolean DEBUG = false;
	private static int MAX_VERSION_SUPPORTED = V3;
	public int version = V2; //MAX_VERSION_SUPPORTED;
	public int[] agent_version = Util.getMyVersion();
	public String globalID;
	public String globalIDhash; // or this, or GID
	public DIR_Terms_Preaccepted[] terms;
	public String initiator_globalID; // used to verify signatures
	public String initiator_globalIDhash; // used to verify signatures
	public int UDP_port;
	public byte[] signature;
	
	private String dir_address; // used to build terms
	private String peer_ID; // localID
	
	@Override
	public DirectoryRequest decode(Decoder dec) throws ASN1DecoderFail {
		Decoder dr = dec.getContent();
		version = 0;
		if(dr.getTypeByte() == Encoder.TAG_INTEGER){
			int _version = dr.getFirstObject(true).getInteger().intValue();
			if(_version > MAX_VERSION_SUPPORTED){
				Util.printCallPath("Need to update software. I do not understand Requests v:"+_version+" v="+version);
				version = MAX_VERSION_SUPPORTED;
			}else 
				version = _version;
		}
		switch(version){
		case 0:
		case 1:
		case 2:
			return decode_2(dr);
			
		case 3:
		default:
			return decode_3(dr);
		}
	}
	public DirectoryRequest decode_2(Decoder dr) throws ASN1DecoderFail {
		globalID = dr.getFirstObject(true).getString();
		if(dr.getTypeByte() == DD.TAG_AC5)
			terms = dr.getFirstObject(true).getSequenceOf(DIR_Terms_Preaccepted.getASN1Type(), new DIR_Terms_Preaccepted[]{}, new DIR_Terms_Preaccepted());
		initiator_globalID = dr.getFirstObject(true).getString();
		this.UDP_port = dr.getFirstObject(true).getInteger().intValue();
		if((version!=0) && (dr.getTypeByte()==Encoder.TAG_OCTET_STRING))
			signature = dr.getFirstObject(true).getBytesAnyType();
		return this;
	}
	public DirectoryRequest decode_3(Decoder dr) throws ASN1DecoderFail {
		if((version>=3)&&(dr.isFirstObjectTagByte(DD.TAG_AC2)))
			agent_version = dr.getFirstObject(true).getIntsArray();
		
		if(((version < 3)||dr.isFirstObjectTagByte(DD.TAG_AC2)))
			globalID = dr.getFirstObject(true).getString();
		if(dr.isFirstObjectTagByte(DD.TAG_AC3))
			globalIDhash = dr.getFirstObject(true).getString();
		if(dr.getTypeByte() == DD.TAG_AC5)
			terms = dr.getFirstObject(true).getSequenceOf(DIR_Terms_Preaccepted.getASN1Type(), new DIR_Terms_Preaccepted[]{}, new DIR_Terms_Preaccepted());
		if((version < 3)||(dr.isFirstObjectTagByte(DD.TAG_AC6)))
			initiator_globalID = dr.getFirstObject(true).getString();
		if(dr.isFirstObjectTagByte(DD.TAG_AC7))
			initiator_globalIDhash = dr.getFirstObject(true).getString();
		if(dr.isFirstObjectTagByte(DD.TAG_AC10))
			this.UDP_port = dr.getFirstObject(true).getInteger().intValue();
		if((version!=0) && (dr.getTypeByte()==Encoder.TAG_OCTET_STRING))
			signature = dr.getFirstObject(true).getBytesAnyType();
		return this;
	}
	@Override
	public Encoder getEncoder() {
		switch(version){
		case 0:
		case 1:
		case 2:
			return getEncoder_2();
		case 3:
		default:
			return getEncoder_3();
		}
	}
	public Encoder getEncoder_2() {
		Encoder enc = new Encoder().initSequence();
		if(version != 0) enc.addToSequence(new Encoder(version));
		if(globalID!=null) enc.addToSequence(new Encoder(globalID, false));
		else{
			System.out.println("DirectoryRequest: This version expects a globalID. v="+version);
			System.out.println("DirectoryRequest: "+this);
			Util.printCallPath("call");
		}
		if((version!=0)&&(terms!=null)) enc.addToSequence(Encoder.getEncoder(terms).setASN1Type(DD.TAG_AC5));
		if(this.initiator_globalID!=null)enc.addToSequence(new Encoder(this.initiator_globalID, false));
		else{
			System.out.println("DirectoryRequest: This version expects an initiator_globalID. v="+version);
			System.out.println("DirectoryRequest: "+this);
			Util.printCallPath("call");
		}
		enc.addToSequence(new Encoder(this.UDP_port));
		if(version!=0) enc.addToSequence(new Encoder(signature));
		return enc;
	}
	public Encoder getEncoder_3() {
		Encoder enc = new Encoder().initSequence();
		if(version != 0) enc.addToSequence(new Encoder(version));
		if(version>=3) enc.addToSequence(new Encoder(agent_version).setASN1Type(DD.TAG_AC1));
		if(globalID!=null)enc.addToSequence(new Encoder(globalID, false).setASN1Type(DD.TAG_AC2));
		if(globalIDhash!=null)enc.addToSequence(new Encoder(globalIDhash, false).setASN1Type(DD.TAG_AC3));
		if((version!=0)&&(terms!=null)) enc.addToSequence(Encoder.getEncoder(terms).setASN1Type(DD.TAG_AC5));
		if(this.initiator_globalID!=null)enc.addToSequence(new Encoder(this.initiator_globalID, false).setASN1Type(DD.TAG_AC6));
		if(this.initiator_globalIDhash!=null)enc.addToSequence(new Encoder(this.initiator_globalIDhash, false).setASN1Type(DD.TAG_AC7));
		if(this.UDP_port > 0)enc.addToSequence(new Encoder(this.UDP_port).setASN1Type(DD.TAG_AC10));
		if(version!=0) enc.addToSequence(new Encoder(signature));
		return enc;
	}
	public String toString() {
		String result= "[DirectoryRequest:v="+version+
				((this.globalID!=null)?"\n gID="+Util.trimmed(globalID):"")+
				((this.globalIDhash!=null)?"\n gIDH="+Util.trimmed(globalIDhash):"")+
		       "\n  from:"+Util.trimmed(initiator_globalID)+"\n  UDPport="+UDP_port ;
			if(terms!=null)
		       for(int i=0; i<terms.length; i++){
		       	result+="\n  terms["+i+"]\n"+ terms[i];
		       }
			else result+="\n terms=null";
	    return result+"]";
	}
	byte buffer[] = new byte[DirectoryServer.MAX_DR_DA];
	public DirectoryRequest(byte[]_buffer, int peek, InputStream is) throws Exception {
		assert(DirectoryServer.MAX_DR_DA>=peek);
		Decoder dec=new Decoder(_buffer);
		if(dec.contentLength()>DirectoryServer.MAX_DR_DA) throw new Exception("Max buffer DirectoryServer.MAX_DR_DA="+DirectoryServer.MAX_DR_DA+
				" is smaller than request legth: "+dec.contentLength());
		Encoder.copyBytes(this.buffer, 0, _buffer, peek, 0);
		read(_buffer, peek, is);
	}
	public DirectoryRequest(){
	}
	public DirectoryRequest(InputStream is) throws Exception {
		read(buffer, 0, is);
	}
	/**
	 * Build request with pre-approved terms for service.
	 * The terms are read from the "directory_forwarding_terms" table.
	 * 
	 * Typically not signed. (Signatures may be requested on DoS detection/payments)
	 * @param target_GID
	 * @param initiator_GID
	 * @param initiator_udp
	 * @param target_peer_ID : needed to retrieve/maintain current terms
	 * @param dir_address : needed to filter/maintain terms
	 */
	public DirectoryRequest(String target_GID, String initiator_GID, int initiator_udp, String target_peer_ID, 
			String dir_address) {
		if (DEBUG) System.out.println("DirectoryRequest<init>: GPID="+target_GID+" iniID="+initiator_GID+" udp="+initiator_udp+" pID="+target_peer_ID+" adr="+dir_address);
		globalID = target_GID;
		this.initiator_globalID = initiator_GID;
		this.UDP_port = initiator_udp;
		this.peer_ID = target_peer_ID;
		this.dir_address= dir_address;
		this.terms = getTerms();
		this.signature=null; // optional ?
	}
	DIR_Terms_Preaccepted[] getTerms(){
		String sql;
		String[]params;
		
		// check first for terms specific to this directory address
		sql = "SELECT "+directory_forwarding_terms.fields_terms+
			" FROM  "+directory_forwarding_terms.TNAME+
		    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
			" AND ("+directory_forwarding_terms.dir_addr+" =? "+
			" OR  "+directory_forwarding_terms.dir_addr+" is NULL )"+
			" AND " + directory_forwarding_terms.priority_type+" = 1 ;";
		params = new String[]{this.peer_ID, this.dir_address};
		if (DEBUG) System.out.println("DirectoryRequest:getTerms: select directory this.dir_address: "+ this.dir_address);
		
		ArrayList<ArrayList<Object>> u;
		try {
			u = Application.db.select(sql, params, DEBUG);
		} catch (P2PDDSQLException e) {
			e.printStackTrace();
			return null;
		}
		if (u == null || u.size() == 0) { // Global directory
		    if (DEBUG) System.out.println("DirectoryRequest:getTerms: select for Global directory");
			sql = "SELECT "+directory_forwarding_terms.fields_terms+
			" FROM  "+directory_forwarding_terms.TNAME+
		    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
			" AND "+directory_forwarding_terms.dir_addr+" is NULL "+
			" AND " + directory_forwarding_terms.priority_type+" = 1 ;";
			params = new String[]{"0"};
			try {
				u = Application.db.select(sql, params, DEBUG);
			} catch (P2PDDSQLException e) {
				e.printStackTrace();
				return null;
			}	
		}
		/*
		if(u==null || u.size()==0){
			sql = "SELECT "+directory_forwarding_terms.fields_terms+
				" FROM  "+directory_forwarding_terms.TNAME+
			    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
				" AND ("+directory_forwarding_terms.dir_addr+" =? "+
				" OR  "+directory_forwarding_terms.dir_addr+" is NULL )"+
				" AND "+directory_forwarding_terms.priority+" = 1 ;";
			params = new String[]{this.peer_ID, this.dir_address};
			if(DEBUG) System.out.println("DirectoryRequest:getTerms: select directory this.dir_address: "+ this.dir_address);
			
			try {
				u = Application.db.select(sql, params, DEBUG);
			} catch (P2PDDSQLException e) {
				e.printStackTrace();
				return null;
			}
		}
		if(u==null || u.size()==0){ // Global directory
		    if(DEBUG) System.out.println("DirectoryRequest:getTerms: select for Global directory");
			sql = "SELECT "+directory_forwarding_terms.fields_terms+
			" FROM  "+directory_forwarding_terms.TNAME+
		    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
			" AND "+directory_forwarding_terms.dir_addr+" is NULL "+
			" AND ("+directory_forwarding_terms.priority+" = 1);";
			params = new String[]{"0"};
			try {
				u = Application.db.select(sql, params, DEBUG);
			} catch (P2PDDSQLException e) {
				e.printStackTrace();
				return null;
			}	
		}
		*/
		
		if(u==null || u.size()==0) return null;
		DIR_Terms_Preaccepted[] dir_terms = new DIR_Terms_Preaccepted[u.size()];
		int i=0; 
		for(ArrayList<Object> _u :u){
			D_TermsInfo ui = new D_TermsInfo(_u);
			dir_terms[i] = new DIR_Terms_Preaccepted();
			if(ui.topic)
				 dir_terms[i].topic = DD.getTopic(this.globalID, this.peer_ID);//"any topic"; //ask???
			else dir_terms[i].topic = null;
			if( ui.ad)
				 dir_terms[i].ad = 1;
			else dir_terms[i].ad = 0;
			if(ui.plaintext)
				 dir_terms[i].plaintext = 1;
			else dir_terms[i].plaintext = 0;
			if(ui.service!=-1)
				dir_terms[i].services_acceptable = Integer.toString(ui.service).getBytes(); // check?
			if(ui.payment ){
			   dir_terms[i].payment = new DIR_Payment();
			   dir_terms[i].payment.amount = 0;
			   dir_terms[i].payment.method = "Any Method";
			   dir_terms[i].payment.details = "Any details";
			}else dir_terms[i].payment = null;   
			if(DEBUG) System.out.println("DirectoryRequest:getTerms:  add: "+ui);
			i++;
		}
		return dir_terms;
	}
	public DIR_Terms_Preaccepted[] updateTerms(
			DIR_Terms_Requested[] terms, String peer_ID, String global_peer_ID,
			String dir_address, DIR_Terms_Preaccepted[] terms2) {
		String sql;
		String[]params;
		
		// check first for terms specific to this directory address
		sql = "SELECT "+directory_forwarding_terms.fields_terms+
			" FROM  "+directory_forwarding_terms.TNAME+
		    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
			" AND ("+directory_forwarding_terms.dir_addr+" =? "+
			" OR  "+directory_forwarding_terms.dir_addr+" is NULL )"+
			" AND " + directory_forwarding_terms.priority_type+" > 1 " +
					" ORDER BY "+directory_forwarding_terms.priority_type+";";
		params = new String[]{this.peer_ID, this.dir_address};
		if(DEBUG) System.out.println("DirectoryRequest:getTerms: select directory this.dir_address: "+ this.dir_address);
		
		ArrayList<ArrayList<Object>> u;
		try {
			u = Application.db.select(sql, params, DEBUG);
		} catch (P2PDDSQLException e) {
			e.printStackTrace();
			return null;
		}
		if(u==null || u.size()==0){ // Global directory
		    if(DEBUG) System.out.println("DirectoryRequest:getTerms: select for Global directory");
			sql = "SELECT "+directory_forwarding_terms.fields_terms+
			" FROM  "+directory_forwarding_terms.TNAME+
		    " WHERE "+directory_forwarding_terms.peer_ID+" =? " +
			" AND "+directory_forwarding_terms.dir_addr+" is NULL "+
			" AND " + directory_forwarding_terms.priority_type+" > 1 " +
			" ORDER BY "+directory_forwarding_terms.priority_type+";";
			params = new String[]{"0"};
			try {
				u = Application.db.select(sql, params, DEBUG);
			} catch (P2PDDSQLException e) {
				e.printStackTrace();
				return null;
			}	
		}

		if(u==null || u.size()==0) return null;
		ArrayList<DIR_Terms_Preaccepted> _dir_terms = new ArrayList<DIR_Terms_Preaccepted>();
		DIR_Terms_Preaccepted[] dir_terms; // = new DIR_Terms_Preaccepted[u.size()];
		int i=0; 
		
		for(ArrayList<Object> _u :u){
			D_TermsInfo ui = new D_TermsInfo(_u);
			DIR_Terms_Preaccepted dir_terms_i = new DIR_Terms_Preaccepted();
			if(ui.topic)
				 dir_terms_i.topic = DD.getTopic(this.globalID, this.peer_ID);//"any topic"; //ask???
			else dir_terms_i.topic = null;
			if( ui.ad)
				 dir_terms_i.ad = 1;
			else dir_terms_i.ad = 0;
			if(ui.plaintext)
				 dir_terms_i.plaintext = 1;
			else dir_terms_i.plaintext = 0;
			if(ui.service!=-1)
				dir_terms_i.services_acceptable = Integer.toString(ui.service).getBytes(); // check?
			if(ui.payment ){
			   dir_terms_i.payment = new DIR_Payment();
			   dir_terms_i.payment.amount = 0;
			   dir_terms_i.payment.method = "Any Method";
			   dir_terms_i.payment.details = "Any details";
			}else dir_terms_i.payment = null;   
			if(DEBUG) System.out.println("DirectoryRequest:getTerms:  add: "+ui);
			_dir_terms.add(dir_terms_i);
			if(sufficientTerms(_dir_terms, terms)){
				break;
			}
			i++;
		}
		dir_terms = _dir_terms.toArray(new DIR_Terms_Preaccepted[0]);
		// should only send the minimum ones...
		return dir_terms;
	}
	/**
	 * Check if the terms2 are satisfied by _dir_terms
	 * @param _dir_terms
	 * @param terms2
	 * @return
	 */
	private boolean sufficientTerms(
			ArrayList<DIR_Terms_Preaccepted> _dir_terms,
			DIR_Terms_Requested[] terms2) {
		// TODO Auto-generated method stub
		return false;
	}
	void read(byte[]buffer, int peek, InputStream is)  throws Exception{
		if(DEBUG)out.println("dirRequest read: ["+peek+"]="+ Util.byteToHexDump(buffer, peek));
		int bytes=peek;
		if(peek==0){
			bytes=is.read(buffer);
			out.println("dirRequest reread: ["+bytes+"]="+ Util.byteToHexDump(buffer, " "));
		}
		int content_length, type_length, len_length, request_length;
		if (bytes<1){
			out.println("dirRequest exiting: bytes<1 ="+bytes);
			return;
		}
		Decoder asn = new Decoder(buffer);
		if(asn.type()!=Encoder.TYPE_SEQUENCE){
			out.println("dirRequest exiting, not sequence: ="+asn.type());
			return;
		}
		do{
			type_length = asn.typeLen();
			if(type_length <=0) {
				out.println("dirRequest reread type ="+type_length);
				if(bytes == DirectoryServer.MAX_DR_DA) throw new Exception("Buffer Type exceeded!");
				if(is.available()<=0)  throw new Exception("Data not available for type!");
				bytes += is.read(buffer, bytes, DirectoryServer.MAX_DR_DA-bytes);
			}
		}while(type_length <= 0);
		if(DEBUG)out.println(" dirRequest type ="+type_length);
		do{
			len_length = asn.lenLen();
			if(len_length <=0) {
				out.println("dirRequest reread len len ="+len_length);
				if(bytes == DirectoryServer.MAX_DR_DA) throw new Exception("Buffer Length exceeded!");
				if(is.available()<=0)  throw new Exception("Data not available for length!");
				bytes += is.read(buffer, bytes, DirectoryServer.MAX_DR_DA-bytes);
			}
		}while(len_length <= 0);
		if(DEBUG)out.println(" dirRequest len len ="+len_length);
		content_length = asn.contentLength();
		request_length = content_length + type_length + len_length;
		if(DEBUG)out.println(" dirRequest req_len ="+request_length);
		if(request_length > DirectoryServer.MAX_LEN){
			throw new Exception("Buffer Content exceeded!");
		}
		byte[] buffer_all = buffer;
		if(bytes < request_length) {
			buffer_all = new byte[request_length];
			Encoder.copyBytes(buffer_all, 0, buffer, bytes);
			do{
				//if(is.available()<=0)  throw new Exception("Data not available for length!");;
				bytes += is.read(buffer_all,bytes,request_length - bytes);
			}while(bytes < request_length);
		}
		Decoder dr = new Decoder(buffer_all);
		decode(dr);
	}
}