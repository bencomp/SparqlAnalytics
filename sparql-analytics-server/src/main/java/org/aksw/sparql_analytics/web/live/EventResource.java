package org.aksw.sparql_analytics.web.live;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.aksw.sparql_analytics.atmosphere.BoundsFilter;
import org.aksw.sparql_analytics.atmosphere.EventListener;
import org.aksw.sparql_analytics.model.Bounds;
import org.aksw.sparql_analytics.model.Event;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterConfig;
import org.atmosphere.cpr.BroadcasterFactory;
import org.atmosphere.cpr.DefaultBroadcaster;
import org.atmosphere.jersey.SuspendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.spi.resource.Singleton;

/**
 * <pre>
 * curl -v -N -X GET http://localhost:8080/MapPush/api
 * boundsHeader='48.0,49.0,2.0,3.0'
 * curl -v -N -X GET http://localhost:8080/MapPush/api -H "X-MAP-BOUNDS: $boundsHeader"
 * </pre>
 * 
 * <pre>
 * event='{"lat":48.921266,"lng":2.499390}'
 * curl -v -X POST http://localhost:8080/MapPush/api/event -d $event -H "Content-Type: application/json"
 * </pre>
 * 
 * @author Nicolas
 */

@Path("/live/")
@Singleton
public class EventResource {

	private final Logger LOG = LoggerFactory.getLogger(EventResource.class);

	private EventListener listener;
	private EventGenerator generator;

	@Resource(name="sparqlAnalyticsDs")
	private DataSource dataSource;
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	@Context
	private BroadcasterFactory broadcasterFactory;

	/**
	 * Programmatic way to get a Broadcaster instance
	 * 
	 * @return the MapPush Broadcaster
	 */
	private Broadcaster getBroadcaster() {
		return broadcasterFactory.lookup(DefaultBroadcaster.class, "MapPush", true);
	}

	/**
	 * The @PostConstruct annotation makes this method executed by the container
	 * after this resource is instanciated. It is one way to initialize the
	 * Broadcaster (e.g. by adding some Filters)
	 */
	@PostConstruct
	public void init() {
		LOG.info("Initializing EventResource");
		BroadcasterConfig config = getBroadcaster().getBroadcasterConfig();
		config.addFilter(new BoundsFilter());
		listener = new EventListener();
		generator = new EventGenerator();		
	}

	/**
	 * When the client connects to this URI, the response is suspended or
	 * upgraded if both client and server arc WebSocket capable. A Broadcaster
	 * is affected to deliver future messages and manage the communication
	 * lifecycle.
	 * 
	 * @param resource
	 *            the AtmosphereResource (injected by the container)
	 * @param bounds
	 *            the bounds (extracted from header and deserialized)
	 * @return a SuspendResponse
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public SuspendResponse<String> connect(@Context AtmosphereResource resource,
			@HeaderParam("X-Map-Bounds") Bounds bounds) {
		if (bounds != null) resource.getRequest().setAttribute("bounds", bounds);
		return new SuspendResponse.SuspendResponseBuilder<String>()
				.broadcaster(getBroadcaster())
				.outputComments(true)
				.addListener(listener)
				.build();
	}

	/**
	 * This URI allows a client to send a new Event that will be broadcaster to
	 * all other connected clients.
	 * 
	 * @param event
	 *            the Event (deserialized from JSON by Jersey)
	 * @return a 200 Response
	 * @throws Exception
	 */
	@POST
	@Path("event")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response broadcastEvent(@QueryParam("async") @DefaultValue("true") boolean async,
			Event event) throws Exception {
		LOG.info("New event received: {}", event);
		if (async) {
			getBroadcaster().broadcast(event);
			return Response.ok(0).build();
		} else {
			getBroadcaster().broadcast(event).get();
			int size = getBroadcaster().getAtmosphereResources().size();
			return Response.ok(size).build();
		}
	}

	@GET
	@Path("start")
	public Response start() {
		LOG.info("Starting EventGenerator");
		generator.start(getBroadcaster(), 100);
		return Response.ok().build();
	}

	@GET
	@Path("stop")
	public Response stop() {
		LOG.info("Stopping EventGenerator");
		generator.stop();
		return Response.ok().build();
	}

}
