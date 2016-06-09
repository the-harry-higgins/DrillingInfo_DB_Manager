package server.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.amazonaws.services.s3.model.ObjectMetadata;

import server.DBConnector;


public class VersionedJarService {
	
	private DBConnector dbConnector = new DBConnector();
	private Map<String, String> jarsOldToNew = new HashMap<String, String>();

	/*
	public String detect(String vjName) {
		List<String> updates = dbConnector.getAssociatedVersions(vjName);
		if (updates == null) {
			return null;
		}
		else if (updates.size() == 0) {
			return vjName + " is up to date.";
		}
		else {
			jarsOldToNew.put(vjName, updates);
			return "Detected " + updates.size() + " updates for " + vjName;
		}
	}
	*/
	
	public String detectAll() {

		Map<String, ObjectMetadata> dbJarMap = dbConnector.getAllJars();
		Map<String, String> localJars = getLocalJars();
		
		Iterator<Entry<String, ObjectMetadata>> it = dbJarMap.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<String, ObjectMetadata> pair = (Map.Entry<String, ObjectMetadata>)it.next();
	        
	        String s3jar = pair.getKey().toString();
			String symName = dbJarMap.get(s3jar).getUserMetaDataOf("bundle-symbolicname");

			if(localJars.containsKey(symName)){
				if (compareVersionNumbers(localJars.get(symName), dbJarMap.get(s3jar).getUserMetaDataOf("version")) <= 0) {
					localJars.remove(symName);
					it.remove();
				}
			}
			else {
				
				//TODO What if there is a file in S3 but not in the users files. 
			}
		}
		
		if (jarsOldToNew.size() > 0) {
			return "Detected updates for " + jarsOldToNew.size() + " jars";
		}
		else {
			return null;
		}
	}	

	private HashMap<String, String> getLocalJars() {
		// TODO Auto-generated method stub
		return new HashMap<String, String>();
	}
	
	
	/**
	 * Compares two version numbers passed as strings.
	 *  
	 * @param originalVer The version of the original file.
	 * @param newVer The version of the new file.
	 * @return Returns positive if the new version is newer. 
	 * 				   negative if it is an older version.
	 * 				   zero if it is the same version. 
	 */
	private int compareVersionNumbers(String originalVer, String newVer) {
		String[] vals1 = originalVer.split("\\.");
	    String[] vals2 = newVer.split("\\.");
	    
	    int i = 0;
	    
	    // set index to first non-equal ordinal or length of shortest version string
	    while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
	      i++;
	    }
	    
	    // compare first non-equal ordinal number
	    if (i < vals1.length && i < vals2.length) {
	        int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
	        return Integer.signum(diff) * -1;
	    }
	    
	    // the strings are equal or one string is a substring of the other
	    // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
	    return Integer.signum(vals1.length - vals2.length) * -1;		
	}
}
