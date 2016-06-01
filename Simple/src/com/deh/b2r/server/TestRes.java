package com.deh.b2r.server;


import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

//import java.net.URI;
//import java.util.ArrayList;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
//import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
//import javax.ws.rs.core.GenericEntity;
//import javax.ws.rs.core.Response;

//import org.glassfish.jersey.server.JSONP;



//import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Utility methods.
 *
 */

@Path("/")
public class TestRes
{

	//static AddressBook book = new AddressBook();
	static Updater updater = new Updater();
	
  @GET
  @Path("{system}")
  @Produces(MediaType.TEXT_PLAIN)
  public String getDatatype(@PathParam("system") String system) throws Exception
  {
    if (system.equals("shutdown")) {
      System.exit(0);
    }
    return "Unknown System Command";
  }
  
  @GET
  @Path("update")
  //@Produces(MediaType.APPLICATION_JSON)
  public void getUpdate() throws Exception
  {
    updater.get("hello.jar");
  }
  
  /*@GET
  @Path("streets")
  @Produces(MediaType.APPLICATION_JSON)
  public List<SharedRep.Address> getQuantities() throws Exception
  {
    return book.getStreets();
  }

  @POST
  @Path("streets")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public SharedRep.Address addStreet(SharedRep.Address street) throws Exception
  {
    book.addStreet(street);
    
    return street;
  }*/

}





