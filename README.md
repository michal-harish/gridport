Motivation and Objectives
========================================================================
 The motivation is to have a standalone http component that can be placed 
 in the http request path which can intercept, filter, throttle and route
 requests to various deployed http service and API endpoints without any 
 implementation or awareness requirement on the part of the endpoint
 based on SLA Contracts, ACL Rules and Routing Definitions.
   
**Objective 1** : To be a Service Registry for Http/RESTful Services 
 * ACL Rules & SLA Contracts for authentication, authorisation, throttling and routing
 * Monitoring and statistics
 * SSL proxy
 * Http Caching (not implemented yet)

**Objective 2** : To be a gateway to the Event-Driven world 
 * option for completing http requests asynchronously       
 * to be a Http interface to publish-subscribe systems
   * JMS Module - working
   * Tuple Space Module - experimental
   * Kafka Module - not implemented yet   

Quick Start Instructions
========================================================================

Installation
------------
 1. Checkout code 
    * $> git clone git://github.com/michal-harish/gridport.git
    * $> cd gridport 
 2. Build a single executable jar 
    * $> mvn package assembly:single
 3. Start the server 
    * $> java -jar ./target/gridport-server.jar
 4. Optionally you can generate gzip distro and deploy as service 
    * $> ant distrobuild
    * this should generate different packages in ./target/dist
    * deploy unzip and run ./bin/sh.daemon start
    
Configuration
-------------
 1. default ./policy.db should be generated with the following settings
    * http port set to 8040
    * ssl port disabled
    * localAdmin contract created for any requests from localhost without authorisation        
    * default endpoint for managing the server created at the uri /manage/
    * example settings were also generated:
        * '/example' endpoint was added pointing to 'http://localhost:80/'  
        * 'examplegroup' with one user 'exampleuser' were added
        * 'examplecontract' was created requiring authenticated user from 'examplegroup'
            through which '/example' endpoint can be accessed at most once every second   
 2. Try http://localhost:8040/manage/ from your browser 
    You should see some rudimentary information about the server - this will become
    the console for managing the policy.db but until then we have to edit it manually
    with something like http://sqlitebrowser.sourceforge.net
 3. Now try http://localhost:8040/example/
    * as per example configuration this endpoint requires authenticated user so a http login box should pop up
    * note: the authentication is actually digest-md5 not a basic http one 
    * the user must be from the 'exampleuser' group so use 'exampleuser' as username and no password
    * if you have apache or another http server running on port 80 you should see now its default page
 4. Add some test rules to newly generated policy.db  
    * edit the ./policy.db and see SLA Contracs & ACL Rules reference below

SLA Contracts
------------------------------------------------------------------------
SLA Contracs define under which conditions and how frequently can certain
endpoint be queried.

ACL Rules reference
------------------------------------------------------------------------
ACL Rules define groups and users who can be added to contracts for authorisation


Anatomy and The Request Path
========================================================================
* Service starts by looking for a 'cli' argument whether to run in interactive console mode
* Then a policy sqlite database is attempted and created anew if can't be located 
* Shutdown hook is registered that will capture kill command  
* Then, depending on settings, http and/or https listeners are initialized 
* A separate thread is started which linstens on standardInput to receive control commands 
* All server activity is logged into the ./logs/gridportd.log
* Server is started with and all http/https are handled by a chain of serial handlers:
    1. Firewall 
        * first it looks at SLA Contracts by IP Address range - if none available the request is rejected with 403 Forbidden
        * if there are available contracts it checks available routes by combination of request attributes and contracts
        * if the routes are ambiguous the more specific ones are preferred 
        * if there are no available routes the request is rejected with 404 Not Found
        * otherwise the RequestContext is created with available contracts and routes
        * context is attached to the request object for availability in the next handler 
    2. Authenticator 
        * if there is option that doesn't require authentication, selects and passes on
        * if there are only routes requiring authentication it checks if there's existing user
        * if the current user doesn't match any of the route contracts' groups, authentication is requested
        * if the there is a user match the routes are reduced to the ones available for the user
    3. RequestHandler
        * only if the request passed Firewall and Authenticator 
        * module is chosen and a ClientThread of that module is invoked 
        * (?) modules are initialized on demand (only if there is actual Client Request)
        * for control panel requests it will be a ClientThreadMangaer
        * for jms bridge it will be ClientThreadJMS and so on
        * for most of the request it will be a ClientThreadRouter which is the main http proxy
            * consume() - rate limiting behaviour and contract-based routing
            * execute() - fire subrequests for 1 or more endpoints in parallel
            * evaluate() - merge sync and async responses
            * complete() - complete responses, publish log message to jms, close the request channels
            
SSL Certificate and Key Store Shortcuts 
------------------------------------------------------------------------
Both inbound and outbound certificates are stored in single keystore file.
(Config table will then contain  KeyStoreFile and KeyStorePass.) 
For incoming ssl connections, a certificate must be added for each gateway.
For accessing individual endpoints via ssl, all certificates needed must be added to a single keystore.jks file.
* self signed certificates with openssl
    * openssl genrsa -des3 -out privkey.pem 2048
    * openssl req -new -x509 -nodes -sha1 -days 365 -key privkey.pem > certificate.pem -config ../conf/openssl.cnf
* SSL KeyStores
    * keytool -genkey -alias ... -dname "cn=..." -keystore keystore.jks
    * keytool -import -trustcacerts -alias ... -file \\192.168....\xampp\apache\conf\ssl.crt\server.crt -keystore ...jks
    * keytool -list -v -keystore keystore.jks
 
Example Configuration Behind Apache
------------------------------------------------------------------------
    <VirtualHost *:80>
        ServerName gridport.co 
        #ServerAlias someservice.gridport.co
        ProxyPreserveHost On
        ProxyVia On
        ProxyPass / http://127.0.0.1:8040/
        <Location />
          Order allow,deny
          Allow from all
        </Location>        
    </VirtualHost>

JMS Receiver Example (php)
------------------------------------------------------------------------
    <?php 
    if ($_SERVER['REQUEST_METHOD']=='PUT') { //subscribe acknowledge
        header("HTTP/1.1 201 Created"); 
                
    } elseif($_SERVER['REQUEST_METHOD']=='POST') { //receive message    
        ob_start();
        $message = file_get_contents("php://input");
        $destination = $_SERVER['HTTP_JMSDESTINATION'];
        $priority = $_SERVER['HTTP_JMSPRIORITY'];
        $messageId = $_SERVER['HTTP_JMSMESSAGEID'];     
        //..critical process of $message that must succeed prior to acknowledgement                 
        //..throw new exception("some problem");        
        header("HTTP/1.1 202 Accepted"); //acknowledge      
        //..some post-acknowledgement processing ..     
    }
    ?>

Backlog
========================================================================    
* REFACTOR domain.Route as immutable POJO
* CHORE move log to /var/log/gridport.log, add log4j configurator and create install script for linux 
* CHORE Win-64 wrapper native missing
* CHORE add jms module to the default policy.db initializtor

* REFACTOR add interface Module ( initialize(), close(), cliCommand(),... )
* REFACTOR make an internal function to read header by case-insensitive header name key
* REFACTOR Create internal PolicyProvider to manage access to the sqlite config
    * Insert default settings, user, contract and endpoints when initializing policy.db
    * Prepare for .conf provider with dir.watcher (will be faster then querying sqlite)
    * Implement jetty handler Graceful
    * Add/remove contexts on the fly
    * This will be a good stimul to refactor handler.Firewall and handler.Authenticator 
    
* DESIGN exception handling and propagation
* DESIGN password management
* DESIGN manager interface (options are cli, web, api)
* DESIGN review default jms auditing
* DESIGN review OPTIONS usage and implement merging Allow headers with proxy settings
* DESIGN proxyMulticast() - implement MATCH (200 ok if responses are identical); 
* DESIGN testing strategy (ESP. EXPECTATIONS AND ASSUMPTIONS ABOUT ROUTING)
* DESIGN performance benchmarking strategy

* BUG ROUTER Set-Cookie passes only last cookie instruction
* FEATURE ClientThread.loadIncomingContentEntity() should not exist, streaming should be impelemented
* FEATURE process multiple subrequest responses in a streaming fashion 
* FEATURE ROUTER if any of the sub request of a multicast event responds with 4xx, ALL subrequests need to be cancelled with extra compensation for those that have already returned 2xx or 3xx 
* FEATURE ROUTER Compensate for pending Event Sub requests           
* FEATURE ROUTER SECONDARY employ user_agent routing variables if SLAs 
* FEATURE ROUTER currently if nested URIs are used the more general must precede the general one if it need be routed to
* FEATURE ROUTER 504 Gateway Timeout in mergeTasks()    
* FEATURE send some default html with 400,403,404,500; also from Authenticator

* BUG JMS Subscription initailization doesn't invoke recovery thread
* FEATUER JMS Keep publishers alive with a session per some client request attribute (probably remote ip?) 
* FEATURE JMS HTTP GET to operate as non-durable retrospective subscriber and only use it as list for url base topic and queue
* FEATURE JMS POSTListenerQueue
* TEST JMS setup with HornetQ

Change Log
========================================================================
28 Jan 2013
 * ClientThread separation of logic proxyPassthrough() proxyMulticast() proxyFulfilment()
 * Refactored interface of domain.RequestContext

27 Jan 2013 
 * added default jms settings for maximum connection attempt in case of activemq
 * fixed query strings that were missing in the subrequest URLs
 * started separating stable from unstable code into packages and refactoring
 
26 Jan 2013 - moved to github (dwrapper was removed and will be optional)

22 Oct 2012
 * Mavenized and build and distro processes
 
21 Oct 2012
 * Migrated to jetty 
 * Migrated to slf4j

22 Sep 2012
 * wildcard routes are discarded if there's an exact match
 * No uri_base in the routing table is an implicit wildcard.
 * Fix for OPTIONS request failure because of trying to push response body
 
23 Jul 2012
 * JMS Module removed recovery thread and only using onMessage session scope to wait and recover.
 * Fixed requests that have both async and sync endpoints

22 Jul 2012
 * JMS Module RESTful API description at gridport.co/jms
 * JMS Publish returns Message ID after 202 status in the response entity

10 Jun 2012
 * single response passthru by streaming, instead of loading entire repsone body into memory 

30 Sep 2011
 * added http method to sub-request DEBUG log
 * replacing all /{2,} for one slash to fix proxy URIs 
 * removed dubious X-Forwarded-Host to fix proxy-to-prxy communication 
 * consumer_ip = <remoteAddress> [ + ,<forwarded-for>] and same applied on contract ip_range
 
18 Sep 2011
 * instance id contains computer name and is now used with jms subscribers

15 Sep 2011
 * gridport-log router consumer.id = <username>@<group> which is chosen from intersection of contract and route groups
 * gridport-log router added `input` + `output` = `volume` in bytes

10 Sep 2011
 * fixed server timestamp format
 * gridport-log-date added consumer.ip and consumer.id
 
5 Sep 2011
 * Server.date formater with milliseconds
 * gridport-log-version 1 contains <name>=<value> information set
 * router.log message ttl is 1 week 
 * JMS when NOTIFY fails with JMS or IO exception listener is paused for 10 seconds to prevent flooding with stale messages

4 Sep 2011
 * added checksum for distro zips
 * moved manage icons into media.gridport.co/aos/grid.css

1 Sep 2011
 * more fine grained IOExceptino handling
 * shut_down calls interruption for all client threads
 * dropped R-service-log settings and endpoint.auth_group field 
 * policy db initialization purely by upgrade method
 * gridport-log-version 1
 * logging all requests with internal jms publisher co.gridport.jms.publish("topicL//gridport.log.router","{..}");
 * settings/router.log=<jms_destination>
 
31 Aug 2011
 * contract filtering moved into ClientThread so that it doesn't block other requests
 * contracts are now authentication entity (not endpoints) e.g. localAdmin(wihtout auth) & remoteAdmin(with admin auth)
 * deprecated endpoint.auth_group field
 * Runtime.addShutdownHook for cleaner sh.daemon stop  

30 Aug 2011
 * created 3 toplevel loggers (server,request,jms)
 * root logger is mainlog ../logs/girdport.log
 * server module initialization happens before main listeners are open  
 * in cli mode output uses stdout only for server logger and others inherit root logger 

29 Aug 2011
 * OPTIONS on JMS topic returns allowed methods in header and  list of subscribers in the entity
 
28 Aug 2011
 * version 0.9
 * standard distro build

26 Aug 2011
 * JMS Module - persistency and startup of subscriptions / restart of the JMS Provider
 * JMS Topics complete

25 Aug 2011
 * JMS message properties to headers when notifying and vice versa when posting
 * JMS on subscribing ping a PUT request to target and expect 201 Created
 * create Request to pass via enqueue/dequeue between Authenticator and RequestHandler
 * Removed all unchecked attributes from the http exchange object and simplifies passing routes    

23 Aug 2011
 * JMS POST /<topic> + payload - publish 
 * JMS RecoveryThread for scheduling redelivery
 * System.out and System.err replaced with log4j Loggers 

22 Aug 2011
 * created JMS module (not yet persistent and configurable) but working with ActiveMQ
 * JMS GET /<topic> = list subscribtions
 * JMS PUT /<topic> = subscribe target url in the entity to receive POST and acknowledge by 202
 * JMS DELETE /<topic> = unsubscribe target url in the entity
 * JMS load jndi properties from the settings table

24 Apr 2011
 * fixed replication of mutiple instances of the same header
 * gateway_host is only domain, port stripped
 * ASYNC status is now the CLIENT RESPONSE STATUS not the async subrequest status condition
 * if ASYNC_STATUS is 3xx then Location headers is added to refresh the page  
 
23 Apr 2011
 * event retries are each time one minute longer
 * POST, PUT and DELETE events terminate with 302 Found to the same location
 * GET events terminate with 202 Accepted
 * 3 SSL options for endpoint: 0 = http only, 1 = https only, null/empty = any 
  
20 Apr 2011
 * Standard X-Forwarded-Host ( without protocol )
  
14 Mar 2011
 * if not method is specified for endpoint then ALL methods are accepted
 
20 Nov 2010
 * added TuppleSpace module via ClientThreadSpace 
 * created way for plugging in ServerThread modules 