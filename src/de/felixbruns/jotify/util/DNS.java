package de.felixbruns.jotify.util;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * Class providing convenience methods for looking up DNS entries.
 * Currently only supports the DNS SRV entry.
 * 
 * @author Felix Bruns <felixbruns@web.de>
 */
public class DNS {
	/**
	 * Perform a DNS SRV lookup for the specified name.
	 * 
	 * @param name The name to lookup.
	 * 
	 * @return A {@link List} of {@link InetSocketAddress} objects.
	 */
	public static List<InetSocketAddress> lookupSRV(String name){
		/* Create list to return later. */
		List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
		
		/* Perform DNS SRV lookup and fill list with responses. */

        try {
			/* Create directory context. */
			DirContext context = null;
            @SuppressWarnings("rawtypes")
			Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            context = new InitialDirContext(env);
            
            /* Actually perform DNS SRV lookup. */
			Attributes attributes = context.getAttributes(name, new String[]{"SRV"});
			
			/* Enumeration of records. */
			NamingEnumeration<?> enumeration = null;
			
			/* Get all SRV records returned. */
			if(attributes.get("SRV") != null){
				enumeration = attributes.get("SRV").getAll();
			}
			
			/* Loop over records, parse them and add them to our list. */
			while(enumeration != null && enumeration.hasMoreElements()){
				/* Get record and cast it to a string. */
				String record = (String)enumeration.nextElement();
				
				/* Format: <Priority> <Weight> <Port> <Host> */
				String[] parts = record.split(" ");
				
				/* Create socket adress from host and port. */
				InetSocketAddress address = new InetSocketAddress(
					parts[3], Integer.parseInt(parts[2])
				);
				/*InetSocketAddress address = new InetSocketAddress(
						record, 4070
					);*/
				
				/* Add address to list. */
				addresses.add(address);
			}
		}
		catch(NamingException e){
			/* Ignore and just return possibly empty list. */
			e.printStackTrace();
		}
        catch (Exception e) {
            // Ignore.
        	e.printStackTrace();
        }			
			
		/* Return list of addresses. */
		return addresses;
	}
	
	public static List<InetSocketAddress> lookupA(String name){
		/* Create list to return later. */
		List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
		
		/* Perform DNS SRV lookup and fill list with responses. */

        try {
			/* Create directory context. */
			DirContext context = null;
            @SuppressWarnings("rawtypes")
			Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            context = new InitialDirContext(env);
            
            /* Actually perform DNS SRV lookup. */
			Attributes attributes = context.getAttributes(name, new String[]{"A"});
			
			/* Enumeration of records. */
			NamingEnumeration<?> enumeration = null;
			
			/* Get all A records returned. */
			if(attributes.get("A") != null){
				enumeration = attributes.get("A").getAll();
			}
			
			/* Loop over records, parse them and add them to our list. */
			while(enumeration != null && enumeration.hasMoreElements()){
				/* Get record and cast it to a string. */
				String record = (String)enumeration.nextElement();
				
				/* Create socket adress from host and port. */
				InetSocketAddress address = new InetSocketAddress(
						record, 4070
					);
				
				/* Add address to list. */
				addresses.add(address);
			}
		}
		catch(NamingException e){
			/* Ignore and just return possibly empty list. */
			e.printStackTrace();
		}
        catch (Exception e) {
            // Ignore.
        	e.printStackTrace();
        }			
			
		/* Return list of addresses. */
		return addresses;
	}
}
