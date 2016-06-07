package server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.io.output.ByteArrayOutputStream;

import server.model.VersionedJar;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * TODO need to separate logger stuff into its own class
 * TODO likely need to change the setup of this to make it restful.
 * 
 * Currently handles pulling jars from S3, verification, and version control.
 *
 */
public class DBConnector {

	private static String bucketName     = "drilling-info-bucket";
	private static String updatedJarName = "";
	private static String prefix = "DrillingInfo_Updates/";
	private static String version = "6.0.1/plugins/";
	private final AmazonS3 s3client;

	/**
	 * Initializer sets up the logger for errors.
	 */
	public DBConnector() {
		s3client = new AmazonS3Client(new ProfileCredentialsProvider());
	}

	/**
	 * Get the jars that are up on S3. 
	 * Use this to determine which jars have updates available.
	 *  
	 * @return The list of all jars on the S3 server
	 */
	public  List<String> getAllJars() {
		List<String> jars = new ArrayList<String>();

		try {
			for (S3ObjectSummary objectSummary : S3Objects.withPrefix(s3client, bucketName, prefix + version)) {
				if(!objectSummary.getKey().replaceFirst(prefix + version, "").equals("")) {
					//System.out.println(" - " + objectSummary.getKey().replaceFirst(prefix + version, ""));
					jars.add(objectSummary.getKey().replaceFirst(prefix + version, ""));
				}
			}
		} catch (AmazonServiceException ase) {
			Fred.logger.error("Caught an AmazonServiceException, which means your request made it to Amazon S3, but was rejected with an error response for some reason.");
			Fred.logger.error("Error Message:    " + ase.getMessage());
			Fred.logger.error("HTTP Status Code: " + ase.getStatusCode());
			Fred.logger.error("AWS Error Code:   " + ase.getErrorCode());
			Fred.logger.error("Error Type:       " + ase.getErrorType());
			Fred.logger.error("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			Fred.logger.error("Caught an AmazonClientException, which means the client encountered an internal error while trying to communicate with S3, such as not being able to access the network.");
			Fred.logger.error("Error Message: " + ace.getMessage());
		}

		return jars;
	}

	/**
	 * This pulls a jar from Amazon S3 and places a copy in the local directory.
	 * Make sure that the jar file has a manifest in it. 
	 * 
	 * @param jar The name of the jar to get. Pass without ".jar" at the end. 
	 * @return 
	 * @throws NoSuchAlgorithmException 
	 */
	public VersionedJar getVersionedJar(String jar) throws NoSuchAlgorithmException {
		updatedJarName = jar;

		try {
			//Creates the local jar file
			File file = new File(updatedJarName + ".jar");
			if (file.exists()){		  		
				if (checkJarVersions(file) > 0) {
					downloadFile(file);
				}
			}
			else {
				downloadFile(file);
			}

			return new VersionedJar(getVersionNumber(file), file);

		} catch (IOException e) {
			e.printStackTrace();
		}catch (AmazonServiceException ase) {
			Fred.logger.error("Caught an AmazonServiceException, which means your request made it to Amazon S3, but was rejected with an error response for some reason.");
			Fred.logger.error("Error Message:    " + ase.getMessage());
			Fred.logger.error("HTTP Status Code: " + ase.getStatusCode());
			Fred.logger.error("AWS Error Code:   " + ase.getErrorCode());
			Fred.logger.error("Error Type:       " + ase.getErrorType());
			Fred.logger.error("Request ID:       " + ase.getRequestId());

			File file = new File(updatedJarName + ".jar");
			file.delete();
		} catch (AmazonClientException ace) {
			Fred.logger.error("Caught an AmazonClientException, which means the client encountered an internal error while trying to communicate with S3, such as not being able to access the network.");
			Fred.logger.error("Error Message: " + ace.getMessage());
		}
		return null;
	}
	
	/*public List<String> getNewestVersion(String localJar) {
		String jar = localJar.substring(0, localJar.lastIndexOf('_'));
		isValidFile(prefix + version + jar);
		
		return null;
	}*/
	
	/**
	 * Pulls the file from S3. Also checks the MD5 for security.
	 * Note that there may be a problem if the ETag from Amazon is not in MD5 format. This may be the case for massive files. 
	 * 
	 * @param file the File to download
	 * @return true if the MD5 checksums matched, false otherwise
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	private boolean downloadFile(File file) throws IOException, NoSuchAlgorithmException {
		boolean sumsMatched = false;
		file.createNewFile();

		//Downloads the file from S3 and puts it's contents into a output stream
		System.out.println("Downloading an object\n");
		S3Object s3object = s3client.getObject(new GetObjectRequest(bucketName, updatedJarName + ".jar"));
		if (s3object == null) return sumsMatched;
		ObjectMetadata id = s3client.getObjectMetadata(bucketName, updatedJarName + ".jar");
		String s3sum = id.getETag();

		//Create the streams
		InputStream reader = new BufferedInputStream(s3object.getObjectContent());
		OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));
		//To test if it will pull corrupt files, un-comment below. 
		//reader.read();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		org.apache.commons.io.IOUtils.copy(reader, baos);
		byte[] bytes = baos.toByteArray();
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

		//Checksum here
		bais.reset();
		MessageDigest md = MessageDigest.getInstance("MD5");
		String generatedSum = SecurityChecksum.getDigest(bais, md);

		if (generatedSum.equals(s3sum)) {
			//Pumps the file into the local file
			bais.reset();
			int read = -1;
			while ((read = bais.read()) != -1) {
				writer.write(read);
			}
			sumsMatched = true;
		}
		else {
			Fred.logger.warn("MD5 Checksums did not match. S3:\"" + s3sum + "\" Local:\"" + generatedSum + "\"");
			file.delete();
		}
		writer.flush();
		writer.close();
		bais.close();
		reader.close();

		return sumsMatched;
	}

	/**
	 * This compares the file based on the manifest file. 
	 * Makes the newest version file the standard name.  
	 * 
	 * @param file The original file to check against the new. 
	 * @throws IOException Caught by the caller. 
	 * @return Returns positive if the new version is newer. 
	 * 				   negative if it is an older version.
	 * 				   zero if it is the same version. 
	 */
	private int checkJarVersions(File file) throws IOException {

		ObjectMetadata id = s3client.getObjectMetadata(bucketName, updatedJarName + ".jar");
		//System.out.println(id.getUserMetadata().get("version"));
		//JarFile jarFile = new JarFile(file);
		//Manifest manifest = jarFile.getManifest();

		String originalVersionNumber = getVersionNumber(file);

		String versionNumber = id.getUserMetadata().get("version");


		//jarFile.close();
		System.out.println("\nLocal Version: " + originalVersionNumber);
		System.out.println("Downloaded Version: " + versionNumber);
		return compareVersionNumbers(originalVersionNumber, versionNumber);
	}

	/**
	 * Iterates through the main attributes of a jar until it finds specific version keywords
	 * to get the version number of the jar.
	 * 
	 * @param manifest
	 * @return version number corresponding to the keyword
	 * @throws IOException 
	 */
	private String getVersionNumber(File file) throws IOException {
		JarFile jarFile = new JarFile(file);
		Manifest manifest = jarFile.getManifest();
		jarFile.close();
		Attributes attributes = manifest.getMainAttributes();
		if (attributes!=null){
			Iterator<Object> it = attributes.keySet().iterator();
			while (it.hasNext()){
				Name key = (Name) it.next();
				String keyword = key.toString();
				//Different types of manifest files may require a change here.
				if (keyword.equals("Implementation-Version") || keyword.equals("Bundle-Version") || keyword.equals("Manifest-Version")){
					return (String) attributes.get(key);
				}
			}
		}
		return null;
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

	/**
	 * Get the name of the jar that is wanted
	 * 
	 * @return the name of the jar with the .jar at the end. 
	 */
	public static String getUpdatedJarName() {
		return updatedJarName + ".jar";
	}
	
	/**
	 * Tries to access a file to see if it exists. 
	 * 
	 * @param path The name of the file to see if it exists. 
	 * @return True if the file exists. False otherwise. 
	 * @throws AmazonClientException
	 * @throws AmazonServiceException
	 */
	public boolean isValidFile(String path) throws AmazonClientException, AmazonServiceException {
	    boolean isValidFile = true;
	    try {
	        ObjectMetadata objectMetadata = s3client.getObjectMetadata(bucketName, path);
	    } catch (AmazonS3Exception s3e) {
	        if (s3e.getStatusCode() == 404) {
	        // i.e. 404: NoSuchKey - The specified key does not exist
	            isValidFile = false;
	        }
	        else {
	            throw s3e;    // rethrow all S3 exceptions other than 404   
	        }
	    }

	    return isValidFile;
	}

}
