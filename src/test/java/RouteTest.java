import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import co.gridport.server.config.ConfigProvider;
import co.gridport.server.domain.Endpoint;
import co.gridport.server.domain.RequestContext;
import co.gridport.server.domain.Route;
import co.gridport.server.handler.Firewall;


@RunWith(MockitoJUnitRunner.class)
public class RouteTest {

    @Mock private ConfigProvider config;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @InjectMocks private Firewall firewall;

    @SuppressWarnings("serial")
    @Before public void setup() {
        when(config.getEndpoints()).thenReturn(
            new HashMap<Integer,Endpoint>() {{
                put(1,new Endpoint(1, true, null, "test.com",      null, "/*",  "http://localhost:88/test", null));
                put(2,new Endpoint(2, true, null, "localhost", null, "/base/*", "http://localhost:88", null));
            }}
        );
    }

    @Test public void noTrailingSlashesAreAdded() {
        when(request.getScheme()).thenReturn("https");
        when(request.getHeader("Host")).thenReturn("test.com");
        when(request.getRequestURI()).thenReturn("/reports");
        RequestContext context = new RequestContext(request, response);
        List<Route> routes = firewall.filterEndpointsByRequest(context);
        Assert.assertEquals(1, routes.size());
        Assert.assertEquals( "http://localhost:88/test/reports", routes.get(0).endpoint + routes.get(0).uri + routes.get(0).query_stirng);
    }

    @Test public void correctGlueSlashBetweenBaseAndTranslatedUri() {
        when(request.getScheme()).thenReturn("https");
        when(request.getHeader("Host")).thenReturn("localhost");
        when(request.getRequestURI()).thenReturn("/base/something");
        RequestContext context = new RequestContext(request, response);
        List<Route> routes = firewall.filterEndpointsByRequest(context);
        Assert.assertEquals(1, routes.size());
        Assert.assertEquals( "http://localhost:88/something", routes.get(0).endpoint + routes.get(0).uri + routes.get(0).query_stirng);
    }

    @Test public void illegalDoubleSashIsRemovedButqueryStringContainingDoubleSlashIsNotModified() {
        when(request.getScheme()).thenReturn("https");
        when(request.getHeader("Host")).thenReturn("localhost");
        when(request.getRequestURI()).thenReturn("/base//something?hello=//");
        RequestContext context = new RequestContext(request, response);
        List<Route> routes = firewall.filterEndpointsByRequest(context);
        Assert.assertEquals(1, routes.size());
        Assert.assertEquals( "http://localhost:88/something?hello=/", routes.get(0).endpoint + routes.get(0).uri + routes.get(0).query_stirng);
    }

}
