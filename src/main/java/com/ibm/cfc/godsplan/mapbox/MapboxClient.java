/* _______________________________________________________ {COPYRIGHT-TOP} _____
 * IBM Confidential
 * IBM Lift CLI Source Materials
 *
 * (C) Copyright IBM Corp. 2018  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 * _______________________________________________________ {COPYRIGHT-END} _____*/

package com.ibm.cfc.godsplan.mapbox;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.maps.errors.ApiException;
import com.ibm.cfc.godsplan.http.BasicHttpClient;
import com.ibm.cfc.godsplan.http.BasicHttpClient.BasicHttpResponse;
import com.ibm.cfc.godsplan.maps.LocationMapper;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.api.directions.v5.models.StepManeuver;
import com.mapbox.geojson.Point;
import retrofit2.Response;

/**
 *
 */
public class MapboxClient
{

   protected static final Logger logger = LoggerFactory.getLogger(MapboxClient.class);
   /***/
   public static final String MAPBOX_URL = "api.mapbox.com";
   /***/
   public static final int MAPBOX_PORT = 80;
   /***/
   public static final String MAPBOX_API_TOKEN = System.getenv("MAPBOX_API_TOKEN");
   /***/
   public static BasicHttpClient httpClient;
   /***/
   public final String FINAL_DESTINATION = "You have arrived at your destination";
   /***/
   public final int SECONDS_IN_A_MINUTE = 60;

   /***/
   public MapboxClient()
   {
      try
      {
         httpClient = new BasicHttpClient("http", MAPBOX_URL, MAPBOX_PORT);
      }
      catch (HttpException e)
      {
         logger.error("Error creating connection to Mapbox.", e);
      }
   }

   /**
    * @return JSON Response from dataset command
    * @throws HttpException
    */
   public String listDatasets() throws HttpException
   {
      Map<String, String> hashMap = new HashMap<String, String>()
      {
         {
            put("access_token", MAPBOX_API_TOKEN);
         }
      };
      BasicHttpResponse httpResponse = httpClient.executeGet("/datasets/v1/team6ix", hashMap);
      return httpResponse.getEntity();
   }

   /**
    * Obtains distance in meters between two points
    * 
    * @param origin
    *           point coordinates of start location
    * @param destination
    *           point coordinates of end location
    * @return distance between two locations
    * @throws IOException
    */
   public double distanceBetweenTwoCoordinates(Point origin, Point destination) throws IOException
   {
      MapboxDirections.Builder builder = MapboxDirections.builder();

      // 1. Pass in all the required information to get a simple directions route.
      builder.accessToken(MAPBOX_API_TOKEN);
      builder.origin(origin);
      builder.destination(destination);

      // 2. That's it! Now execute the command and get the response.
      Response<DirectionsResponse> response = builder.build().executeCall();

      if (response.isSuccessful())
      {
         return response.body().routes().get(0).distance();
      }

      return -1;
   }

   /**
    * List route steps between two locations
    * 
    * @param origin
    *           point coordinates of start location
    * @param dest
    *           point coordinates of end location
    * @param profile
    *           route transportation profile, default is walking
    * @return list of steps in route
    */
   public List<String> getRoute(Point origin, Point dest, Optional<String> profile)
   {
      MapboxDirections request;
      List<String> stepsList = new ArrayList<>();

      // 1. Pass in all the required information to get a route.
      if (profile.isPresent())
      {
         logger.info("Finding route, setting movement profile to {}", profile.get());
         request = MapboxDirections.builder().accessToken(MAPBOX_API_TOKEN).origin(origin).destination(dest)
               .profile(profile.get()).steps(true).build();
      }
      else
      {
         logger.info("Finding route, setting movement profile to default {}", DirectionsCriteria.PROFILE_WALKING);
         request = MapboxDirections.builder().accessToken(MAPBOX_API_TOKEN).origin(origin).destination(dest)
               .profile(DirectionsCriteria.PROFILE_WALKING).steps(true).build();
      }

      Response<DirectionsResponse> response;

      try
      {
         response = request.executeCall();
      }
      catch (IOException e)
      {
         logger.error("Unable to make call to mapbox for directions between, {} and {}", origin, dest, e);
         stepsList.add("ERROR: Could not find directions");
         return stepsList;
      }

      // 3. Log information from the response
      if (response.isSuccessful())
      {
         logger.info("MapBox directions call successful");
         List<LegStep> steps = response.body().routes().get(0).legs().get(0).steps();
         double routeDurationMins = response.body().routes().get(0).duration() / SECONDS_IN_A_MINUTE;
         logger.info("Route found with {} steps, estimated duration {}", steps.size(), routeDurationMins);

         for (LegStep stepLeg : steps)
         {
            String stepName = stepLeg.name();
            StepManeuver maneuver = stepLeg.maneuver();

            if (!stepName.isEmpty() && !maneuver.instruction().contains(FINAL_DESTINATION))
            {
               stepsList.add(MessageFormat.format("{0}, for {1} meters.", stepLeg.maneuver().instruction(),
                     stepLeg.distance()));
            }
            else if (maneuver.instruction().contains(FINAL_DESTINATION))
            {
               stepsList.add(maneuver.instruction());
            }
         }
      }

      return stepsList;
   }

   public static void main(String args[]) throws HttpException, IOException, ApiException, InterruptedException
   {
      MapboxClient client = new MapboxClient();
      System.out.println(client.listDatasets());

      LocationMapper mapper = new LocationMapper();
      Point o = mapper.getGeocodingCoordinates("8200 Warden Ave");
      Point d = mapper.getGeocodingCoordinates("First Markham Place");

      System.out.println("Origin: " + o);
      System.out.println("Destination: " + d);
      System.out.println("Distance between locations: " + client.distanceBetweenTwoCoordinates(o, d));
      System.out.println(client.getRoute(o, d, Optional.empty()));

   }
}
