/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.mgt;

import static org.apache.jena.fuseki.Fuseki.serverLog ;

import java.util.List ;

import javax.servlet.http.HttpServlet ;

import org.apache.jena.fuseki.Fuseki ;
import org.apache.jena.fuseki.FusekiException ;
import org.apache.jena.fuseki.server.FusekiErrorHandler ;
import org.eclipse.jetty.server.Connector ;
import org.eclipse.jetty.server.Server ;
import org.eclipse.jetty.server.nio.SelectChannelConnector ;
import org.eclipse.jetty.servlet.ServletContextHandler ;
import org.eclipse.jetty.servlet.ServletHolder ;

public class ManagementServer
{
    /** Create but do not initialize */
    public static Server createManagementServer(int mgtPort)
    {
        // Separate Jetty server
        Server server = new Server() ;
        
//        BlockingChannelConnector bcConnector = new BlockingChannelConnector() ;
//        bcConnector.setUseDirectBuffers(false) ;
//        Connector connector = bcConnector ;
        
        Connector connector = new SelectChannelConnector() ;
        // Ignore idle time. 
        // If set, then if this goes off, it keeps going off and you get a lot of log messages.
        connector.setMaxIdleTime(0) ; // Jetty outputs a lot of messages if this goes off.
        connector.setPort(mgtPort);
        server.addConnector(connector) ;
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setErrorHandler(new FusekiErrorHandler()) ;
        server.setHandler(context);
        return server  ;
    }

    public static void addServerFunctions(ServletContextHandler context, String base) {
        Fuseki.serverLog.info("Adding server information functions") ;
        if ( !base.endsWith("/" ) )
            base = base + "/" ;
        if ( !base.startsWith("/"))
            throw new FusekiException("Base URI does not start with a '/'") ; 
        // Dump request
        addServlet(context, new DumpServlet(),         base+"dump") ;   // XXX Remove.?
        addServlet(context, new ActionDescription(),   base+"status") ;
        addServlet(context, new ActionPing(),          base+"ping") ;
    }
    
    public static void addAdminFunctions(ServletContextHandler context, String base) {
        Fuseki.serverLog.info("Adding administration functions") ;
        if ( !base.endsWith("/" ) )
            base = base + "/" ;
        if ( !base.startsWith("/"))
            throw new FusekiException("Base URI does nto start with a '/'") ; 
        addServlet(context, new MgtCmdServlet(),        base+"mgt") ;       // XXX Old - remove.
        addServlet(context, new ActionStats(),          base+"stats/*") ;   // "/abc/*" covers ".../abc" as well.
        addServlet(context, new ActionDatasets(),       base+"datasets/*") ;  
    }

    // SHARE
    private static void addServlet(ServletContextHandler context, String datasetPath, HttpServlet servlet, List<String> pathSpecs)
    {
        for ( String pathSpec : pathSpecs )
        {
            if ( pathSpec.endsWith("/") )
                pathSpec = pathSpec.substring(0, pathSpec.length()-1) ;
            if ( pathSpec.startsWith("/") )
                pathSpec = pathSpec.substring(1, pathSpec.length()) ;
            addServlet(context, servlet, datasetPath+"/"+pathSpec) ;
        }
    }

    private static void addServlet(ServletContextHandler context, HttpServlet servlet, String pathSpec)
    {
        ServletHolder holder = new ServletHolder(servlet) ;
        addServlet(context, holder, pathSpec) ;
    }
    
    private static void addServlet(ServletContextHandler context, ServletHolder holder, String pathSpec)
    {
        serverLog.debug("Add servlet @ "+pathSpec) ;
        context.addServlet(holder, pathSpec) ;
    }

}

