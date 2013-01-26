Motivation and Objectives
========================================================================
 The motivation is to have a standalone http component that can be placed 
 in the http request path which can intercept, filter, throttle and route
 http requests to various deployed http service endpoints without any implementation
 or awareness requirement on the part of the service endpoints based 
 on SLA Contracts, ACL Rules and Routing Definitions.
   
 Objective 1: To be a Service Registry for HTTP/RESTful services 
    - ACL Rules & SLA Contracts for authentication, authorisation, throttling and routing
    - Monitoring and statistics
    - SSL proxy
    - Http Caching (not implemented yet)
 Objective 2: To be a gateway to the Event-Driven world 
    - option for completing http requests asynchronously       
    - Http interface to publish and subscribe systems
        - JMS Module
        - Kafka Module (not implemented yet)   

Quick Start Instructions
========================================================================

Installation
------------
 1. checkout code 
    $> git clone git://github.com/michal-harish/gridport.git
    $> cd gridport 
 2. build a single executable jar 
    $> mvn package assembly:single
 3. quick launch 
    $> java -jar ./target/gridport-server.jar 
 4. add some test rules to newly generated policy.db  
    - it is a sqlList database which should be initialized - http://sqlitebrowser.sourceforge.net
    - see SLA Contracs & ACL Rules reference below 
 5. Optionally, generate gzip distros with service wrapper $> ant distrobuild
    - this should generate different packages in ./target/dist

SLA Contracts
------------------------------------------------------------------------
SLA Contracs define under which conditions and how frequently can certain
endpoint be send requests to.

ACL Rules reference
------------------------------------------------------------------------
ACL Rules define groups and users who can be added to contracts for authorisation

WEB INTERFACE /manage
------------------------------------------------------------------------
ssl service_endpoint        http_method                 gateway_host    auth_group  uri_base
1   module://manager        GET POST DELETE             -               admin root  /manage/*
0   module://space          GET POST OPTIONS MOVE PUT   -                           /space/ 
1   module://jms            POST PUT DELETE OPTIONS     -                           /jms/*



Anatomy - TODO Update as per firewall-authenticator-handler design after threading done
========================================================================
* Service starts by looking first looking at arguments
* Then a policy sqlite database is attempted and created anew if first run 
* Then, depending on settings, http and/or https listeners are initialized 
* After that all requests received on http/https are received by the main thread
* Authenticator receives all incoming requeusts ( still in the main thread)
* Authenticator decides whether to consider the requests at all or reject
* Authetnicator looks for required authentication and parks the request
* If the request is finally authenticated it is forked into a thread by Request Handler
* Request Handler decides what kind of Client Thread to fork based on routing table
* A separate thread listens on standardInput to receive commands (if cli is enabled)
* Currently there is no scheduler or garbage collector running in the background
* Modules are initialized on demand (only if there is actual Client Request)
* All server activity is logged into the gridportd.log
* On shutdown server first closes request listeners and calls all modules to cleanup 


SSL CERTIFICATE & KEY STORES 
======================================================================== 
Both inbound and outbound certificates are stored in single keystore file.
(Config table will then contain  KeyStoreFile and KeyStorePass.) 
For incoming ssl connections, a certificate must be added for each gateway.
For accessing individual endpoints via ssl, all certificates needed must be added to a single keystore.jks file.
* self signed certificates with openssl
** openssl genrsa -des3 -out privkey.pem 2048
** openssl req -new -x509 -nodes -sha1 -days 365 -key privkey.pem > certificate.pem -config ../conf/openssl.cnf
* SSL KeyStores
** keytool -genkey -alias ... -dname "cn=..." -keystore keystore.jks
** keytool -import -trustcacerts -alias ... -file \\192.168....\xampp\apache\conf\ssl.crt\server.crt -keystore ...jks
** keytool -list -v -keystore keystore.jks

REQUEST PROCESSING PATH
========================================================================
 * Server(s) listen with implicit GridPortAuthenticator and ClientRequestHandler
 * Every request is first intercepted by the GridPortAuthenticator
 ** here the contract is looked up and validated and results in the list of allowed endpoints (their IDs)
 ** then ClientRequestHandler.route() is called and merged with routes allowed by contract 
 ** if there is no authentication free route, a digest md5 authentication is invoked
 ** Authentication.Success is issued when there is at least one route and authentication has been satisfied
 * ClientRequestHandler.handle() takes over authenticated request
 ** First the paritcular type of ClientThread is selected, initialized and started
 * ClientThreadXXX.run() takes over
 
EXAMPLE CONFIGURATON AT PORT 8040 BEHIND APACHE PROXY
========================================================================
    <VirtualHost *:80>
        ServerName gridport.co 
        #ServerAlias someservice.gridport.co
        ProxyPreserveHost On
        ProxyVia On
        ProxyPass / http://127.0.0.1:8040/
    </VirtualHost>

JMS Receiver Example (php)
========================================================================
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
TODO create install script for linux 
TODO START THINKING OF TEST STRATEGY (ESP. EXPECTATIONS AND ASSUMPTIONS ABOUT ROUTING)
FIXME null base_uri throws exception in GridPortHandler:207
FIXME query string is missing from the REQUEST_URI after migration to jetty
TODO unit tests and performance benchmarks
TODO Create internal PolicyProvider to manage access to the sqlite config
    - Insert default settings, user, contract and endpoints when initializing policy.db
    - Prepare for .conf provider with dir.watcher (will be faster then querying sqlite)
    - Implement jetty handler Graceful
    - Add/remove contexts on the fly
TODO Jackson
TODO Metrics
TODO look for //??? as unresolved migration code
      
DEPRIORITISED nexus proxying (used to fail prior to jetty)
DEPRIORITISED implement keep-alive connection (for svn proxying and other similar ones)

TODO Tidy up context class
FIXME JMS Subscription initailization doesn't invoke recovery thread
TODO JMS Keep publishers alive with a session per some client request attribute (probably remote ip?) 
TODO Authenticator .. send some Forbidden html with the http status
TODO JMS HTTP GET to operate as non-durable retrospective subscriber and only use it as list for url base topic and queue
TODO make an internal function to read header by case-insensitive header name key
TODO process multiple subrequest responses in a streaming fashion 
TODO INIT insert into settings(name,value) VALUES('router.log','topic://gridport.log.router');
TODO INIT insert into settings(name,value) VALUES('httpPort,'8040');
TODO INIT insert into settings(name,value) VALUES('generalTimeout,'30');
FIXME Win-64 wrapper native missing
TODO PASSWORDS
TODO JMS POSTListenerQueue
TODO ROUTER log all jms publish messages with internal jms publisher co.gridport.jms.publish("gridport.log.jms","{..}");
FIXME ROUTER Set-Cookie passes only last cookie instruction
TOCO ROUTER 504 Gateway Timeout in mergeTasks()
TODO ROUTER X-Forwarded-For 
TODO JMS Test setup with HornetQ
TODO BUILD - distro publisher ( into the AOS3 downloads or an sourceforge/freshmeat api) 
TODO CODE STRUCTURE - Module Interface ( initialize(), close(), cliCommand(),... ) 
FIXME ROUTER currently if nested URIs are used the more general must precede the general one if it need be routed to
TODO ROUTER if any of the sub request of a multicast event responds with 4xx, ALL subrequests need to be cancelled with extra compensation for those that have already returned 2xx or 3xx 
TODO ROUTER Compensate for pending Event Sub requests 		  
TODO ROUTER review mergeTasks();  implement MATCH (200 ok if responses are identical); 
TODO ROUTER review mergeTasks();  review MERGE
TODO ROUTER review mergeTasks();  implement MIX using multipart/mixed; 
TODO ROUTER review OPTIONS and implement merging Allow headers with proxy settings
TODO ROUTER SECONDARY employ user_agent routing variables if SLAs
 
CHANGE LOG
========================================================================
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